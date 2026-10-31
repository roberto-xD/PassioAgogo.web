// ============================================================
// Edge Function · cancelar-guia
//
// Cancela una guía ya generada y pide a la paquetería que devuelva lo
// cobrado.
//
// Hace falta porque las guías se pagan al generarse, y generar es
// justo lo que se hace sin poder comprobar antes: una paquetería que
// cotiza puede rechazar la guía, el cliente puede cancelar el pedido, o
// se puede imprimir con la empresa equivocada. Sin esta función, cada
// equivocación es dinero que se queda ahí.
//
// **No siempre se devuelve el dinero.** Una guía que la paquetería ya
// escaneó no se cancela, y cada una tiene su propio plazo. Por eso la
// respuesta dice si hubo devolución en vez de darla por hecha: lo que
// aquí se registra es lo que pasó, no lo que se pidió.
//
// Mismas defensas que `generar-guia`: solo administración, porque esto
// mueve dinero y deja un pedido sin forma de enviarse.
//
// Secrets: los mismos que las otras funciones de envío.
// ============================================================

import { createClient } from "jsr:@supabase/supabase-js@2";

const ENVIA_TOKEN = (Deno.env.get("ENVIA_TOKEN") ?? "")
  .trim()
  .replace(/^Bearer\s+/i, "")
  .trim();
const ENVIA_BASE = (Deno.env.get("ENVIA_BASE_URL") ?? "https://api-test.envia.com")
  .replace(/\/+$/, "");
const DEBUG = Deno.env.get("ENVIA_DEBUG") === "1";

interface FilaGuia {
  id: string;
  folio: number | null;
  guia_numero: string | null;
  guia_paqueteria: string | null;
  guia_costo: number | null;
  recoleccion_id: string | null;
}

function corsHeaders(): Record<string, string> {
  return {
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

/** Qué hacer con lo que contestó la paquetería al rechazar la cancelación. */
function pistaDeEnvia(mensaje: string, descripcion: string): string | undefined {
  const m = `${mensaje} ${descripcion}`.toUpperCase();
  if (m.includes("SCAN") || m.includes("TRANSIT") || m.includes("PICKED")) {
    return "La paquetería ya recogió o escaneó el paquete: a partir de ahí la guía no " +
      "se cancela. Si el pedido no va a salir, habrá que resolverlo con ellos.";
  }
  if (m.includes("TIME") || m.includes("EXPIRED") || m.includes("WINDOW")) {
    return "Se pasó el plazo de cancelación de esa paquetería. El plazo lo pone cada " +
      "una y empieza al generar la guía.";
  }
  if (m.includes("NOT FOUND") || m.includes("INVALID")) {
    return "La paquetería no reconoce ese número de guía. Comprueba que sea el suyo y " +
      "no el de otra.";
  }
  return undefined;
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders() });
  }
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);
  if (!ENVIA_TOKEN) return json({ error: "Falta configurar el token de Envia." }, 503);

  // ---- quién llama ----
  const token = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "").trim();
  if (!token) return json({ error: "Falta la sesión." }, 401);

  const comoQuienLlama = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_ANON_KEY") ?? "",
    { global: { headers: { Authorization: `Bearer ${token}` } } },
  );
  const { data: sesion } = await comoQuienLlama.auth.getUser();
  if (!sesion?.user) return json({ error: "Sesión inválida." }, 401);

  const { data: esAdmin, error: errorRol } = await comoQuienLlama.rpc("is_admin");
  if (errorRol || esAdmin !== true) {
    return json({ error: "Solo administración puede cancelar guías." }, 403);
  }

  // ---- qué se cancela ----
  let cuerpo: { solicitudId?: unknown };
  try {
    cuerpo = await req.json();
  } catch (_e) {
    return json({ error: "Petición ilegible" }, 400);
  }

  const solicitudId = String(cuerpo.solicitudId ?? "").trim();
  if (!solicitudId) return json({ error: "Falta la solicitud." }, 400);

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  );

  const { data: fila } = await supabase
    .from("order_requests")
    .select("id, folio, guia_numero, guia_paqueteria, guia_costo, recoleccion_id")
    .eq("id", solicitudId)
    .maybeSingle();
  const solicitud = fila as FilaGuia | null;

  if (!solicitud) return json({ error: "No encontramos esa solicitud." }, 404);
  if (!solicitud.guia_numero || !solicitud.guia_paqueteria) {
    return json({ error: "Esa solicitud no tiene guía que cancelar." }, 409);
  }
  // Una recolección pedida significa que la paquetería ya tiene instrucciones de pasar.
  // Cancelar la guía sin avisarles deja un camión yendo a por un paquete que no sale.
  if (solicitud.recoleccion_id) {
    return json({
      error: "Esa guía ya está en una recolección pedida.",
      pista: "Cancela primero la recolección con la paquetería; si no, pasarán a " +
        "recoger un paquete que ya no va a salir.",
      recoleccion: solicitud.recoleccion_id,
    }, 409);
  }

  // ---- la cancelación ----
  // Se cancela por número de rastreo, no por el identificador de Envia.
  const peticion = {
    carrier: solicitud.guia_paqueteria,
    trackingNumber: solicitud.guia_numero,
    folio: String(solicitud.folio ?? ""),
  };

  let respuesta: Response;
  try {
    respuesta = await fetch(`${ENVIA_BASE}/ship/cancel/`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${ENVIA_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(peticion),
      signal: AbortSignal.timeout(25000),
    });
  } catch (e) {
    console.error("envia /ship/cancel sin respuesta", String(e));
    return json({
      error: "La paquetería no respondió. Comprueba en su panel si la guía quedó " +
        "cancelada antes de volver a intentarlo.",
    }, 504);
  }

  const texto = await respuesta.text().catch(() => "");
  let crudo: Record<string, unknown> | null = null;
  try {
    crudo = texto ? JSON.parse(texto) : null;
  } catch (_e) {
    crudo = null;
  }

  const detalle = crudo?.error as Record<string, unknown> | undefined;
  if (!respuesta.ok || crudo?.meta === "error") {
    const mensaje = String(detalle?.message ?? "");
    const descripcion = String(detalle?.description ?? "");
    console.error("envia /ship/cancel", respuesta.status, texto.slice(0, 2000));
    return json({
      error: "No se pudo cancelar la guía.",
      motivo: descripcion || mensaje || String(respuesta.status),
      codigo: mensaje || undefined,
      pista: pistaDeEnvia(mensaje, descripcion),
      ...(DEBUG ? { cuerpo: texto.slice(0, 2000), peticion } : {}),
    }, 502);
  }

  const datos = (crudo?.data ?? {}) as Record<string, unknown>;
  // `balanceReturned` es lo único que dice si hubo devolución. Cancelar y que te
  // devuelvan el dinero no son lo mismo, y confundirlos sería dar por recuperado algo
  // que no lo está.
  const devuelto = datos.balanceReturned === true;

  // ---- se limpia la solicitud ----
  // Las columnas de la guía vuelven a vacío: el pedido queda como estaba antes de
  // generarla, y puede volver a generarse. Lo que **no** se borra es `envio_*`, que es
  // lo que el cliente eligió y sigue siendo cierto.
  const { error: errorGuardar } = await supabase
    .from("order_requests")
    .update({
      guia_numero: null,
      guia_url: null,
      guia_rastreo_url: null,
      guia_paqueteria: null,
      guia_servicio: null,
      guia_costo: null,
      guia_envia_id: null,
      guia_generada_en: null,
      necesita_recoleccion: null,
    })
    .eq("id", solicitudId);

  const resultado = {
    numero: solicitud.guia_numero,
    paqueteria: solicitud.guia_paqueteria,
    costo: solicitud.guia_costo,
    devuelto,
    fechaDevolucion: String(datos.balanceReturnDate ?? ""),
  };

  if (errorGuardar) {
    console.error("no se pudo limpiar la guia", solicitudId, errorGuardar.message);
    return json({
      cancelacion: resultado,
      aviso: "La guía se canceló con la paquetería pero la solicitud sigue " +
        "apuntándola. Quítala a mano antes de generar otra.",
    }, 200);
  }

  return json({
    cancelacion: resultado,
    ...(devuelto ? {} : {
      aviso: "La paquetería aceptó la cancelación pero todavía no confirma la " +
        "devolución del importe. Revísalo en su panel.",
    }),
  }, 200);
});
