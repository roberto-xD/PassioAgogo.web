// ============================================================
// Edge Function · generar-guia
//
// Convierte una solicitud en un paquete: pide la guía a Envia, guarda
// el número de rastreo y el PDF de la etiqueta.
//
// **Por qué existe esta función y no lo hace la app de control.**
// La app tiene la clave privada y es la única que puede abrir la
// dirección del cliente. Pero el token de Envia no puede vivir en un
// APK: un APK se descompila, y ese token genera guías reales y cobra.
// Así que el trabajo se reparte: la app abre el sobre y manda la
// dirección ya en claro; aquí está el token y se pide la guía.
//
// La dirección **pasa** por este servidor, no **se queda**: va a Envia,
// que tiene que saber a dónde llevar el paquete, y no se escribe en
// ninguna tabla. Lo que sigue cifrado en la base sigue cifrado.
//
// **Esta es la única función que gasta dinero.** Por eso es la única
// que comprueba quién llama: sin sesión de administración, no hace
// nada. Y una solicitud que ya tiene guía no genera otra: dos clics
// seguidos cobrarían dos veces y la segunda etiqueta quedaría impresa,
// pagada y pegada en ninguna caja.
//
// Secrets (Supabase → Edge Functions → Secrets):
//   ENVIA_TOKEN    token de Envia. Sandbox mientras se prueba.
//   ENVIA_BASE_URL https://api-test.envia.com (default) o api.envia.com
//   ENVIA_DEBUG    "1" añade el detalle del rechazo a la respuesta.
// SUPABASE_URL, SUPABASE_ANON_KEY y SUPABASE_SERVICE_ROLE_KEY los
// inyecta la plataforma.
// ============================================================

import { createClient } from "jsr:@supabase/supabase-js@2";

const ENVIA_TOKEN = (Deno.env.get("ENVIA_TOKEN") ?? "")
  .trim()
  .replace(/^Bearer\s+/i, "")
  .trim();
const ENVIA_BASE = (Deno.env.get("ENVIA_BASE_URL") ?? "https://api-test.envia.com")
  .replace(/\/+$/, "");
const DEBUG = Deno.env.get("ENVIA_DEBUG") === "1";

/** Lo que la tienda declara en la guía. Nunca el nombre del producto. */
const CONTENIDO = "Articulos de higiene personal";

/**
 * Formato de la etiqueta.
 *
 * `/ship/generate` exige `settings` con estos dos, y `/ship/rate` no: cotizar no imprime
 * nada. PDF en 4x6 es la etiqueta térmica de toda la vida, y también sale bien impresa
 * en una hoja normal.
 */
const ETIQUETA_FORMATO = Deno.env.get("ENVIA_ETIQUETA_FORMATO") ?? "PDF";
const ETIQUETA_TAMANO = Deno.env.get("ENVIA_ETIQUETA_TAMANO") ?? "STOCK_4X6";

/** La caja más grande que maneja la tienda, y su tope de peso. */
const CAJA_MAX = { largo: 100, ancho: 50, alto: 50 };
const PESO_MAX_KG = 40;

const CP_VALIDO = /^[0-9]{5}$/;

interface FilaOrigen {
  nombre: string | null;
  contacto: string | null;
  telefono: string | null;
  calle: string | null;
  numero: string | null;
  colonia: string | null;
  ciudad: string | null;
  estado_codigo: string | null;
  cp: string | null;
  pais: string | null;
}

/**
 * La solicitud, escrita a mano porque el cliente de Supabase no conoce el esquema: sin
 * esto, una columna mal escrita no la cazaría nadie hasta que la guía saliera incompleta.
 */
interface FilaSolicitud {
  id: string;
  folio: number | null;
  cliente_nombre: string | null;
  cliente_telefono: string | null;
  metodo_entrega: string | null;
  envio_paqueteria: string | null;
  envio_servicio: string | null;
  guia_envia_id: string | null;
  guia_numero: string | null;
  order_request_items: Array<
    { variant_id: string; cantidad: number; precio_estimado: number }
  > | null;
}

/** La dirección del cliente, ya abierta por la app de control. */
interface Destino {
  calle?: string;
  interior?: string;
  colonia?: string;
  cp?: string;
  ciudad?: string;
  estado?: string;
  estadoCodigo?: string;
  referencias?: string;
}

function corsHeaders(): Record<string, string> {
  return {
    // La app de control no es un navegador: no manda Origin y no hay lista de orígenes
    // que valga. Lo que protege esta función es la sesión de administración, no el CORS.
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers":
      "authorization, apikey, content-type, x-client-info",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Max-Age": "86400",
    "Content-Type": "application/json",
  };
}

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders() });
}

/**
 * El paquete del pedido.
 *
 * Es la misma regla que usa `cotizar-envio`, copiada a propósito: las Edge Functions se
 * despliegan sueltas y esta se pega entera en el editor del panel, así que un módulo
 * compartido rompería ese camino. Si la regla cambia, cambia en los dos sitios — y por
 * eso está escrita igual en ambos, para que la diferencia salte a la vista.
 */
function armarPaquete(
  filas: Array<{ peso_gr: number; largo_cm: number; ancho_cm: number; alto_cm: number }>,
  cantidades: number[],
) {
  let pesoGr = 0;
  let volumen = 0;
  const base = [0, 0, 0];

  filas.forEach((fila, i) => {
    const cantidad = cantidades[i];
    pesoGr += fila.peso_gr * cantidad;
    volumen += fila.largo_cm * fila.ancho_cm * fila.alto_cm * cantidad;
    const lados = [fila.largo_cm, fila.ancho_cm, fila.alto_cm].sort((a, b) => b - a);
    for (let j = 0; j < 3; j++) base[j] = Math.max(base[j], lados[j]);
  });

  let largo = Math.max(1, base[0]);
  let ancho = Math.max(1, base[1]);
  let alto = Math.max(1, base[2]);

  const factor = Math.cbrt(volumen / (largo * ancho * alto));
  if (factor > 1) {
    largo = Math.ceil(largo * factor);
    ancho = Math.ceil(ancho * factor);
    alto = Math.ceil(alto * factor);
  }

  largo = Math.min(CAJA_MAX.largo, largo);
  ancho = Math.min(CAJA_MAX.ancho, ancho);
  alto = Math.min(CAJA_MAX.alto, alto);
  if (largo * ancho * alto < volumen) {
    largo = Math.min(CAJA_MAX.largo, Math.ceil(volumen / (ancho * alto)));
  }

  const pesoKg = Math.min(PESO_MAX_KG, Math.max(0.1, pesoGr / 1000));
  return { peso: Math.round(pesoKg * 100) / 100, largo, ancho, alto };
}

/**
 * Separa el número de la calle.
 *
 * El formulario pide «calle y número» en un solo campo, que es como la gente escribe una
 * dirección, pero la paquetería quiere los dos por separado. Se corta el número del
 * final si está ahí; si no, va «S/N», que es lo que se pone en una guía y lo que un
 * repartidor entiende. Nunca se inventa un número.
 */
function partirCalle(calle: string): { street: string; number: string } {
  const limpia = calle.trim();
  const m = limpia.match(/^(.*?)[\s,]+(\d+[A-Za-z]?)$/);
  if (m && m[1].trim()) return { street: m[1].trim(), number: m[2] };
  return { street: limpia, number: "S/N" };
}

/** Ciudad y estado de un código postal, por si el sobre guardado no los trae. */
async function lugarDe(cp: string) {
  try {
    const r = await fetch(`https://geocodes.envia.com/zipcode/MX/${cp}`, {
      signal: AbortSignal.timeout(6000),
    });
    if (!r.ok) return null;
    const filas = await r.json();
    const fila = Array.isArray(filas) ? filas[0] : null;
    const codigo = fila?.state?.code?.["2digit"] ?? "";
    if (!codigo) return null;
    return { ciudad: String(fila.locality ?? ""), estado: String(codigo) };
  } catch (_e) {
    return null;
  }
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders() });
  }
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);
  if (!ENVIA_TOKEN) return json({ error: "Falta configurar el token de Envia." }, 503);

  // ---- quién llama ----
  // Con **su** token, no con service_role: así el RLS se aplica igual que a cualquiera
  // y lo que se comprueba es la sesión de verdad, no una que digamos que tiene.
  const token = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "").trim();
  if (!token) return json({ error: "Falta la sesión." }, 401);

  const comoQuienLlama = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_ANON_KEY") ?? "",
    { global: { headers: { Authorization: `Bearer ${token}` } } },
  );

  const { data: sesion } = await comoQuienLlama.auth.getUser();
  if (!sesion?.user) return json({ error: "Sesión inválida." }, 401);

  // El rol lo dice la base, no el cliente. `is_admin()` ya existe y es la misma
  // definición que usan las políticas: si algún día cambia, cambia aquí también.
  const { data: esAdmin, error: errorRol } = await comoQuienLlama.rpc("is_admin");
  if (errorRol || esAdmin !== true) {
    return json({ error: "Solo administración puede generar guías." }, 403);
  }

  // ---- lo que se pide ----
  let cuerpo: {
    solicitudId?: unknown;
    direccion?: unknown;
    carrier?: unknown;
    service?: unknown;
    recoleccion?: unknown;
  };
  try {
    cuerpo = await req.json();
  } catch (_e) {
    return json({ error: "Petición ilegible" }, 400);
  }

  const solicitudId = String(cuerpo.solicitudId ?? "").trim();
  if (!solicitudId) return json({ error: "Falta la solicitud." }, 400);

  const destino = (cuerpo.direccion ?? {}) as Destino;
  const cpDestino = String(destino.cp ?? "").replace(/\D/g, "");
  if (!CP_VALIDO.test(cpDestino)) {
    return json({ error: "La dirección no trae un código postal válido." }, 400);
  }
  if (!String(destino.calle ?? "").trim()) {
    return json({ error: "La dirección no trae calle." }, 400);
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  );

  // ---- la solicitud ----
  const { data: filaSolicitud } = await supabase
    .from("order_requests")
    .select(
      "id, folio, cliente_nombre, cliente_telefono, metodo_entrega, " +
        "envio_paqueteria, envio_servicio, guia_envia_id, guia_numero, " +
        "order_request_items(variant_id, cantidad, precio_estimado)",
    )
    .eq("id", solicitudId)
    .maybeSingle();
  const solicitud = filaSolicitud as FilaSolicitud | null;

  if (!solicitud) return json({ error: "No encontramos esa solicitud." }, 404);

  // Ya tiene guía: se devuelve la que hay en vez de cobrar otra.
  if (solicitud.guia_envia_id) {
    return json({
      yaTenia: true,
      guia: { numero: solicitud.guia_numero, enviaId: solicitud.guia_envia_id },
    }, 200);
  }
  if (solicitud.metodo_entrega === "personal") {
    return json({ error: "Ese pedido es para entrega en persona, no lleva guía." }, 409);
  }

  const renglones = solicitud.order_request_items ?? [];
  if (renglones.length === 0) return json({ error: "La solicitud no tiene artículos." }, 409);

  // ---- el origen ----
  const { data: filaOrigen } = await supabase
    .from("locations")
    .select(
      "nombre, contacto, telefono, calle, numero, colonia, ciudad, estado_codigo, cp, pais",
    )
    .eq("es_origen", true)
    .maybeSingle();
  const origen = filaOrigen as FilaOrigen | null;

  if (!origen?.cp || !origen?.estado_codigo || !origen?.telefono) {
    return json({ error: "Falta configurar la dirección de origen de la tienda." }, 503);
  }

  // ---- pesos: de la base, como al cotizar ----
  const ids = [...new Set(renglones.map((r) => r.variant_id))];
  const { data: filasVariantes } = await supabase
    .from("product_variants")
    .select("id, precio_venta, peso_gr, largo_cm, ancho_cm, alto_cm")
    .in("id", ids);

  const porId = new Map(
    ((filasVariantes ?? []) as Array<
      {
        id: string;
        precio_venta: number | null;
        peso_gr: number;
        largo_cm: number;
        ancho_cm: number;
        alto_cm: number;
      }
    >).map((v) => [v.id, v]),
  );
  if (porId.size !== ids.length) {
    return json({ error: "Alguno de los artículos ya no existe en el catálogo." }, 409);
  }

  const paquete = armarPaquete(
    renglones.map((r) => porId.get(r.variant_id)!),
    renglones.map((r) => r.cantidad),
  );
  const valor = renglones.reduce(
    (s, r) => s + Number(r.precio_estimado ?? porId.get(r.variant_id)?.precio_venta ?? 0) * r.cantidad,
    0,
  );

  // Con qué enviar: lo que pidan ahora, o lo que el cliente eligió al cotizar.
  const carrier = String(cuerpo.carrier ?? solicitud.envio_paqueteria ?? "").trim().toLowerCase();
  const service = String(cuerpo.service ?? solicitud.envio_servicio ?? "").trim().toLowerCase();
  if (!carrier || !service) {
    return json({ error: "Falta elegir paquetería y servicio." }, 400);
  }

  // Ciudad y estado: los del sobre si los trae; si no, los del código postal. Los
  // sobres guardados antes de que existiera esa consulta no llevan el código de estado,
  // y la paquetería lo quiere de dos letras.
  //
  // Se compara contra cadena vacía y no con `??`: un sobre viejo trae `estadoCodigo: ""`,
  // no ausente, y `??` solo cae del lado del respaldo con null. Con `??` el estado
  // viajaba vacío a la paquetería y la guía salía sin estado.
  const codigoDelSobre = String(destino.estadoCodigo ?? "").trim();
  const ciudadDelSobre = String(destino.ciudad ?? "").trim();
  const delCp = (!codigoDelSobre || !ciudadDelSobre) ? await lugarDe(cpDestino) : null;

  const ciudadDestino = ciudadDelSobre || delCp?.ciudad || "";
  const estadoDestino = codigoDelSobre || delCp?.estado || "";
  if (!estadoDestino) {
    return json({
      error: "No pudimos resolver el estado del código postal del cliente.",
    }, 409);
  }
  const { street, number } = partirCalle(String(destino.calle));

  const referencia = [
    destino.interior ? `Int. ${destino.interior}` : "",
    String(destino.referencias ?? "").trim(),
  ].filter((s) => s).join(". ");

  const peticion = {
    origin: {
      name: origen.contacto || origen.nombre || "Passion à gogo",
      company: origen.nombre || "Passion à gogo",
      phone: origen.telefono,
      street: origen.calle ?? "",
      number: origen.numero ?? "S/N",
      district: origen.colonia ?? "",
      city: origen.ciudad ?? "",
      state: origen.estado_codigo,
      country: origen.pais ?? "MX",
      postalCode: origen.cp,
    },
    destination: {
      name: solicitud.cliente_nombre ?? "Destinatario",
      phone: solicitud.cliente_telefono ?? origen.telefono,
      street,
      number,
      district: String(destino.colonia ?? "").trim(),
      city: ciudadDestino,
      state: estadoDestino,
      country: "MX",
      postalCode: cpDestino,
      reference: referencia,
    },
    packages: [{
      type: "box",
      content: CONTENIDO,
      amount: 1,
      declaredValue: Math.round(valor * 100) / 100,
      lengthUnit: "CM",
      weightUnit: "KG",
      weight: paquete.peso,
      dimensions: { length: paquete.largo, width: paquete.ancho, height: paquete.alto },
    }],
    shipment: { type: 1, carrier, service },
    // Obligatorio al generar, y no existe al cotizar: cotizar no imprime nada.
    settings: {
      currency: "MXN",
      printFormat: ETIQUETA_FORMATO,
      printSize: ETIQUETA_TAMANO,
    },
  };

  // ---- la guía ----
  let respuesta: Response;
  try {
    respuesta = await fetch(`${ENVIA_BASE}/ship/generate/`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${ENVIA_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(peticion),
      signal: AbortSignal.timeout(25000),
    });
  } catch (e) {
    // Puede haberse generado y perdido la respuesta: no se reintenta solo.
    console.error("envia /ship/generate sin respuesta", String(e));
    return json({
      error: "La paquetería no respondió. Revisa en su panel si la guía se generó " +
        "antes de volver a intentarlo.",
    }, 504);
  }

  const texto = await respuesta.text().catch(() => "");
  let crudo: Record<string, unknown> | null = null;
  try {
    crudo = texto ? JSON.parse(texto) : null;
  } catch (_e) {
    crudo = null;
  }

  // Envia contesta 200 con `meta: "error"` cuando rechaza: mirar solo el código HTTP
  // daría por buena una respuesta sin guía.
  const detalle = crudo?.error as Record<string, unknown> | undefined;
  if (!respuesta.ok || crudo?.meta === "error") {
    console.error("envia /ship/generate", respuesta.status, texto.slice(0, 2000));
    return json({
      error: "No se pudo generar la guía.",
      motivo: String(detalle?.message ?? detalle?.description ?? respuesta.status),
      ...(DEBUG ? { cuerpo: texto.slice(0, 2000), peticion } : {}),
    }, 502);
  }

  const datos = Array.isArray(crudo?.data) ? crudo.data[0] as Record<string, unknown> : null;
  const numero = String(datos?.trackingNumber ?? "").trim();
  if (!numero) {
    console.error("envia /ship/generate sin numero", texto.slice(0, 2000));
    return json({ error: "La paquetería no devolvió número de rastreo." }, 502);
  }

  const guia = {
    numero,
    url: String(datos?.label ?? ""),
    rastreo: String(datos?.trackUrl ?? ""),
    paqueteria: String(datos?.carrier ?? carrier),
    servicio: String(datos?.service ?? service),
    costo: Number(datos?.totalPrice ?? 0),
    enviaId: String(datos?.shipmentId ?? ""),
  };

  // ---- se guarda ----
  // Si esto fallara, la guía existe y está pagada pero no quedaría registrada: por eso
  // el número viaja en la respuesta pase lo que pase, para poder anotarlo a mano.
  const { error: errorGuardar } = await supabase
    .from("order_requests")
    .update({
      guia_numero: guia.numero,
      guia_url: guia.url,
      guia_rastreo_url: guia.rastreo,
      guia_paqueteria: guia.paqueteria,
      guia_servicio: guia.servicio,
      guia_costo: guia.costo,
      guia_envia_id: guia.enviaId,
      guia_generada_en: new Date().toISOString(),
      // Se marca ahora, que es cuando se sabe con qué paquetería va, pero **no se pide
      // aquí**: Envia agrupa varias guías en una sola recolección si son de la misma
      // paquetería y salen del mismo sitio, y pedir una por pedido sería un camión por
      // paquete. La lista de lo pendiente está en `v_recolecciones_pendientes`.
      necesita_recoleccion: cuerpo.recoleccion === true,
    })
    .eq("id", solicitudId);

  if (errorGuardar) {
    console.error("no se pudo guardar la guia", solicitudId, errorGuardar.message);
    return json({
      guia,
      aviso: "La guía se generó pero no se pudo guardar en la solicitud. " +
        "Anota el número de rastreo.",
    }, 200);
  }

  return json({ guia }, 200);
});
