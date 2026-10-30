// ============================================================
// Edge Function · envia-diagnostico
//
// Pregunta a Envia qué paqueterías y servicios hay disponibles **en el
// entorno que estamos usando**, en vez de deducirlo a base de intentos.
//
// Nació de dos cosas que nos costaron varias rondas averiguar:
//
//   1. La lista de paqueterías nunca llegó a consultarse: la API de
//      consultas tiene host propio por entorno y apuntábamos al de
//      producción con token de sandbox. Contestaba 401, caíamos a una
//      lista escrita a ojo, y como esa lista acertaba, no se notó.
//   2. Estafeta cotiza pero no genera guías en sandbox
//      (`SERVICE_QUOTE_ONLY`). Eso solo se supo al intentarlo, y cada
//      intento en producción cuesta dinero.
//
// Solo lee. No genera nada, no cobra nada y no toca la base. Aun así
// pide sesión de administración: dice qué tiene contratado la tienda, y
// eso no es asunto de quien visita la web.
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

/** Mismo entorno que la API de envíos; los tokens no son intercambiables. */
const ENVIA_QUERIES = (Deno.env.get("ENVIA_QUERIES_URL") ??
  (ENVIA_BASE.includes("api-test")
    ? "https://queries.test.envia.com"
    : "https://queries.envia.com"))
  .replace(/\/+$/, "");

function corsHeaders(): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers":
      "authorization, apikey, content-type, x-client-info",
    "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
    "Access-Control-Max-Age": "86400",
    "Content-Type": "application/json",
  };
}

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders() });
}

/**
 * Una consulta a Envia, con lo que pasó en vez de un valor vacío.
 *
 * Importa distinguir «contestó que no hay nada» de «no contestó»: lo primero es un dato
 * del negocio y lo segundo un fallo de configuración, y confundirlos fue justo lo que
 * nos tuvo cotizando con una lista inventada.
 */
async function consultar(ruta: string) {
  const url = `${ENVIA_QUERIES}${ruta}`;
  try {
    const r = await fetch(url, {
      headers: { "Authorization": `Bearer ${ENVIA_TOKEN}` },
      signal: AbortSignal.timeout(10000),
    });
    const texto = await r.text();
    let cuerpo: unknown = null;
    try {
      cuerpo = texto ? JSON.parse(texto) : null;
    } catch (_e) {
      cuerpo = texto.slice(0, 300);
    }
    return { url, estado: r.status, ok: r.ok, cuerpo };
  } catch (e) {
    return { url, estado: 0, ok: false, cuerpo: String(e) };
  }
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders() });
  }
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
    return json({ error: "Solo administración." }, 403);
  }

  const params = new URL(req.url).searchParams;
  const pais = params.get("pais")?.toUpperCase() || "MX";
  // `/service?country_code=XX` es la buena: `/available-service/{pais}` da 404 y las
  // rutas de «carrier actions» tampoco existen en este host.
  const rutas = [
    "/carrier",
    `/service?country_code=${pais}`,
  ];

  const respuestas = await Promise.all(rutas.map((r) => consultar(r)));
  const paqueterias = respuestas[0];
  const servicios = respuestas[1];

  return json({
    entorno: {
      // Lo primero que hay que mirar cuando algo no cuadra: si el token y estos hosts
      // no son del mismo entorno, todo lo demás contesta 401 y parece otra cosa.
      envios: ENVIA_BASE,
      consultas: ENVIA_QUERIES,
      esSandbox: ENVIA_BASE.includes("api-test"),
      pais,
    },
    // Resumen primero: con doscientas paqueterías, el detalle no cabe en una pantalla.
    resumen: resumir(paqueterias.cuerpo, pais),
    // Los servicios de paquetería del país: con quién se puede cotizar una caja, que
    // no es lo mismo que la lista de paqueterías —ahí salen también las de carga—.
    paqueteria: servicios.ok
      ? deTipoPaquete(servicios.cuerpo, pais)
      : { error: servicios.estado },
    // Los de una paquetería concreta, con todos sus campos. Sirve para comparar una
    // que genera guías con una que solo cotiza, y ver en qué se diferencian.
    detalle: filtrarPorCarrier(servicios.cuerpo, pais, params.get("nombre") ?? ""),
    rutas: respuestas.map((r) => ({
      ruta: r.url.replace(ENVIA_QUERIES, ""),
      estado: r.estado,
      ok: r.ok,
    })),
  }, 200);
});

/** Servicios de paquetería activos y nacionales, agrupados por paquetería. */
function deTipoPaquete(cuerpo: unknown, pais: string) {
  const filas = (cuerpo as { data?: unknown })?.data;
  if (!Array.isArray(filas)) return {};

  const porCarrier: Record<string, string[]> = {};
  for (const f of filas) {
    if (String(f?.country_code ?? "").toUpperCase() !== pais) continue;
    if (Number(f?.service_shipment_type_id) !== 1) continue;
    if (Number(f?.active) !== 1 || Number(f?.international) !== 0) continue;

    const carrier = String(f?.carrier_name ?? "").trim();
    if (!carrier || carrier.startsWith("_test")) continue;
    (porCarrier[carrier] ??= []).push(
      `${f?.name} · ${f?.description ?? ""} · ${f?.delivery_estimate ?? ""}`,
    );
  }
  return porCarrier;
}

/** Todo lo que el catálogo dice de una paquetería, para mirarlo de cerca. */
function filtrarPorCarrier(cuerpo: unknown, pais: string, nombre: string) {
  if (!nombre) return "pasa ?nombre=estafeta para ver sus servicios completos";
  const filas = (cuerpo as { data?: unknown })?.data;
  if (!Array.isArray(filas)) return null;

  return filas.filter((f) =>
    String(f?.country_code ?? "").toUpperCase() === pais &&
    String(f?.carrier_name ?? "").toLowerCase() === nombre.toLowerCase()
  );
}

/** Las paqueterías del país, que son las únicas con las que se va a cotizar. */
function resumir(cuerpo: unknown, pais: string) {
  const filas = (cuerpo as { data?: unknown })?.data;
  if (!Array.isArray(filas)) return { total: 0, delPais: [] as string[] };

  const delPais = filas
    .filter((f) => String(f?.country_code ?? "").toUpperCase() === pais)
    .map((f) => String(f?.name ?? "").trim())
    .filter((n) => n && !n.startsWith("_test"));

  return {
    total: filas.length,
    delPais: [...new Set(delPais)].sort(),
  };
}

