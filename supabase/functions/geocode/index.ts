// ============================================================
// Edge Function · geocode
//
// Resuelve un código postal mexicano en ciudad, estado y sus colonias,
// preguntándole al catálogo público de Envia.
//
// **No existe por secreto, existe por discreción.** El catálogo de Envia
// es abierto y con CORS permisivo: el navegador podría consultarlo
// directo y funcionaría igual. Lo que cambia al pasar por aquí es quién
// pregunta. Yendo directo, Envia y su CDN ven la IP de cada cliente y el
// código postal que teclea, incluso el de quien arma un carrito y nunca
// compra. Desde aquí solo ven este servidor.
//
// No usa service_role ni toca la base: es una consulta de ida y vuelta.
//
// Secrets (Supabase → Edge Functions → Secrets):
//   CONTACT_ALLOWED_ORIGINS orígenes permitidos, separados por coma.
//                           Se comparte con `contact` y `spin`: es la
//                           misma instalación.
// ============================================================

const ALLOWED_ORIGINS = (Deno.env.get("CONTACT_ALLOWED_ORIGINS") ?? "*")
  .split(",")
  .map((o) => o.trim())
  .filter((o) => o.length > 0);

const GEOCODES = "https://geocodes.envia.com/zipcode/MX";

/** Un código postal mexicano: cinco dígitos y nada más. */
const CP_VALIDO = /^[0-9]{5}$/;

/**
 * Lo ya resuelto, mientras esta instancia siga viva.
 *
 * Los códigos postales no cambian de ciudad. Supabase recicla las
 * instancias cuando quiere, así que esto no es una caché de verdad: es
 * no molestar a Envia dos veces por el mismo código en el rato que dure.
 */
const memoria = new Map<string, Lugar | null>();

/** Lo poco que la pantalla necesita. */
interface Lugar {
  cp: string;
  ciudad: string;
  estado: string;
  estadoCodigo: string;
  colonias: string[];
}

function corsHeaders(origin: string | null): Record<string, string> {
  const allowAll = ALLOWED_ORIGINS.includes("*");
  const allowed = allowAll
    ? "*"
    : (origin && ALLOWED_ORIGINS.includes(origin) ? origin : ALLOWED_ORIGINS[0]);
  return {
    "Access-Control-Allow-Origin": allowed,
    "Access-Control-Allow-Headers":
      "authorization, apikey, content-type, x-client-info",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Max-Age": "86400",
    "Vary": "Origin",
    "Content-Type": "application/json",
  };
}

function json(body: unknown, status: number, origin: string | null): Response {
  const headers = corsHeaders(origin);
  // Un código postal pertenece al mismo estado hoy que mañana. Dejar que
  // el navegador se lo quede evita repetir la consulta en cada visita.
  if (status === 200) headers["Cache-Control"] = "public, max-age=86400";
  return new Response(JSON.stringify(body), { status, headers });
}

/**
 * Pregunta a Envia por un código postal.
 *
 * Devuelve `null` tanto si el código no existe como si Envia no contesta:
 * a la pantalla le da igual el motivo, en los dos casos la dirección se
 * escribe a mano.
 */
async function consultar(cp: string): Promise<Lugar | null> {
  if (memoria.has(cp)) return memoria.get(cp) ?? null;

  let lugar: Lugar | null = null;
  try {
    // Sin tiempo de espera, una respuesta lenta de Envia se come el
    // presupuesto de la función y la pantalla se queda en «Buscando…».
    const corte = AbortSignal.timeout(6000);
    const respuesta = await fetch(`${GEOCODES}/${cp}`, { signal: corte });

    if (respuesta.ok) {
      // Un código que no existe contesta 200 con una lista vacía.
      const filas = await respuesta.json();
      const fila = Array.isArray(filas) ? filas[0] : null;
      const codigo = fila?.state?.code?.["2digit"] ?? "";

      // Sin código de estado la respuesta no sirve para lo único que la
      // queremos: escribir el estado como la paquetería lo entiende.
      if (codigo) {
        lugar = {
          cp: fila.zip_code ?? cp,
          // `locality` y no la alcaldía: fuera de la capital coinciden, y
          // en la Ciudad de México es lo que espera leer una paquetería.
          ciudad: fila.locality ?? "",
          estado: fila.state?.name ?? "",
          estadoCodigo: codigo,
          colonias: Array.isArray(fila.suburbs)
            ? fila.suburbs.filter((s: unknown) => typeof s === "string" && s)
            : [],
        };
      }
    }
  } catch (_e) {
    // Se cae al mismo sitio que un código inexistente, a propósito.
    lugar = null;
  }

  memoria.set(cp, lugar);
  return lugar;
}

Deno.serve(async (req: Request): Promise<Response> => {
  const origin = req.headers.get("origin");

  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders(origin) });
  }
  if (req.method !== "GET") {
    return json({ error: "Método no permitido" }, 405, origin);
  }

  // El código se valida antes de salir a la red. Es lo único que impide
  // que esta función sirva para pedir cualquier otra cosa en nombre del
  // servidor: lo que no sean cinco dígitos no llega a construir una URL.
  const cp = new URL(req.url).searchParams.get("cp")?.trim() ?? "";
  if (!CP_VALIDO.test(cp)) {
    return json({ error: "Código postal inválido" }, 400, origin);
  }

  const lugar = await consultar(cp);
  // Que no exista no es un error: es una respuesta, y la pantalla la usa
  // para dejar los campos a mano.
  return json(lugar ?? { encontrado: false }, 200, origin);
});
