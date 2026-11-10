// ============================================================
// Edge Function · reembolsar
//
// Devuelve el dinero de una solicitud en Mercado Pago.
//
// **La llama la app de control, no la web.** El cliente pide cancelar
// —eso es `fn_cancelar_solicitud` (51)— y quien decide devolver es la
// tienda, mirando el caso. Aquí no hay nada automático: esto es el botón
// que aprieta una persona.
//
// La defensa que de verdad importa está en el guardián del paquete: **no
// se devuelve si hay guía**. Una guía generada es un paquete que puede
// estar ya en manos de la paquetería, y devolver el dinero de algo que va
// en camino es regalarlo. Si la guía se generó por error, primero se
// cancela con `cancelar-guia` —que la borra de la fila— y entonces esta
// función deja pasar. Ese orden no es burocracia: es lo que hace que la
// comprobación signifique algo.
//
// **No consulta el estado de la devolución después.** Mercado Pago puede
// tardar en liberar el dinero, y quien cuenta eso es el webhook, que ya
// traduce `refunded` y `charged_back` a `reembolsado`. Aquí se registra
// lo que contestó al pedirlo.
//
// Secrets: MP_ACCESS_TOKEN, el mismo de `crear-pago`.
// SUPABASE_URL, SUPABASE_ANON_KEY y SUPABASE_SERVICE_ROLE_KEY los inyecta
// la plataforma.
// ============================================================

import { createClient } from "jsr:@supabase/supabase-js@2";

const MP_API = "https://api.mercadopago.com";
const MP_TOKEN = (Deno.env.get("MP_ACCESS_TOKEN") ?? "").trim();
const DEBUG = Deno.env.get("MP_DEBUG") === "1";

interface FilaPago {
  id: string;
  folio: number | null;
  pago_estado: string | null;
  pago_total: number | null;
  pago_referencia: string | null;
  pago_orden_id: string | null;
  guia_numero: string | null;
  codigo_descuento: string | null;
  cancelacion_solicitada_at: string | null;
  cancelada_at: string | null;
  reembolso_at: string | null;
}

function corsHeaders(): Record<string, string> {
  return {
    // La app de control no es un navegador: no manda Origin y no hay lista de
    // orígenes que valga. Lo que protege esta función es la sesión de
    // administración, no el CORS.
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

/** Centavos arriba o abajo no, pero tampoco un float con cola. */
function dinero(n: number): number {
  return Math.round(n * 100) / 100;
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders() });
  }
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);
  if (!MP_TOKEN) {
    return json({ error: "Falta configurar MP_ACCESS_TOKEN." }, 503);
  }

  // ---- quién llama ----
  const token = (req.headers.get("authorization") ?? "")
    .replace(/^Bearer\s+/i, "")
    .trim();
  if (!token) return json({ error: "Falta la sesión." }, 401);

  const comoQuienLlama = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_ANON_KEY") ?? "",
    { global: { headers: { Authorization: `Bearer ${token}` } } },
  );
  const { data: sesion } = await comoQuienLlama.auth.getUser();
  const usuario = sesion?.user;
  if (!usuario) return json({ error: "Sesión inválida." }, 401);

  // El rol lo dice la base, no el cliente.
  const { data: esAdmin, error: errorRol } = await comoQuienLlama.rpc("is_admin");
  if (errorRol || esAdmin !== true) {
    return json({ error: "Solo administración puede reembolsar." }, 403);
  }

  // ---- qué se devuelve ----
  let cuerpo: {
    solicitudId?: unknown;
    monto?: unknown;
    nota?: unknown;
    devolverCodigo?: unknown;
  };
  try {
    cuerpo = await req.json();
  } catch (_e) {
    return json({ error: "Petición ilegible" }, 400);
  }

  const solicitudId = String(cuerpo.solicitudId ?? "").trim();
  if (!solicitudId) return json({ error: "Falta la solicitud." }, 400);

  const nota = String(cuerpo.nota ?? "").trim().slice(0, 300);
  // Por omisión se devuelve el código. A quien se le devuelve el dinero se le
  // devuelve el premio: si no, esa persona se queda sin compra y sin código. Se
  // puede desactivar desde la app para el caso en que se devuelva por abuso.
  const devolverCodigo = cuerpo.devolverCodigo !== false;

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  );

  const { data: fila } = await supabase
    .from("order_requests")
    .select(
      "id, folio, pago_estado, pago_total, pago_referencia, pago_orden_id, " +
        "guia_numero, codigo_descuento, cancelacion_solicitada_at, cancelada_at, " +
        "reembolso_at",
    )
    .eq("id", solicitudId)
    .maybeSingle();
  const solicitud = fila as FilaPago | null;

  if (!solicitud) return json({ error: "No encontramos esa solicitud." }, 404);

  // ---- los guardianes ----
  //
  // En este orden a propósito: primero lo que ya pasó —no hay nada que devolver, ya
  // se devolvió— y al final el del paquete, que es el que más explicación necesita.

  if (solicitud.pago_estado !== "aprobado") {
    return json({
      error: "Esa solicitud no tiene un pago aprobado que devolver.",
      pagoEstado: solicitud.pago_estado ?? "sin pago",
    }, 409);
  }

  if (solicitud.reembolso_at) {
    return json({
      error: "Esa solicitud ya se reembolsó.",
      reembolsoAt: solicitud.reembolso_at,
    }, 409);
  }

  // **El guardián del paquete.** Ver el encabezado.
  if (solicitud.guia_numero) {
    return json({
      error: "Esa solicitud ya tiene guía: cancélala primero con cancelar-guia. " +
        "Mientras exista, el paquete puede estar en camino.",
      guiaNumero: solicitud.guia_numero,
    }, 409);
  }

  const pagoId = (solicitud.pago_referencia ?? "").trim();
  if (!pagoId) {
    // Sin el id del pago no hay a qué pedirle la devolución. Pasa si el webhook no
    // llegó a escribirlo; se arregla volviendo a enviar el aviso desde el panel.
    return json({
      error: "No tenemos la referencia del pago en Mercado Pago. Reenvía el aviso " +
        "del webhook desde el panel y vuelve a intentarlo.",
      ordenId: solicitud.pago_orden_id,
    }, 409);
  }

  const cobrado = dinero(Number(solicitud.pago_total ?? 0));
  const pedido = cuerpo.monto === undefined || cuerpo.monto === null
    ? null
    : dinero(Number(cuerpo.monto));

  if (pedido !== null && !(pedido > 0 && pedido <= cobrado)) {
    return json({
      error: `El importe a devolver tiene que estar entre 0 y ${cobrado}.`,
    }, 400);
  }

  // ---- la devolución ----
  //
  // Sin cuerpo se devuelve todo; con `amount`, esa parte. Mercado Pago acepta varias
  // parciales sobre el mismo pago hasta sumar el total.
  //
  // `X-Idempotency-Key` no es una mejora, es lo que impide devolver dos veces: si la
  // respuesta se pierde por el camino y la app reintenta, Mercado Pago reconoce la
  // llave y contesta con el mismo reembolso en vez de hacer otro. Lleva el importe
  // dentro porque una parcial de $100 y otra de $200 sobre el mismo pago **sí** son
  // dos movimientos distintos.
  const llave = `reembolso:${solicitud.id}:${pedido ?? "total"}`;

  let respuesta: Response;
  try {
    respuesta = await fetch(`${MP_API}/v1/payments/${pagoId}/refunds`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${MP_TOKEN}`,
        "Content-Type": "application/json",
        "X-Idempotency-Key": llave,
      },
      body: pedido === null ? "{}" : JSON.stringify({ amount: pedido }),
    });
  } catch (e) {
    console.error("no se pudo llamar a Mercado Pago", String(e));
    return json({ error: "No pudimos contactar con Mercado Pago." }, 502);
  }

  const crudo = await respuesta.text();
  if (DEBUG) console.log("respuesta de reembolso", respuesta.status, crudo.slice(0, 1500));

  // deno-lint-ignore no-explicit-any
  let datos: any = {};
  try {
    datos = crudo ? JSON.parse(crudo) : {};
  } catch (_e) {
    datos = {};
  }

  if (!respuesta.ok) {
    console.error("Mercado Pago rechazó el reembolso", respuesta.status, crudo.slice(0, 500));
    return json({
      error: String(datos?.message ?? "Mercado Pago no aceptó la devolución."),
      estado: respuesta.status,
      ...(DEBUG ? { crudo: crudo.slice(0, 1500) } : {}),
    }, 502);
  }

  const reembolsoId = String(datos?.id ?? "").trim();
  const devuelto = dinero(Number(datos?.amount ?? pedido ?? cobrado));
  // Parcial mientras no se devuelva todo: la solicitud no queda cancelada por una
  // devolución a medias, y el cliente que recibe $200 de $800 no está cancelado.
  const total = devuelto >= cobrado;

  const ahora = new Date().toISOString();
  const { error: errorGuardar } = await supabase
    .from("order_requests")
    .update({
      reembolso_id: reembolsoId || null,
      reembolso_monto: devuelto,
      reembolso_at: ahora,
      // El estado del pago lo confirmará el webhook con lo que diga Mercado Pago;
      // se escribe ya para que la app de control no tenga que esperarlo.
      ...(total ? { pago_estado: "reembolsado" } : {}),
      // Cancelada **ahora**, que es cuando el dinero sale. Si la pidió el cliente,
      // suya es; si no, la canceló la tienda.
      ...(total
        ? {
          cancelada_at: ahora,
          cancelada_por: solicitud.cancelacion_solicitada_at ? "cliente" : "tienda",
        }
        : {}),
    } as never)
    .eq("id", solicitudId);

  if (errorGuardar) {
    // El dinero ya salió: esto **no** se devuelve como error, o la app reintentaría
    // una devolución que ya ocurrió. Se grita en los logs y se contesta lo que pasó.
    console.error("reembolso hecho pero no se pudo guardar", errorGuardar.message);
  }

  // El código vuelve a estar disponible. Solo con la devolución completa: en una
  // parcial la compra sigue en pie y el premio se usó.
  let codigoDevuelto: string | null = null;
  if (devolverCodigo && total && solicitud.codigo_descuento) {
    const { error: errorCodigo } = await supabase.rpc("liberar_codigo", {
      p_codigo: solicitud.codigo_descuento,
    });
    if (errorCodigo) {
      console.error("no se pudo liberar el código", errorCodigo.message);
    } else {
      codigoDevuelto = solicitud.codigo_descuento;
      // Se suelta de la solicitud, si no el índice único de `codigo_descuento` no
      // deja usarlo en otro carrito. Queda escrito en el historial de pagos.
      await supabase
        .from("order_requests")
        .update({ codigo_descuento: null } as never)
        .eq("id", solicitudId);
    }
  }

  // El historial. El índice único de 39_pagos se come el repetido si el webhook
  // escribe el suyo con el mismo pago y estado.
  await supabase.from("pagos").insert({
    request_id: solicitud.id,
    accion: "reembolsado",
    orden_id: solicitud.pago_orden_id,
    pago_id: pagoId,
    monto: devuelto,
    estado_crudo: String(datos?.status ?? ""),
    usuario_id: usuario.id,
    usuario: usuario.email ?? null,
    nota: [
      total ? "Devolución total" : "Devolución parcial",
      nota,
      codigoDevuelto ? `Código ${codigoDevuelto} devuelto` : "",
    ].filter((t) => t.length > 0).join(" · "),
  } as never);

  return json({
    ok: true,
    folio: solicitud.folio,
    reembolsoId,
    monto: devuelto,
    total,
    estado: String(datos?.status ?? ""),
    codigoDevuelto,
    guardado: !errorGuardar,
  }, 200);
});
