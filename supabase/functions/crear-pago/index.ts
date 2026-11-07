// ============================================================
// Edge Function · crear-pago
//
// Crea una orden en Mercado Pago para una solicitud y devuelve la URL
// a la que hay que mandar el navegador.
//
// **El token de Mercado Pago vive aquí y en ningún otro sitio.**
// `agogo.js` se descarga en el navegador: un token puesto ahí sería
// público, y con el de producción cualquiera podría crear cobros a
// nombre de la tienda y consultar los pagos de todos.
//
// **El monto se calcula aquí, leyendo los precios de la base.** Lo que
// manda el navegador es el id de la solicitud y nada más. Es el mismo
// criterio que la cotización de envíos y que la ruleta: lo que decide
// un cobro no se decide en el navegador. Y aquí importa más que nunca,
// porque `order_request_items.precio_estimado` **sí** lo escribe el
// cliente al crear su solicitud —así se diseñó: una solicitud guarda
// los precios que la persona vio— y cobrar por ese número sería dejar
// que el navegador ponga el precio.
//
// **Lo que se le enseña a Mercado Pago es genérico.** Un renglón que
// diga «Artículos de higiene personal» y nada más. El nombre del
// producto no va: aparecería en el correo de Mercado Pago y en el
// estado de cuenta de la tarjeta, que es justo lo que no controla quien
// compra. Mismo motivo por el que la guía declara ese contenido y no
// otro.
//
// Usa la **Orders API** (`POST /v1/orders`), no la de Preferences.
// Mercado Pago mantiene Preferences pero ya no la recomienda para
// integraciones nuevas: las funciones nuevas salen solo en Orders, y
// Orders devuelve `checkout_url` donde la otra devolvía `init_point`.
//
// Secrets (Supabase → Edge Functions → Secrets):
//   MP_ACCESS_TOKEN   token de Mercado Pago. **Empezar con el de
//                     prueba**: el de producción cobra de verdad.
//   SITIO_URL         raíz del sitio, para el regreso: https://…
//   MP_STATEMENT      lo que se lee en el estado de cuenta. Discreto
//                     por defecto; son 13 caracteres como máximo.
//   MP_MAX_MESES      meses sin intereses como tope. 1 = ninguno.
//   CONTACT_ALLOWED_ORIGINS orígenes permitidos, separados por coma.
// SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY los inyecta la plataforma.
// ============================================================

import { createClient } from "jsr:@supabase/supabase-js@2";

/**
 * El token, limpio de lo que se le pega al copiarlo.
 *
 * Un espacio al final o un «Bearer » delante sobreviven al copiar-y-pegar sin verse, y
 * Mercado Pago los rechaza con un 401 idéntico al de un token equivocado.
 */
const MP_TOKEN = (Deno.env.get("MP_ACCESS_TOKEN") ?? "")
  .trim()
  .replace(/^Bearer\s+/i, "")
  .trim();

const MP_API = "https://api.mercadopago.com";

/**
 * A dónde vuelve el navegador cuando Mercado Pago termina.
 *
 * Sin barra final para poder pegarle lo que haga falta sin acabar con dos.
 */
const SITIO = (Deno.env.get("SITIO_URL") ?? "").trim().replace(/\/+$/, "");

/**
 * Lo que la persona leerá en su estado de cuenta, por si la base no contesta.
 *
 * El valor bueno vive en `site_settings.pago_statement` (40_acuse.sql), y esto es solo
 * el respaldo. Está en la base y no aquí porque **la web también lo enseña**: le dice a
 * la persona que verá esto en su tarjeta. Con dos copias —un secret y una constante en
 * el bundle— acabarían diciendo cosas distintas, y entonces le estaríamos prometiendo
 * algo falso sobre su estado de cuenta.
 *
 * Discreto a propósito: el estado de cuenta lo puede leer quien comparte la tarjeta, y
 * el nombre de la tienda ahí es información que quien compra no eligió dar. Mismo
 * criterio que el contenido declarado en la guía.
 */
const STATEMENT_RESPALDO = (Deno.env.get("MP_STATEMENT") ?? "PAG").trim().slice(0, 13);

/** Tope de meses sin intereses. 1 deja solo el pago de una vez. */
const MAX_MESES = Math.max(1, Number(Deno.env.get("MP_MAX_MESES") ?? "1"));

/**
 * Añade a la respuesta el motivo crudo de Mercado Pago y lo que le mandamos.
 *
 * Para afinar el mapeo de campos, que es donde se va el tiempo: Mercado Pago contesta
 * 400 con el nombre del campo que no le gustó, y sin ver lado a lado lo enviado y su
 * queja hay que adivinar. **Apagarlo en producción.**
 */
const DEBUG = Deno.env.get("MP_DEBUG") === "1";

const ALLOWED_ORIGINS = (Deno.env.get("CONTACT_ALLOWED_ORIGINS") ?? "*")
  .split(",")
  .map((o) => o.trim())
  .filter((o) => o.length > 0);

/** Lo que se declara. Nunca el nombre del producto. */
const CONCEPTO = "Articulos de higiene personal";

function corsHeaders(origin: string | null): Record<string, string> {
  const allowAll = ALLOWED_ORIGINS.includes("*");
  const allowed = allowAll
    ? "*"
    : (origin && ALLOWED_ORIGINS.includes(origin) ? origin : ALLOWED_ORIGINS[0]);
  return {
    "Access-Control-Allow-Origin": allowed,
    "Access-Control-Allow-Headers":
      "authorization, apikey, content-type, x-client-info",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Max-Age": "86400",
    "Vary": "Origin",
    "Content-Type": "application/json",
  };
}

function json(body: unknown, status: number, origin: string | null): Response {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders(origin) });
}

/**
 * Lo menos que se puede cobrar.
 *
 * Con un 75 % de descuento un pedido pequeño se queda en unos pocos pesos, y por
 * debajo de cierto importe la comisión se come el cobro entero —además de que Mercado
 * Pago tiene sus propios mínimos—. Mejor decirlo y resolverlo a mano que abrir una
 * orden que se va a atascar.
 */
const MINIMO = Math.max(0, Number(Deno.env.get("MP_MINIMO") ?? "20"));

/** Texto limpio, o cadena vacía. */
function limpio(v: unknown): string {
  return v == null ? "" : String(v).trim();
}

/** Dos decimales, como cadena, que es como los quiere Mercado Pago. */
function dinero(n: number): string {
  return (Math.round(n * 100) / 100).toFixed(2);
}

interface Renglon {
  variant_id: string;
  cantidad: number;
}

/**
 * La fila tal como la pide la consulta de abajo.
 *
 * Se declara a mano porque el cliente de Supabase no conoce el esquema: sin esto su
 * tipo para `data` es un error genérico y no deja leer ni `id`.
 */
interface FilaSolicitud {
  id: string;
  folio: number | null;
  created_by: string | null;
  cliente_nombre: string | null;
  cliente_email: string | null;
  total_estimado: number | null;
  envio_costo: number | null;
  envio_descripcion: string | null;
  metodo_entrega: string | null;
  pago_estado: string | null;
  pago_orden_id: string | null;
  pago_total: number | null;
  codigo_descuento: string | null;
  order_request_items: Renglon[] | null;
}

interface FilaVariante {
  id: string;
  precio_venta: number | null;
}

/**
 * Cuánto descuenta el código de esta solicitud, en pesos sobre los artículos.
 *
 * Valida de nuevo todo: que exista, que no esté canjeado, que no haya caducado y que
 * el premio sea de verdad un descuento. Lo que diga el navegador no entra aquí.
 *
 * **No marca el código como canjeado.** Eso lo hace el webhook cuando el pago se
 * aprueba: quemarlo al abrir la orden le quitaría el premio a quien cierre la pestaña
 * sin pagar.
 *
 * Si algo no cuadra devuelve `error` en vez de cobrar sin descuento: quien escribió un
 * código espera que se le aplique, y cobrarle el precio entero en silencio es la peor
 * de las salidas.
 */
async function descuentoDe(
  // deno-lint-ignore no-explicit-any
  supabase: any,
  codigo: string,
  totalArticulos: number,
): Promise<{ monto: number; pct: number; regalo: string; error?: string }> {
  if (!codigo) return { monto: 0, pct: 0, regalo: "" };

  const { data } = await supabase
    .from("wheel_spins")
    .select("codigo, canjeado, expira_at, wheel_prizes(tipo, descuento_pct, detalle)")
    .eq("codigo", codigo)
    .maybeSingle();

  const giro = data as
    | {
      canjeado: boolean;
      expira_at: string | null;
      wheel_prizes:
        | { tipo: string | null; descuento_pct: number | null; detalle: string | null }
        | null;
    }
    | null;

  const nada = { monto: 0, pct: 0, regalo: "" };
  if (!giro) return { ...nada, error: "Ese código no existe." };
  if (giro.canjeado) return { ...nada, error: "Ese código ya se usó." };
  if (giro.expira_at && new Date(giro.expira_at).getTime() < Date.now()) {
    return { ...nada, error: "Ese código ya caducó." };
  }

  const premio = giro.wheel_prizes;
  const tipo = limpio(premio?.tipo);

  // Un regalo no toca el importe: lo que hace es que la tienda meta un artículo más en
  // la caja. Se guarda su descripción en la solicitud para que quien la prepare lo lea
  // sin cruzar tablas.
  if (tipo === "regalo") {
    return { monto: 0, pct: 0, regalo: limpio(premio?.detalle) || codigo };
  }

  if (tipo !== "descuento") {
    return { ...nada, error: "Ese código no trae ningún premio que aplicar." };
  }

  const pct = Number(premio?.descuento_pct ?? 0);
  if (!(pct >= 1 && pct <= 100)) {
    return { ...nada, error: "Ese código no trae ningún premio que aplicar." };
  }

  // Se redondea el descuento, no el total: así el importe cobrado y el guardado salen
  // del mismo número y no se separan por un centavo.
  const monto = Math.round(totalArticulos * pct) / 100;
  return { monto, pct, regalo: "" };
}

/**
 * El texto del estado de cuenta, de `site_settings`.
 *
 * Nunca lanza: si la fila no está o la consulta falla, se usa el respaldo. Quedarse sin
 * cobrar porque no se pudo leer un rótulo de trece caracteres sería absurdo.
 */
async function statement(): Promise<string> {
  try {
    // Cliente propio, como en `anotar`: el tipo genérico del de fuera no encaja aquí y
    // pelearse con eso cuesta más que las tres líneas de construir otro.
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
    );
    const { data } = await supabase
      .from("site_settings")
      .select("valor")
      .eq("clave", "pago_statement")
      .maybeSingle();
    const valor = String((data as { valor?: unknown } | null)?.valor ?? "").trim();
    return valor ? valor.slice(0, 13) : STATEMENT_RESPALDO;
  } catch (e) {
    console.error("no se pudo leer pago_statement", String(e));
    return STATEMENT_RESPALDO;
  }
}

/**
 * La URL de una orden que todavía se puede pagar, o `null`.
 *
 * Se le pregunta a Mercado Pago en vez de fiarse de lo que guardamos: la orden pudo
 * caducar, o pagarse justo ahora en otra pestaña, y mandar a alguien a una URL muerta es
 * peor que abrirle una orden nueva.
 *
 * Nunca lanza: si no se puede comprobar, se devuelve `null` y quien llama crea otra. El
 * riesgo de eso es dejar una orden abierta sin usar, que no cobra nada.
 */
async function ordenAbierta(ordenId: string): Promise<string | null> {
  try {
    const respuesta = await fetch(`${MP_API}/v1/orders/${ordenId}`, {
      headers: { "Authorization": `Bearer ${MP_TOKEN}` },
      signal: AbortSignal.timeout(10_000),
    });
    if (!respuesta.ok) return null;

    const datos = await respuesta.json() as Record<string, unknown>;
    const url = String(datos.checkout_url ?? "").trim();
    const estado = String(datos.status ?? "").trim().toLowerCase();

    // Solo las que siguen esperando a que alguien pague. Una `processed` ya se pagó, y
    // las demás terminaron de otra forma.
    const abierta = estado === "created" || estado === "action_required" || estado === "";
    return url && abierta ? url : null;
  } catch (e) {
    console.error("no se pudo comprobar la orden abierta", ordenId, String(e));
    return null;
  }
}

/**
 * Deja constancia. Nunca lanza.
 *
 * Esto acompaña a algo que ya pasó. Si el registro falla se queja en los logs y se
 * sigue: perder la orden de pago por no poder anotarla sería cambiar un problema
 * pequeño por uno caro.
 */
async function anotar(evento: Record<string, unknown>): Promise<void> {
  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
    );
    // El cliente no conoce el esquema, así que su tipo para `insert` es inservible
    // aquí. La forma de la fila la fija 39_pagos.sql.
    const { error } = await supabase.from("pagos").insert(evento as never);
    if (error) {
      console.error("no se pudo anotar el pago", JSON.stringify(evento), error.message);
    }
  } catch (e) {
    console.error("no se pudo anotar el pago", String(e));
  }
}

Deno.serve(async (req: Request): Promise<Response> => {
  const origin = req.headers.get("origin");

  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders(origin) });
  }
  if (req.method !== "POST") {
    return json({ error: "Método no permitido" }, 405, origin);
  }
  if (!MP_TOKEN) {
    return json({ error: "El cobro en línea aún no está configurado." }, 503, origin);
  }
  if (!SITIO) {
    return json({ error: "Falta configurar la dirección del sitio." }, 503, origin);
  }

  // ---- quién llama ----
  // Con **su** token, no con service_role: así el RLS se aplica igual que a cualquiera
  // y lo que se comprueba es la sesión de verdad.
  const token = (req.headers.get("authorization") ?? "")
    .replace(/^Bearer\s+/i, "")
    .trim();
  if (!token) return json({ error: "Falta la sesión." }, 401, origin);

  const comoQuienLlama = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_ANON_KEY") ?? "",
    { global: { headers: { Authorization: `Bearer ${token}` } } },
  );

  const { data: sesion } = await comoQuienLlama.auth.getUser();
  const usuario = sesion?.user;
  if (!usuario) return json({ error: "Sesión inválida." }, 401, origin);

  // ---- lo que se pide ----
  let cuerpo: { solicitudId?: unknown };
  try {
    cuerpo = await req.json();
  } catch (_e) {
    return json({ error: "Petición ilegible" }, 400, origin);
  }
  const solicitudId = String(cuerpo.solicitudId ?? "").trim();
  if (!solicitudId) return json({ error: "Falta la solicitud." }, 400, origin);

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  );

  // ---- la solicitud ----
  const { data: filaSolicitud, error: errorSolicitud } = await supabase
    .from("order_requests")
    .select(
      "id, folio, created_by, cliente_nombre, cliente_email, total_estimado, " +
        "envio_costo, envio_descripcion, metodo_entrega, " +
        "pago_estado, pago_orden_id, pago_total, codigo_descuento, " +
        "order_request_items(variant_id, cantidad)",
    )
    .eq("id", solicitudId)
    .maybeSingle();
  const solicitud = filaSolicitud as FilaSolicitud | null;

  if (errorSolicitud || !solicitud) {
    return json({ error: "No encontramos esa solicitud." }, 404, origin);
  }

  // Es suya, o es de administración. Se comprueba aquí y no con el RLS porque esta
  // consulta va con service_role: la lee entera para calcular, así que el permiso hay
  // que mirarlo a mano.
  const esSuya = solicitud.created_by === usuario.id;
  if (!esSuya) {
    const { data: esAdmin } = await comoQuienLlama.rpc("is_admin");
    if (esAdmin !== true) {
      return json({ error: "Esa solicitud no es tuya." }, 403, origin);
    }
  }

  // Ya pagada. Se corta antes de crear nada: cobrar dos veces lo mismo es el error que
  // más cuesta deshacer.
  if (solicitud.pago_estado === "aprobado") {
    return json({ error: "Esta solicitud ya está pagada." }, 409, origin);
  }

  // ---- el monto ----
  // Los precios salen de `product_variants`, no de `order_request_items`. Esa columna
  // la escribe el navegador al crear la solicitud y sirve para dejar constancia de lo
  // que la persona vio; para cobrar no sirve.
  const renglones = solicitud.order_request_items ?? [];
  if (renglones.length === 0) {
    return json({ error: "Esa solicitud no tiene artículos." }, 400, origin);
  }

  const ids = [...new Set(renglones.map((r) => r.variant_id))];
  const { data: filasVariantes, error: errorVariantes } = await supabase
    .from("product_variants")
    .select("id, precio_venta")
    .in("id", ids);
  const variantes = (filasVariantes ?? []) as unknown as FilaVariante[];

  if (errorVariantes || variantes.length !== ids.length) {
    return json(
      { error: "Alguno de los productos ya no está disponible. Escríbenos." },
      409,
      origin,
    );
  }

  const precios = new Map<string, number>(
    variantes.map((v) => [v.id, Number(v.precio_venta ?? 0)]),
  );

  let totalArticulos = 0;
  for (const r of renglones) {
    const precio = precios.get(r.variant_id) ?? 0;
    const cantidad = Math.max(0, Math.floor(Number(r.cantidad ?? 0)));
    if (precio <= 0 || cantidad <= 0) {
      return json(
        { error: "Alguno de los productos ya no tiene precio. Escríbenos." },
        409,
        origin,
      );
    }
    totalArticulos += precio * cantidad;
  }
  totalArticulos = Math.round(totalArticulos * 100) / 100;

  // Si los precios se movieron desde que se armó la solicitud, **no se cobra**.
  //
  // Cobrar más de lo que la persona aceptó no se hace. Cobrar menos es una pérdida que
  // la tienda no decidió. Y en el camino lento —se paga días después, por WhatsApp— es
  // un caso real, no teórico: una promoción empieza o termina en medio. Así que se
  // para y se avisa, en vez de resolverlo por nuestra cuenta.
  const estimado = solicitud.total_estimado == null
    ? null
    : Number(solicitud.total_estimado);
  if (estimado != null && Math.abs(totalArticulos - estimado) > 0.01) {
    return json(
      {
        error: "Los precios de tu solicitud cambiaron. Escríbenos y la ponemos al día " +
          "antes de cobrar.",
        estimado: dinero(estimado),
        ahora: dinero(totalArticulos),
      },
      409,
      origin,
    );
  }

  // ---- el descuento ----
  //
  // Se **vuelve a validar aquí**, aunque `validar-codigo` ya lo hiciera. Aquélla es
  // para enseñar el total antes de pulsar; ésta es la que cobra. Del navegador llega
  // el código y nada más: si llegara el porcentaje, bastaría con editar la petición
  // para regalarse un 100 %.
  const descuento = await descuentoDe(
    supabase,
    limpio(solicitud.codigo_descuento),
    totalArticulos,
  );
  if (descuento.error) return json({ error: descuento.error }, 409, origin);

  // El envío sí se toma de la solicitud: lo puso la cotización, que ya era del
  // servidor, y es lo que se le prometió. Recotizar aquí lo cambiaría a espaldas suyas.
  //
  // Y **no se descuenta**: lo paga la tienda entero a la paquetería, así que recortarlo
  // saldría del bolsillo de la tienda sin que el cliente lo note.
  const envio = Math.max(0, Number(solicitud.envio_costo ?? 0));
  const total = Math.round((totalArticulos - descuento.monto + envio) * 100) / 100;

  if (total < MINIMO) {
    return json(
      {
        error: `El total con descuento queda por debajo del mínimo que podemos ` +
          `cobrar (${dinero(MINIMO)}). Escríbenos y lo resolvemos por WhatsApp.`,
      },
      409,
      origin,
    );
  }

  // ---- la orden ----
  // Un renglón genérico por los artículos y, si lo hay, otro por el envío. Mercado Pago
  // exige que `total_amount` sea exactamente la suma de los renglones, así que el envío
  // va como renglón y no como un extra aparte. De paso se lee desglosado en su página,
  // que es lo que la persona espera ver.
  // El descuento se resta del renglón de artículos en vez de ir como uno negativo:
  // Mercado Pago exige que `total_amount` sea la suma de los renglones, y un importe
  // negativo ahí es pedir un rechazo. El desglose lo ve la persona en nuestro acuse.
  const items: Array<Record<string, unknown>> = [
    {
      title: CONCEPTO,
      quantity: 1,
      unit_price: dinero(totalArticulos - descuento.monto),
      external_code: `solicitud-${solicitud.folio ?? ""}`,
    },
  ];
  if (envio > 0) {
    items.push({
      title: `Envío${solicitud.envio_descripcion ? ` · ${solicitud.envio_descripcion}` : ""}`,
      quantity: 1,
      unit_price: dinero(envio),
      external_code: "envio",
    });
  }

  const orden: Record<string, unknown> = {
    type: "online",
    processing_mode: "manual",
    total_amount: dinero(total),
    // Esto es lo que nos devuelve el webhook para saber de qué solicitud habla. Sin
    // ello, un aviso de pago sería un monto sin dueño.
    external_reference: solicitud.id,
    description: CONCEPTO,
    items,
    config: {
      online: {
        // Las tres al mismo sitio, y a la raíz. La app enruta por hash (`#/cuenta`), y
        // Mercado Pago le pega sus propios parámetros a la URL: con un hash en medio,
        // acabarían dentro del fragmento y la app no los vería. Desde la raíz, la app
        // reconoce esos parámetros y lleva al historial.
        //
        // **El regreso no es prueba de pago.** Es una URL que la persona puede escribir
        // a mano. Lo que vale es el webhook; esto solo sirve para no dejarla mirando la
        // página de Mercado Pago.
        success_url: `${SITIO}/`,
        failure_url: `${SITIO}/`,
        pending_url: `${SITIO}/`,
        auto_return: "approved",
      },
      payment_method: {
        max_installments: MAX_MESES,
      },
      // Dentro de `config`, no al nivel superior. Estaba fuera y Mercado Pago
      // respondía 400 a la orden entera por un campo que no reconocía ahí.
      statement_descriptor: await statement(),
    },
  };

  // El correo y el nombre solo para que no los vuelva a escribir. La dirección **no**
  // va: sigue cifrada extremo a extremo y Mercado Pago no la necesita para cobrar.
  const payer: Record<string, unknown> = {};
  if (solicitud.cliente_email) payer.email = String(solicitud.cliente_email);
  if (solicitud.cliente_nombre) payer.first_name = String(solicitud.cliente_nombre);
  if (Object.keys(payer).length > 0) orden.payer = payer;

  // ---- ¿ya hay una orden abierta? ----
  //
  // Quien cerró la pestaña a medio pagar vuelve y pulsa «Continuar el pago». Lo que
  // quiere es **esa** orden, no otra: la suya sigue abierta en Mercado Pago y abrir una
  // segunda dejaría dos cobros vivos por lo mismo.
  //
  // Esto además es obligatorio, no una mejora: `X-Idempotency-Key` es la misma para la
  // misma solicitud y el mismo monto, y Mercado Pago responde 409 a una llave ya usada.
  // Sin este camino, «Continuar el pago» fallaría siempre.
  //
  // También tras un rechazo, no solo con el pago pendiente. Cuando una tarjeta no pasa,
  // Mercado Pago deja la orden en `action_required` / `waiting_retry`: sigue viva y
  // esperando otro intento. Si abriéramos una segunda, quedarían dos órdenes pagables
  // por lo mismo, y quien tenga las dos pestañas abiertas podría pagarlas las dos.
  // Quien decide si la vieja sirve es Mercado Pago, en `ordenAbierta`.
  if (
    (solicitud.pago_estado === "pendiente" || solicitud.pago_estado === "rechazado") &&
    solicitud.pago_orden_id &&
    Math.abs(Number(solicitud.pago_total ?? 0) - total) <= 0.01
  ) {
    const abierta = await ordenAbierta(solicitud.pago_orden_id);
    if (abierta) {
      return json(
        { checkoutUrl: abierta, ordenId: solicitud.pago_orden_id, total: dinero(total) },
        200,
        origin,
      );
    }
  }

  // La llave de idempotencia: la misma solicitud, el mismo monto y el mismo minuto dan
  // la misma llave. Si alguien pulsa «Pagar» dos veces, Mercado Pago reconoce el segundo
  // intento en vez de abrir dos cobros.
  //
  // El minuto está ahí para que un intento **de verdad nuevo** —la orden de antes
  // caducó, o la tarjeta se rechazó y se reintenta un rato después— pueda crear otra
  // orden en vez de chocar con la llave vieja. Un minuto es largo para dos clics
  // seguidos y corto para volver a intentarlo.
  const minuto = Math.floor(Date.now() / 60_000);
  const llave = `${solicitud.id}:${dinero(total)}:${minuto}`;

  let respuesta: Response;
  try {
    respuesta = await fetch(`${MP_API}/v1/orders`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${MP_TOKEN}`,
        "Content-Type": "application/json",
        "X-Idempotency-Key": llave,
      },
      body: JSON.stringify(orden),
      signal: AbortSignal.timeout(20_000),
    });
  } catch (e) {
    console.error("mercado pago no contestó", String(e));
    return json(
      { error: "No pudimos abrir el pago ahora mismo. Inténtalo de nuevo." },
      502,
      origin,
    );
  }

  // El cuerpo se lee como texto y luego se interpreta: un 401 de Mercado Pago no trae
  // JSON, y `respuesta.json()` reventaría tapando el motivo real con un error de
  // parseo.
  const texto = await respuesta.text();
  let datos: Record<string, unknown> = {};
  try {
    datos = JSON.parse(texto) as Record<string, unknown>;
  } catch (_e) {
    datos = {};
  }

  const checkoutUrl = String(datos.checkout_url ?? "").trim();
  if (!respuesta.ok || !checkoutUrl) {
    console.error(
      "mercado pago rechazó la orden",
      respuesta.status,
      texto.slice(0, 2000),
    );
    return json(
      {
        error: "No pudimos abrir el pago ahora mismo. Inténtalo de nuevo.",
        estadoMp: respuesta.status,
        // Con MP_DEBUG, el motivo de Mercado Pago tal cual, y lo que le mandamos.
        //
        // Sin esto, un 502 en la consola del navegador no dice nada: hay que ir a los
        // logs de Supabase a buscarlo. Y lo que casi siempre falla aquí es un campo con
        // el tipo equivocado, que solo se ve comparando lo enviado con su queja.
        //
        // **Apagarlo en producción**: esto enseña el detalle de nuestra integración a
        // quien pulse el botón.
        ...(DEBUG ? { detalle: texto.slice(0, 2000), enviado: orden } : {}),
      },
      502,
      origin,
    );
  }

  const ordenId = String(datos.id ?? "").trim();

  // Se guarda la orden **antes** de devolverla. Si se devolviera primero y el guardado
  // fallara, la persona pagaría una orden que no sabemos de quién es: el webhook trae
  // el `external_reference`, así que se podría reconstruir, pero la pantalla diría
  // «sin pagar» mientras el dinero ya salió.
  const { error: errorGuardar } = await supabase
    .from("order_requests")
    .update({
      pago_estado: "pendiente",
      pago_total: total,
      pago_orden_id: ordenId || null,
      pago_actualizado_en: new Date().toISOString(),
      // Lo que se descontó, copiado aquí: si mañana cambia el premio, lo que se le
      // aplicó a esta persona no lo reescribe nadie.
      descuento_pct: descuento.pct || null,
      descuento_monto: descuento.monto || null,
      regalo: descuento.regalo || null,
    } as never)
    .eq("id", solicitud.id);

  if (errorGuardar) {
    console.error("no se pudo guardar la orden de pago", errorGuardar.message);
  }

  await anotar({
    request_id: solicitud.id,
    accion: "creada",
    orden_id: ordenId || null,
    monto: total,
    usuario_id: usuario.id,
    usuario: usuario.email ?? null,
    nota: esSuya ? null : "Creada por administración",
  });

  return json({ checkoutUrl, ordenId, total: dinero(total) }, 200, origin);
});
