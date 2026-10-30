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
  // Id de paquetería para preguntar qué deja hacer. Estafeta MX es la 11, y es la que
  // nos rechazó la guía, así que es el caso de prueba natural.
  const carrierId = params.get("carrier") ?? "11";

  // `/available-service/{pais}` contestó 404: la ruta documentada no existe en este
  // host. En vez de adivinar una por una, se prueban las candidatas de golpe y se
  // enseña cuál responde. Son consultas de solo lectura: probar no rompe nada.
  const rutas = [
    "/carrier",
    `/available-service/${pais}`,
    `/service?country_code=${pais}`,
    "/service",
    `/carrier-actions/${carrierId}`,
    `/carrier/${carrierId}/actions`,
    `/carrier-service?carrier_id=${carrierId}`,
  ];

  const respuestas = await Promise.all(rutas.map((r) => consultar(r)));
  const paqueterias = respuestas[0];

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
    // Qué ruta contesta y cuál no. Las que den 200 son las que sirven.
    rutas: respuestas.map((r) => ({
      ruta: r.url.replace(ENVIA_QUERIES, ""),
      estado: r.estado,
      ok: r.ok,
      // De las que funcionen, lo justo para ver la forma; el catálogo entero no.
      muestra: r.ok ? recortar(r.cuerpo) : r.cuerpo,
    })),
  }, 200);
});

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

/** Lo justo para reconocer la forma de una respuesta sin volcar un catálogo entero. */
function recortar(cuerpo: unknown): unknown {
  const filas = (cuerpo as { data?: unknown })?.data;
  if (Array.isArray(filas)) {
    return { total: filas.length, primeras: filas.slice(0, 3) };
  }
  return cuerpo;
}
