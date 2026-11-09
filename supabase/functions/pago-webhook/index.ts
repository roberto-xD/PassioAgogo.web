// ============================================================
// Edge Function · pago-webhook
//
// Recibe los avisos de Mercado Pago y es **la única cosa que puede
// marcar una solicitud como pagada**.
//
// ── Esta función se despliega SIN verificación de JWT ──
//
//   supabase functions deploy pago-webhook --no-verify-jwt
//
// o, en el panel: Edge Functions → pago-webhook → Details →
// «Verify JWT with legacy secret» apagado. Mercado Pago no tiene
// sesión en Supabase: si el JWT se exige, todos los avisos rebotan con
// 401 y los pagos se quedan en «pendiente» para siempre.
//
// Que esté abierta al mundo es justo por lo que hay dos candados, y
// ninguno es opcional:
//
//  1. **La firma.** `x-signature` trae un HMAC-SHA256 que solo puede
//     calcular quien tiene el secreto del webhook. Sin firma válida no
//     se mira el cuerpo siquiera.
//  2. **Volver a preguntar.** El cuerpo del aviso **no se cree**: dice
//     qué pago mirar y nada más. El estado y el monto se le preguntan a
//     Mercado Pago con nuestro token. Un aviso dice lo que quiera quien
//     lo manda; la respuesta a nuestra propia consulta, no.
//
// El segundo candado es el que importa de verdad. Sin él, bastaría con
// adivinar la firma una vez para regalarse pedidos.
//
// Secrets (Supabase → Edge Functions → Secrets):
//   MP_ACCESS_TOKEN    el mismo que usa `crear-pago`.
//   MP_WEBHOOK_SECRET  la clave secreta del webhook. Se saca en Mercado
//                      Pago → Tus integraciones → la aplicación →
//                      Webhooks → Configurar notificación. **No es el
//                      access token**: son dos cosas distintas.
//   MP_DEBUG           "1" escribe el cuerpo del aviso en los logs,
//                      para afinar el mapeo. Apagarlo después.
// SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY los inyecta la plataforma.
// ============================================================

import { createClient } from "jsr:@supabase/supabase-js@2";

const MP_TOKEN = (Deno.env.get("MP_ACCESS_TOKEN") ?? "")
  .trim()
  .replace(/^Bearer\s+/i, "")
  .trim();
const MP_SECRET = (Deno.env.get("MP_WEBHOOK_SECRET") ?? "").trim();
const MP_API = "https://api.mercadopago.com";
const DEBUG = Deno.env.get("MP_DEBUG") === "1";

/**
 * Los estados de Mercado Pago traducidos a las cinco palabras que usa la pantalla.
 *
 * Mercado Pago distingue muchos más —por qué se rechazó la tarjeta, si el contracargo
 * está en disputa—. Esos matices se guardan en `pagos.estado_crudo` sin traducir,
 * porque para entender un rechazo hacen falta sus palabras; pero al cliente, en su
 * historial, «rechazado» le dice lo que necesita saber.
 */
const ESTADOS: Record<string, string> = {
  // Pagos
  approved: "aprobado",
  accredited: "aprobado",
  authorized: "pendiente",
  pending: "pendiente",
  in_process: "pendiente",
  in_mediation: "pendiente",
  rejected: "rechazado",
  cancelled: "cancelado",
  canceled: "cancelado",
  refunded: "reembolsado",
  partially_refunded: "reembolsado",
  charged_back: "reembolsado",
  // Órdenes
  created: "pendiente",
  action_required: "pendiente",
  processed: "aprobado",
  failed: "rechazado",
  expired: "cancelado",
};

/**
 * ¿La firma es nuestra?
 *
 * La plantilla que firma Mercado Pago es `id:<data.id>;request-id:<x-request-id>;ts:<ts>;`
 * y el HMAC es SHA-256 en hexadecimal, con el secreto del webhook como llave.
 *
 * Los pares que no vengan se omiten de la plantilla. Eso lo dice la práctica, no la
 * documentación, que en este punto se contradice entre regiones: **conviene comprobarlo
 * con un aviso real antes de confiar en que todos pasan**. Mientras no esté comprobado,
 * un fallo aquí se ve en los logs como «firma inválida» y el pago se queda pendiente —
 * molesto, pero no peligroso, que es el lado correcto por el que equivocarse.
 */
async function firmaValida(
  signature: string,
  requestId: string,
  dataId: string,
): Promise<boolean> {
  const partes = new Map<string, string>();
  for (const trozo of signature.split(",")) {
    const i = trozo.indexOf("=");
    if (i > 0) {
      partes.set(trozo.slice(0, i).trim(), trozo.slice(i + 1).trim());
    }
  }
  const ts = partes.get("ts") ?? "";
  const v1 = partes.get("v1") ?? "";
  if (!ts || !v1) return false;

  const armar = (id: string) =>
    [
      id ? `id:${id};` : "",
      requestId ? `request-id:${requestId};` : "",
      `ts:${ts};`,
    ].join("");

  // Varias formas del manifiesto, porque la documentación no las fija.
  //
  //  - El id **en minúsculas**. Con la API vieja de pagos los ids eran numéricos y la
  //    caja daba igual; los de la Orders API son `ORDTST01M6MR8…`, en mayúsculas, y
  //    varias implementaciones lo pasan a minúsculas al firmar.
  //  - **Sin el id**, porque la documentación se contradice entre regiones sobre si los
  //    pares que faltan se omiten de la plantilla.
  //
  // Probar unas pocas no debilita nada: siguen haciendo falta el secreto y un HMAC
  // válido, y lo único que cambia es sobre qué texto. Quedarse con una sola forma y
  // acertar la equivocada deja todos los pagos sin confirmar, que es lo que pasa ahora.
  const candidatos = new Set([armar(dataId), armar(dataId.toLowerCase()), armar("")]);

  const llave = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(MP_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );

  for (const manifiesto of candidatos) {
    const firma = await crypto.subtle.sign(
      "HMAC",
      llave,
      new TextEncoder().encode(manifiesto),
    );
    const esperado = Array.from(new Uint8Array(firma))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");

    if (igualEnTiempoConstante(esperado, v1)) {
      if (DEBUG) console.log("firma válida con el manifiesto:", manifiesto);
      return true;
    }
  }

  // Con DEBUG, qué se probó. Sin el secreto ni las firmas: lo que hace falta para
  // entender un fallo es el texto que se firmó, no la llave.
  if (DEBUG) {
    console.error("ninguna forma del manifiesto cuadró:", [...candidatos].join(" | "));
  }
  return false;
}

/**
 * Compara sin que el tiempo delate cuánto se acertó.
 *
 * Saliendo al primer carácter distinto, el tiempo de respuesta diría cuántos caracteres
 * son correctos y la firma se podría adivinar a trozos.
 */
function igualEnTiempoConstante(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diferencia = 0;
  for (let i = 0; i < a.length; i++) {
    diferencia |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diferencia === 0;
}

/** Busca una clave a cualquier profundidad. Devuelve la primera que encuentre. */
// deno-lint-ignore no-explicit-any
function buscar(obj: any, clave: string, hondo = 0): unknown {
  if (hondo > 6 || obj == null || typeof obj !== "object") return undefined;
  if (!Array.isArray(obj) && Object.prototype.hasOwnProperty.call(obj, clave)) {
    const v = obj[clave];
    if (v != null && v !== "") return v;
  }
  for (const v of Object.values(obj)) {
    const hallado = buscar(v, clave, hondo + 1);
    if (hallado !== undefined) return hallado;
  }
  return undefined;
}

function texto(v: unknown): string {
  return v == null ? "" : String(v).trim();
}

/**
 * ¿De qué habla este aviso?
 *
 * Mercado Pago manda varios temas a la misma URL, y no todos son nuestros:
 *
 *  - `order` — la Orders API, que es con la que cobramos. Su id es `ORDTST01M6…`.
 *  - `payment` — la API clásica de pagos. Id numérico.
 *  - `topic_merchant_order_wh` / `merchant_order` — el aviso **antiguo** de «orden
 *    comercial», con un id numérico que no es el de una orden de la Orders API.
 *
 * El tercero llegaba y se le pedía `/v1/orders/<id numérico>`, Mercado Pago contestaba
 * `invalid_path_param`, y nosotros devolvíamos 500 — con lo que lo reintentaba una y
 * otra vez. Además de ensuciar los logs, un webhook que falla seguido acaba
 * desactivado. Y no se pierde nada ignorándolo: el aviso de `order` trae lo mismo y es
 * el que usamos.
 *
 * El orden de las comprobaciones importa: `topic_merchant_order_wh` **contiene**
 * «order», así que hay que descartarlo antes de preguntar por eso.
 */
function temaDelAviso(tipo: string): "orden" | "pago" | "otro" {
  if (tipo.includes("merchant_order")) return "otro";
  if (tipo.includes("payment")) return "pago";
  if (tipo.includes("order")) return "orden";
  return "otro";
}

const esDePago = (tipo: string) => temaDelAviso(tipo) === "pago";

/** Lo que le preguntamos a Mercado Pago, ya exprimido. */
interface Estado {
  solicitudId: string;
  estado: string;
  estadoCrudo: string;
  monto: number | null;
  /** La clase: `credit_card`, `debit_card`, `ticket`, `account_money`… */
  metodo: string;
  /** La marca: `master`, `visa`, `amex`, `oxxo`… */
  marca: string;
  /** Cuatro dígitos exactos, o vacío. Nunca el número completo. */
  ultimos4: string;
  /** Lo que Mercado Pago acredita a la tienda, sin su comisión. */
  neto: number | null;
  /** Cuándo libera el dinero. Acreditado no es disponible. */
  liberacion: string;
  /** El desglose de comisiones tal cual lo manda, sin interpretar. */
  comisiones: unknown;
  pagoId: string;
  ordenId: string;
}

/**
 * Le pregunta a Mercado Pago por el recurso del aviso.
 *
 * La forma exacta de la respuesta de Orders todavía no está comprobada contra un aviso
 * real, así que en vez de leer rutas fijas se busca cada dato por su nombre a cualquier
 * profundidad. Es menos elegante y mucho más difícil de romper: si Mercado Pago mueve
 * `status` un nivel más adentro, esto sigue funcionando.
 *
 * `null` cuando no se pudo preguntar o no se entendió la respuesta. Entonces no se toca
 * nada: dejar el pago pendiente es recuperable, marcarlo aprobado por error no.
 */
async function consultar(tipo: string, id: string): Promise<Estado | null> {
  const esPago = esDePago(tipo);
  const url = esPago ? `${MP_API}/v1/payments/${id}` : `${MP_API}/v1/orders/${id}`;

  let respuesta: Response;
  try {
    respuesta = await fetch(url, {
      headers: { "Authorization": `Bearer ${MP_TOKEN}` },
      signal: AbortSignal.timeout(15_000),
    });
  } catch (e) {
    console.error("no se pudo consultar a mercado pago", url, String(e));
    return null;
  }

  const cuerpo = await respuesta.text();
  if (!respuesta.ok) {
    console.error("mercado pago rechazó la consulta", respuesta.status, cuerpo.slice(0, 1000));
    return null;
  }

  // deno-lint-ignore no-explicit-any
  let datos: any;
  try {
    datos = JSON.parse(cuerpo);
  } catch (_e) {
    console.error("respuesta ilegible de mercado pago", cuerpo.slice(0, 500));
    return null;
  }
  if (DEBUG) console.log("recurso de mercado pago", cuerpo.slice(0, 4000));

  const solicitudId = texto(buscar(datos, "external_reference"));
  if (!solicitudId) {
    console.error("el recurso no trae external_reference", url);
    return null;
  }

  // El estado del pago manda sobre el de la orden: una orden «processed» cuyo pago se
  // reembolsó después sigue diciendo processed, y lo que hay que enseñar es el reembolso.
  const pagos = (buscar(datos, "payments") ?? []) as Array<Record<string, unknown>>;
  const ultimo = elPagoQueManda(pagos);

  const crudo = texto(
    (ultimo && (ultimo.status_detail ?? ultimo.status)) ??
      buscar(datos, "status_detail") ??
      buscar(datos, "status"),
  ).toLowerCase();
  const estadoBase = texto((ultimo && ultimo.status) ?? buscar(datos, "status"))
    .toLowerCase();

  // Se prueba con el detalle y, si no está en la tabla, con el estado a secas. Los
  // detalles son muchos y cambian; los estados son pocos y estables.
  const estado = ESTADOS[crudo] ?? ESTADOS[estadoBase] ?? "pendiente";
  if (!ESTADOS[crudo] && !ESTADOS[estadoBase]) {
    console.error("estado desconocido de mercado pago", crudo, estadoBase);
  }

  // `paid_amount` antes que `amount`: el primero es lo que de verdad se cobró y el
  // segundo lo que se pidió cobrar. Coinciden casi siempre, y cuando no, el bueno es el
  // primero. En un intento fallido `paid_amount` ni aparece.
  const montoCrudo = (ultimo && (ultimo.paid_amount ?? ultimo.amount)) ??
    buscar(datos, "total_paid_amount") ??
    buscar(datos, "transaction_amount") ??
    buscar(datos, "total_amount");
  const monto = montoCrudo == null ? null : Number(montoCrudo);

  const clase = metodoDe(ultimo, datos, "type");
  const clasico = await delPagoClasico(ultimo, clase, estado);

  return {
    solicitudId,
    estado,
    estadoCrudo: crudo || estadoBase,
    monto: monto != null && Number.isFinite(monto) ? monto : null,
    metodo: clase,
    marca: metodoDe(ultimo, datos, "id"),
    ultimos4: ultimos4De(datos) || clasico.ultimos4,
    neto: clasico.neto,
    liberacion: clasico.liberacion,
    comisiones: clasico.comisiones,
    pagoId: texto((ultimo && ultimo.id) ?? (esPago ? id : "")),
    ordenId: esPago ? texto(buscar(datos, "order_id")) : id,
  };
}

/** Lo que solo da la API clásica de pagos. Todo opcional: se vive sin ello. */
interface DelPagoClasico {
  ultimos4: string;
  /** Lo que Mercado Pago acredita, ya sin su comisión. */
  neto: number | null;
  /** Cuándo lo libera. Acreditado no es lo mismo que disponible. */
  liberacion: string;
  /** El desglose de comisiones tal cual, sin interpretar. */
  comisiones: unknown;
}

/**
 * Lo que la Orders API no cuenta, preguntándoselo a la API clásica de pagos.
 *
 * La Orders API no da ni los cuatro dígitos ni la comisión. Pero cada intento trae
 * `reference.id`, que es el identificador del pago en la API de siempre, y esa sí
 * devuelve `card.last_four_digits`, `transaction_details.net_received_amount`,
 * `money_release_date` y `fee_details`.
 *
 * Una sola llamada para las cuatro cosas, y solo cuando alguna sirve: con el pago
 * cobrado o devuelto —que es cuando hay comisión que contar— o con una tarjeta, de la
 * que al menos se sacan los dígitos.
 *
 * Nunca lanza. Si no se puede preguntar, se sigue sin estos datos: el estado del pago
 * ya está, y perderlo por no poder adornarlo sería absurdo.
 */
// deno-lint-ignore no-explicit-any
async function delPagoClasico(
  pago: any,
  clase: string,
  estado: string,
): Promise<DelPagoClasico> {
  const vacio: DelPagoClasico = {
    ultimos4: "",
    neto: null,
    liberacion: "",
    comisiones: null,
  };

  const referencia = texto(pago?.reference?.id);
  const esTarjeta = ["credit_card", "debit_card", "prepaid_card"].includes(clase);
  const hayDinero = estado === "aprobado" || estado === "reembolsado";
  if (!referencia || !(esTarjeta || hayDinero)) return vacio;

  try {
    const respuesta = await fetch(`${MP_API}/v1/payments/${referencia}`, {
      headers: { "Authorization": `Bearer ${MP_TOKEN}` },
      signal: AbortSignal.timeout(10_000),
    });
    if (!respuesta.ok) return vacio;

    // deno-lint-ignore no-explicit-any
    const datos: any = await respuesta.json();
    if (DEBUG) {
      console.log("pago clásico", JSON.stringify(datos).slice(0, 3000));
    }

    // El neto viene dentro de `transaction_details`, no suelto.
    const crudo = datos?.transaction_details?.net_received_amount ??
      buscar(datos, "net_received_amount");
    const neto = crudo == null ? null : Number(crudo);

    return {
      ultimos4: ultimos4De(datos),
      // Con una ficha de efectivo sin pagar el neto llega en cero, que no es «esta
      // venta no dejó nada» sino «todavía no ha pasado nada». Cero se descarta.
      neto: neto != null && Number.isFinite(neto) && neto > 0 ? neto : null,
      liberacion: texto(datos?.money_release_date),
      comisiones: Array.isArray(datos?.fee_details) ? datos.fee_details : null,
    };
  } catch (e) {
    console.error("no se pudo pedir el pago clásico", referencia, String(e));
    return vacio;
  }
}

/**
 * Qué tan lejos llegó el dinero. Comparte tabla con la regla de precedencia de abajo.
 *
 * Nada (0), un intento que no cuajó (1), cobrado (2), devuelto (3). Rechazado y
 * cancelado comparten rango porque para la cuenta significan lo mismo: no hay dinero.
 */
const RANGO: Record<string, number> = {
  pendiente: 0,
  rechazado: 1,
  cancelado: 1,
  aprobado: 2,
  reembolsado: 3,
};

/** Nuestro estado a partir de los suyos, probando el detalle y luego el estado a secas. */
function nuestroEstado(status: unknown, detalle: unknown): string | undefined {
  const d = texto(detalle).toLowerCase();
  const s = texto(status).toLowerCase();
  return ESTADOS[d] ?? ESTADOS[s];
}

/**
 * De todos los intentos, el que decide en qué quedó la orden.
 *
 * **No es «el último del array».** Una orden con dos intentos —uno rechazado y otro
 * aprobado— los trae a los dos, y coger el último funciona solo mientras Mercado Pago
 * los devuelva del más viejo al más nuevo, cosa que no promete. Si algún día invirtiera
 * el orden, leeríamos el intento fallido y marcaríamos como rechazado un pedido ya
 * pagado: de todos los errores posibles aquí, el más caro.
 *
 * Así que se elige por significado y no por posición: gana el que llegó más lejos con
 * el dinero, con el mismo rango que usa la regla de precedencia. Un reembolso gana a un
 * cobro, un cobro gana a un rechazo. A igualdad, el intento más reciente.
 */
function elPagoQueManda(
  pagos: Array<Record<string, unknown>>,
): Record<string, unknown> | null {
  if (!Array.isArray(pagos) || pagos.length === 0) return null;

  let mejor: Record<string, unknown> | null = null;
  let mejorRango = -1;
  let mejorIntento = -1;

  for (const pago of pagos) {
    const estado = nuestroEstado(pago.status, pago.status_detail) ?? "pendiente";
    const rango = RANGO[estado] ?? 0;
    const intento = Number(pago.attempt_number ?? 0) || 0;

    if (rango > mejorRango || (rango === mejorRango && intento >= mejorIntento)) {
      mejor = pago;
      mejorRango = rango;
      mejorIntento = intento;
    }
  }
  return mejor;
}

/**
 * Con qué se pagó. [campo] es `type` (la clase: `credit_card`, `ticket`…) o `id` (la
 * marca: `master`, `visa`, `oxxo`).
 *
 * **La Orders API los anida** en `payment_method: { id, type }`. La API vieja de pagos
 * los daba planos, en `payment_method_id` y `payment_type_id`, y buscar solo esos
 * dejaba el método vacío en todos los avisos — y con él la marca, los cuatro dígitos y
 * la línea del estado de cuenta, que cuelgan de saber que fue una tarjeta.
 *
 * Se mira primero el pago concreto y luego la orden: la orden trae una copia, pero si
 * alguna vez hubiera dos intentos con medios distintos, el bueno es el del último.
 */
// deno-lint-ignore no-explicit-any
function metodoDe(pago: any, datos: any, campo: "id" | "type"): string {
  const delPago = pago?.payment_method?.[campo];
  if (delPago) return texto(delPago);

  const deLaOrden = (buscar(datos, "payment_method") as any)?.[campo];
  if (deLaOrden) return texto(deLaOrden);

  // Los nombres planos, por si el aviso viene de la API de pagos y no de la de órdenes.
  const plano = campo === "type"
    ? buscar(datos, "payment_type_id")
    : buscar(datos, "payment_method_id");
  return texto(plano ?? "");
}

/**
 * Los últimos cuatro dígitos de la tarjeta, si pagó con una.
 *
 * Se filtra a cuatro dígitos exactos antes de devolverlos, y no por tiquismiquis: la
 * columna tiene un `check` con esa forma, así que cualquier otra cosa haría fallar el
 * update entero y perderíamos también el estado del pago. Mejor quedarse sin el dato
 * que sin el cobro.
 *
 * El número completo **no aparece por ningún lado**, ni aquí ni en lo que guardamos:
 * lo captura Mercado Pago en su propia página. Esa es la razón de usar Checkout Pro.
 */
// deno-lint-ignore no-explicit-any
function ultimos4De(datos: any): string {
  const crudo = texto(
    buscar(datos, "last_four_digits") ?? buscar(datos, "last_four") ?? "",
  );
  return /^[0-9]{4}$/.test(crudo) ? crudo : "";
}

/**
 * Consume un uso del código. Nunca lanza.
 *
 * Suma uno a `codigos.usos` en vez de marcar un booleano, porque ahora un código puede
 * valer para muchos: los de campaña se comparten y tienen tope. Se incrementa con una
 * condición sobre el propio tope —`usos < usos_max`— para que dos pagos aprobados a la
 * vez no lo pasen de largo.
 *
 * Y se marca también `wheel_spins.canjeado`, que es lo que sigue mirando la gestión de
 * la ruleta: mientras existan las dos tablas, tienen que contar lo mismo.
 *
 * Si falla, el cobro ya está hecho y el pedido en orden; lo único que queda es un uso
 * sin descontar. Tirar el aviso entero por eso sería dejar sin registrar un pago para
 * proteger un descuento.
 */
async function quemarCodigo(
  // deno-lint-ignore no-explicit-any
  supabase: any,
  codigo: string,
): Promise<void> {
  try {
    const { error } = await supabase.rpc("consumir_codigo", { p_codigo: codigo });
    if (error) console.error("no se pudo consumir el código", codigo, error.message);
  } catch (e) {
    console.error("no se pudo consumir el código", codigo, String(e));
  }
}

Deno.serve(async (req: Request): Promise<Response> => {
  // Mercado Pago no manda `OPTIONS` ni le interesa CORS: esto no lo llama un navegador.
  // Por eso tampoco hay cabeceras de CORS en las respuestas.
  if (req.method !== "POST") {
    return new Response("Método no permitido", { status: 405 });
  }
  if (!MP_TOKEN || !MP_SECRET) {
    console.error("falta MP_ACCESS_TOKEN o MP_WEBHOOK_SECRET");
    // 503 y no 200: Mercado Pago reintenta, y cuando el secreto esté puesto el aviso
    // llegará solo. Con un 200 lo daría por entregado y el pago se quedaría colgado.
    return new Response("Sin configurar", { status: 503 });
  }

  const url = new URL(req.url);
  const crudo = await req.text();
  if (DEBUG) console.log("aviso de mercado pago", crudo.slice(0, 2000));

  // deno-lint-ignore no-explicit-any
  let aviso: any = {};
  try {
    aviso = JSON.parse(crudo);
  } catch (_e) {
    aviso = {};
  }

  const dataId = texto(url.searchParams.get("data.id") ?? buscar(aviso, "id"));
  const firma = texto(req.headers.get("x-signature"));
  const requestId = texto(req.headers.get("x-request-id"));

  if (!firma || !(await firmaValida(firma, requestId, dataId))) {
    console.error("firma inválida", firma.slice(0, 120), "data.id:", dataId);
    return new Response("Firma inválida", { status: 401 });
  }

  const tipo = texto(aviso.type ?? aviso.topic ?? url.searchParams.get("type")).toLowerCase();
  if (!dataId) {
    // Nada que consultar. Se contesta 200 para que no lo reintente eternamente: el
    // aviso llegó bien, es que no hablaba de ningún recurso.
    console.error("aviso sin recurso", tipo);
    return new Response("Sin recurso", { status: 200 });
  }

  // Un tema que no es nuestro se da por recibido y se deja estar. 200 y no 500: el
  // aviso llegó bien, simplemente no habla de nada que tengamos que hacer, y
  // contestar error haría que lo reintentara para siempre.
  if (temaDelAviso(tipo) === "otro") {
    if (DEBUG) console.log("aviso de otro tema, ignorado:", tipo, dataId);
    return new Response("Otro tema", { status: 200 });
  }

  const estado = await consultar(tipo, dataId);
  if (!estado) {
    // No se pudo confirmar. 500 para que Mercado Pago reintente: es mejor que lo
    // vuelva a mandar que dejar el pago mal puesto.
    return new Response("No se pudo confirmar", { status: 500 });
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  );

  const { data: solicitud } = await supabase
    .from("order_requests")
    .select("id, folio, pago_estado, pago_total, pago_neto, pago_liberacion, codigo_descuento")
    .eq("id", estado.solicitudId)
    .maybeSingle();

  if (!solicitud) {
    // 200: el aviso es válido pero habla de algo que no es nuestro —o de una solicitud
    // borrada—. Reintentarlo no cambiaría nada.
    console.error("aviso de una solicitud que no existe", estado.solicitudId);
    return new Response("Desconocida", { status: 200 });
  }

  // Los avisos llegan desordenados y repetidos, así que un estado solo avanza.
  //
  // Solo se bloquea el retroceso **desde cobrado o devuelto**, que son los dos estados
  // donde el dinero se movió de verdad. Desde ahí, un aviso viejo que llegue tarde
  // diciendo «pendiente» o «rechazado» se descarta: sin esto, un reembolso ya hecho
  // volvería a leerse como pagado. Hacia arriba todo pasa, y un `rechazado → pendiente`
  // también, que es el segundo intento de quien cambió de tarjeta.
  //
  // El rango es el mismo que usa `elPagoQueManda` para elegir entre varios intentos:
  // una sola definición, arriba, porque son la misma idea.
  const antes = RANGO[texto(solicitud.pago_estado)] ?? 0;
  const ahora = RANGO[estado.estado] ?? 0;
  const retrocede = antes >= 2 && ahora < antes;

  if (!retrocede) {
    const { error } = await supabase
      .from("order_requests")
      .update({
        pago_estado: estado.estado,
        // El monto que se guarda es **el que Mercado Pago dice que se pagó**, no el que
        // calculamos. Si no coinciden, es lo primero que hay que ver.
        pago_total: estado.monto ?? solicitud.pago_total,
        pago_referencia: estado.pagoId || null,
        pago_metodo: estado.metodo || null,
        pago_marca: estado.marca || null,
        pago_ultimos4: estado.ultimos4 || null,
        // Lo que de verdad entra. Si esta vez no se pudo averiguar se deja lo que
        // hubiera: un aviso posterior que no traiga el neto no debe borrar el que ya
        // teníamos. `pago_comision` la calcula la base.
        pago_neto: estado.neto ?? solicitud.pago_neto,
        pago_liberacion: estado.liberacion || solicitud.pago_liberacion,
        pago_actualizado_en: new Date().toISOString(),
      } as never)
      .eq("id", solicitud.id);
    if (error) console.error("no se pudo guardar el estado del pago", error.message);
  } else {
    console.error(
      "aviso descartado por retroceder",
      solicitud.pago_estado,
      "→",
      estado.estado,
    );
  }

  // Con el pago cobrado, el código de la ruleta se quema. **Aquí y no antes**: al
  // abrir la orden habría quemado el premio de quien cierra la pestaña sin pagar, y
  // al validarlo, el de quien solo quiso ver cuánto le quedaba.
  if (!retrocede && estado.estado === "aprobado" && solicitud.codigo_descuento) {
    await quemarCodigo(supabase, String(solicitud.codigo_descuento));
  }

  // El historial se escribe siempre, incluso para el aviso descartado: que llegó, es un
  // hecho, y si algún día no cuadra un cobro lo que hará falta es verlos todos.
  //
  // El índice único de 39_pagos.sql se come los repetidos, así que un error de clave
  // duplicada aquí es lo esperado y no se grita.
  const { error: errorHistorial } = await supabase.from("pagos").insert({
    request_id: solicitud.id,
    accion: estado.estado,
    orden_id: estado.ordenId || null,
    pago_id: estado.pagoId || null,
    monto: estado.monto,
    // En el historial va clase y marca juntas: es para leerlo, no para decidir nada.
    metodo: [estado.metodo, estado.marca].filter(Boolean).join(" · ") || null,
    neto: estado.neto,
    // Sin interpretar: hoy no sabemos qué significa cada `type` y guardarlo entero
    // permite entenderlo el día que haga falta.
    comisiones: estado.comisiones,
    estado_crudo: estado.estadoCrudo || null,
    nota: retrocede ? `Descartado: llegó después de ${solicitud.pago_estado}` : null,
  } as never);
  if (errorHistorial && !errorHistorial.message.includes("duplicate")) {
    console.error("no se pudo anotar el aviso", errorHistorial.message);
  }

  return new Response("ok", { status: 200 });
});
