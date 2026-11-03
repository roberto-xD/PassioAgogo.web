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

  const manifiesto = [
    dataId ? `id:${dataId};` : "",
    requestId ? `request-id:${requestId};` : "",
    `ts:${ts};`,
  ].join("");

  const llave = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(MP_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const firma = await crypto.subtle.sign(
    "HMAC",
    llave,
    new TextEncoder().encode(manifiesto),
  );
  const esperado = Array.from(new Uint8Array(firma))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");

  // Comparación en tiempo constante: si saliera antes al primer carácter distinto, el
  // tiempo de respuesta diría cuántos caracteres se acertaron y la firma se podría
  // adivinar a trozos.
  if (esperado.length !== v1.length) return false;
  let diferencia = 0;
  for (let i = 0; i < esperado.length; i++) {
    diferencia |= esperado.charCodeAt(i) ^ v1.charCodeAt(i);
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

/** Lo que le preguntamos a Mercado Pago, ya exprimido. */
interface Estado {
  solicitudId: string;
  estado: string;
  estadoCrudo: string;
  monto: number | null;
  metodo: string;
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
  const esPago = tipo.includes("payment");
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
  const ultimo = Array.isArray(pagos) && pagos.length > 0
    ? pagos[pagos.length - 1]
    : null;

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

  const montoCrudo = (ultimo && ultimo.amount) ??
    buscar(datos, "total_paid_amount") ??
    buscar(datos, "transaction_amount") ??
    buscar(datos, "total_amount");
  const monto = montoCrudo == null ? null : Number(montoCrudo);

  return {
    solicitudId,
    estado,
    estadoCrudo: crudo || estadoBase,
    monto: monto != null && Number.isFinite(monto) ? monto : null,
    metodo: texto(buscar(datos, "payment_method_id") ?? buscar(datos, "payment_type_id")),
    pagoId: texto((ultimo && ultimo.id) ?? (esPago ? id : "")),
    ordenId: esPago ? texto(buscar(datos, "order_id")) : id,
  };
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
    .select("id, folio, pago_estado, pago_total")
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
  // El rango dice qué tan lejos ha llegado el dinero: nada (0), un intento que no
  // cuajó (1), cobrado (2), devuelto (3). Rechazado y cancelado comparten rango porque
  // los dos significan lo mismo para la cuenta: no hay dinero.
  //
  // Solo se bloquea el retroceso **desde cobrado o devuelto**, que son los dos estados
  // donde el dinero se movió de verdad. Desde ahí, un aviso viejo que llegue tarde
  // diciendo «pendiente» o «rechazado» se descarta: sin esto, un reembolso ya hecho
  // volvería a leerse como pagado. Hacia arriba todo pasa, y un `rechazado → pendiente`
  // también, que es el segundo intento de quien cambió de tarjeta.
  const RANGO: Record<string, number> = {
    pendiente: 0,
    rechazado: 1,
    cancelado: 1,
    aprobado: 2,
    reembolsado: 3,
  };
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
    metodo: estado.metodo || null,
    estado_crudo: estado.estadoCrudo || null,
    nota: retrocede ? `Descartado: llegó después de ${solicitud.pago_estado}` : null,
  } as never);
  if (errorHistorial && !errorHistorial.message.includes("duplicate")) {
    console.error("no se pudo anotar el aviso", errorHistorial.message);
  }

  return new Response("ok", { status: 200 });
});
