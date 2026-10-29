// ============================================================
// Edge Function · solicitar-recoleccion
//
// Pide a la paquetería que pase a recoger los paquetes por la tienda,
// en vez de llevarlos al centro de procesamiento.
//
// Envia agrupa varias guías en una sola recolección **si son de la
// misma paquetería y salen de la misma dirección**, que es exactamente
// el caso de la tienda: se generan guías durante el día y al final se
// pide una recolección para todas.
//
// Mismas defensas que `generar-guia`, y por el mismo motivo: pedir una
// recolección mueve un camión. Solo administración, y una solicitud que
// ya está en una recolección no entra en otra.
//
// **No todas las paqueterías recogen.** Unas lo agendan solas al crear
// la guía, otras lo aceptan aparte y otras solo reciben en sucursal. Si
// la que corresponde no recoge, Envia lo dice y aquí se devuelve tal
// cual: es un dato del negocio, no un fallo que esconder.
//
// Secrets: los mismos que `generar-guia`.
// ============================================================

import { createClient } from "jsr:@supabase/supabase-js@2";

const ENVIA_TOKEN = (Deno.env.get("ENVIA_TOKEN") ?? "")
  .trim()
  .replace(/^Bearer\s+/i, "")
  .trim();
const ENVIA_BASE = (Deno.env.get("ENVIA_BASE_URL") ?? "https://api-test.envia.com")
  .replace(/\/+$/, "");
const DEBUG = Deno.env.get("ENVIA_DEBUG") === "1";

/** Franja por defecto, si no se pide otra. */
const HORA_INICIO = Deno.env.get("ENVIA_RECOLECCION_DESDE") ?? "10:00";
const HORA_FIN = Deno.env.get("ENVIA_RECOLECCION_HASTA") ?? "18:00";

/** Tope de guías por recolección, para no armar una petición absurda. */
const MAX_GUIAS = 50;

const FECHA_VALIDA = /^\d{4}-\d{2}-\d{2}$/;
const HORA_VALIDA = /^([01]\d|2[0-3]):[0-5]\d$/;

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

interface FilaGuia {
  id: string;
  folio: number | null;
  guia_numero: string | null;
  guia_paqueteria: string | null;
  recoleccion_id: string | null;
}

function corsHeaders(): Record<string, string> {
  return {
    // La app de control no es un navegador. Lo que protege esto es la sesión de
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
    return json({ error: "Solo administración puede pedir recolecciones." }, 403);
  }

  // ---- lo que se pide ----
  let cuerpo: {
    solicitudes?: unknown;
    fecha?: unknown;
    desde?: unknown;
    hasta?: unknown;
  };
  try {
    cuerpo = await req.json();
  } catch (_e) {
    return json({ error: "Petición ilegible" }, 400);
  }

  const ids = (Array.isArray(cuerpo.solicitudes) ? cuerpo.solicitudes : [])
    .map((v) => String(v ?? "").trim())
    .filter((v) => v.length > 0);
  if (ids.length === 0 || ids.length > MAX_GUIAS) {
    return json({ error: "Indica entre 1 y 50 solicitudes." }, 400);
  }

  const fecha = String(cuerpo.fecha ?? "").trim();
  if (!FECHA_VALIDA.test(fecha)) {
    return json({ error: "La fecha de recolección debe venir como AAAA-MM-DD." }, 400);
  }
  const desde = String(cuerpo.desde ?? HORA_INICIO).trim();
  const hasta = String(cuerpo.hasta ?? HORA_FIN).trim();
  if (!HORA_VALIDA.test(desde) || !HORA_VALIDA.test(hasta) || desde >= hasta) {
    return json({ error: "La franja horaria no es válida." }, 400);
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  );

  // ---- las guías ----
  const { data: filas } = await supabase
    .from("order_requests")
    .select("id, folio, guia_numero, guia_paqueteria, recoleccion_id")
    .in("id", ids);
  const solicitudes = (filas ?? []) as FilaGuia[];

  const sinGuia = solicitudes.filter((s) => !s.guia_numero);
  if (solicitudes.length !== ids.length || sinGuia.length > 0) {
    return json({
      error: "Hay solicitudes sin guía generada. Genera primero las guías.",
      folios: sinGuia.map((s) => s.folio),
    }, 409);
  }

  // Ya recogidas: se avisa en vez de pedir el camión dos veces.
  const yaPedidas = solicitudes.filter((s) => s.recoleccion_id);
  if (yaPedidas.length > 0) {
    return json({
      error: "Algunas ya están en una recolección pedida.",
      folios: yaPedidas.map((s) => s.folio),
    }, 409);
  }

  // Envia exige una sola paquetería por recolección: mezclarlas devolvería un error
  // suyo mucho menos claro que este.
  const paqueterias = [...new Set(solicitudes.map((s) => s.guia_paqueteria ?? ""))];
  if (paqueterias.length !== 1 || !paqueterias[0]) {
    return json({
      error: "Una recolección es de una sola paquetería. Agrúpalas y pide una por cada una.",
      paqueterias,
    }, 409);
  }
  const carrier = paqueterias[0];

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

  const peticion = {
    carrier,
    pickupDate: fecha,
    pickupTimeStart: desde,
    pickupTimeEnd: hasta,
    pickupAddress: {
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
    trackingNumbers: solicitudes.map((s) => s.guia_numero),
  };

  let respuesta: Response;
  try {
    respuesta = await fetch(`${ENVIA_BASE}/ship/pickup/`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${ENVIA_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(peticion),
      signal: AbortSignal.timeout(25000),
    });
  } catch (e) {
    console.error("envia /ship/pickup sin respuesta", String(e));
    return json({
      error: "La paquetería no respondió. Revisa en su panel si la recolección quedó " +
        "agendada antes de volver a pedirla.",
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
    console.error("envia /ship/pickup", respuesta.status, texto.slice(0, 2000));
    return json({
      error: "No se pudo agendar la recolección.",
      // El motivo se enseña tal cual: «esta paquetería no recoge en tu zona» es una
      // respuesta útil, no un fallo. Con ella se decide si llevarlo a sucursal.
      motivo: String(detalle?.message ?? detalle?.description ?? respuesta.status),
      ...(DEBUG ? { cuerpo: texto.slice(0, 2000), peticion } : {}),
    }, 502);
  }

  const datos = (crudo?.data ?? {}) as Record<string, unknown>;
  const confirmacion = String(datos.confirmation ?? datos.id ?? "").trim();
  if (!confirmacion) {
    console.error("envia /ship/pickup sin confirmacion", texto.slice(0, 2000));
    return json({ error: "La paquetería no devolvió número de confirmación." }, 502);
  }

  const { error: errorGuardar } = await supabase
    .from("order_requests")
    .update({ recoleccion_id: confirmacion, recoleccion_fecha: fecha })
    .in("id", ids);

  const recoleccion = {
    confirmacion,
    paqueteria: carrier,
    fecha,
    desde: String(datos.timeFrom ?? desde),
    hasta: String(datos.timeTo ?? hasta),
    estado: String(datos.status ?? ""),
    guias: solicitudes.length,
  };

  if (errorGuardar) {
    console.error("no se pudo guardar la recoleccion", errorGuardar.message);
    return json({
      recoleccion,
      aviso: "La recolección quedó agendada pero no se pudo anotar en las solicitudes. " +
        "Guarda el número de confirmación.",
    }, 200);
  }

  return json({ recoleccion }, 200);
});
