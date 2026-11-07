// ============================================================
// Edge Function · validar-codigo
//
// Dice si un código de la ruleta vale y cuánto descuenta, **para
// enseñarlo**. No aplica nada ni reserva nada.
//
// Quien aplica el descuento de verdad es `crear-pago`, que vuelve a
// validarlo por su cuenta. Esto existe solo para que la persona vea el
// total correcto antes de pulsar «Pagar»: si esta función mintiera, el
// cobro seguiría saliendo bien.
//
// **Exige sesión y cuenta los intentos.** Un validador de códigos sin
// freno se prueba entero: son 31 caracteres en 6 posiciones, pero los
// que valen son solo los emitidos, y con unos miles vivos un script da
// con uno en una tarde. El tope es el único motivo por el que se puede
// contestar «ese código no existe» sin regalar información.
//
// Secrets (Supabase → Edge Functions → Secrets):
//   CONTACT_IP_SALT         sal del hash del IP, la misma de `contact`.
//   CODIGO_MAX_POR_HORA     intentos por cuenta y hora (20).
//   CODIGO_MAX_POR_HORA_IP  intentos por conexión y hora (60).
//   CONTACT_ALLOWED_ORIGINS orígenes permitidos, separados por coma.
// SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY los inyecta la plataforma.
// ============================================================

import { createClient } from "jsr:@supabase/supabase-js@2";

const ALLOWED_ORIGINS = (Deno.env.get("CONTACT_ALLOWED_ORIGINS") ?? "*")
  .split(",")
  .map((o) => o.trim())
  .filter((o) => o.length > 0);

const IP_SALT = Deno.env.get("CONTACT_IP_SALT") ?? "";
const MAX_POR_USUARIO = Math.max(1, Number(Deno.env.get("CODIGO_MAX_POR_HORA") ?? "20"));
const MAX_POR_IP = Math.max(1, Number(Deno.env.get("CODIGO_MAX_POR_HORA_IP") ?? "60"));

/** Lo más largo que puede ser un código. Más que eso ni se mira. */
const LARGO_MAX = 24;

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

/** Texto limpio, o cadena vacía. */
function texto(v: unknown): string {
  return v == null ? "" : String(v).trim();
}

function clientIp(req: Request): string {
  const forwarded = req.headers.get("x-forwarded-for") ?? "";
  return forwarded.split(",")[0].trim() || "desconocido";
}

/** El IP nunca en claro, solo su resumen con sal. Igual que en `contact`. */
async function hashIp(ip: string): Promise<string> {
  const bytes = new TextEncoder().encode(`${IP_SALT}:${ip}`);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

interface FilaGiro {
  codigo: string;
  rotulo: string;
  canjeado: boolean;
  expira_at: string | null;
  wheel_prizes:
    | { tipo: string | null; descuento_pct: number | null; detalle: string | null }
    | null;
}

Deno.serve(async (req: Request): Promise<Response> => {
  const origin = req.headers.get("origin");

  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders(origin) });
  }
  if (req.method !== "POST") {
    return json({ error: "Método no permitido" }, 405, origin);
  }

  // ---- quién llama ----
  const token = (req.headers.get("authorization") ?? "")
    .replace(/^Bearer\s+/i, "")
    .trim();
  if (!token) return json({ error: "Necesitas una sesión." }, 401, origin);

  const comoQuienLlama = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_ANON_KEY") ?? "",
    { global: { headers: { Authorization: `Bearer ${token}` } } },
  );
  const { data: sesion } = await comoQuienLlama.auth.getUser();
  const usuario = sesion?.user;
  if (!usuario) return json({ error: "Necesitas una sesión." }, 401, origin);

  let cuerpo: { codigo?: unknown };
  try {
    cuerpo = await req.json();
  } catch (_e) {
    return json({ error: "Petición ilegible" }, 400, origin);
  }

  const codigo = String(cuerpo.codigo ?? "").trim().toUpperCase();
  if (!codigo || codigo.length > LARGO_MAX) {
    return json({ valido: false, motivo: "Escribe tu código." }, 200, origin);
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  );

  const ipHash = await hashIp(clientIp(req));
  const freno = await demasiados(supabase, usuario.id, ipHash);
  if (freno) return json({ valido: false, motivo: freno }, 429, origin);

  const resultado = await revisar(supabase, codigo);
  await anotar(supabase, usuario.id, ipHash, resultado.valido);

  return json(resultado, 200, origin);
});

interface Veredicto {
  valido: boolean;
  motivo?: string;
  /** `descuento` o `regalo`. Decide qué enseña la pantalla. */
  tipo?: string;
  /** Solo con `descuento`. Con un regalo es cero: no descuenta nada. */
  pct?: number;
  rotulo?: string;
  /** Con un regalo, qué se gana. Es lo que se lee al preparar la caja. */
  detalle?: string;
}

/**
 * ¿Vale este código?
 *
 * Se distingue «no existe» de «ya se usó» y de «caducó» a propósito. Parece que regala
 * información, pero lo que se regala es la existencia de códigos **que ya no sirven**:
 * los que sí valen contestan «válido» de todas formas. Y a quien se equivoca al
 * teclear, decirle qué pasó le ahorra reintentar a ciegas.
 */
async function revisar(
  // deno-lint-ignore no-explicit-any
  supabase: any,
  codigo: string,
): Promise<Veredicto> {
  const { data } = await supabase
    .from("wheel_spins")
    .select(
      "codigo, rotulo, canjeado, expira_at, " +
        "wheel_prizes(tipo, descuento_pct, detalle)",
    )
    .eq("codigo", codigo)
    .maybeSingle();

  const giro = data as FilaGiro | null;
  if (!giro) return { valido: false, motivo: "Ese código no existe." };
  if (giro.canjeado) return { valido: false, motivo: "Ese código ya se usó." };

  if (giro.expira_at && new Date(giro.expira_at).getTime() < Date.now()) {
    return { valido: false, motivo: "Ese código ya caducó." };
  }

  const premio = giro.wheel_prizes;
  const tipo = texto(premio?.tipo);

  // Un premio de consolación no tiene nada que aplicar. Se le dice sin rodeos, que no
  // es culpa suya haber escrito un código real.
  if (tipo !== "descuento" && tipo !== "regalo") {
    return { valido: false, motivo: "Ese código no trae ningún premio que aplicar." };
  }

  // Un regalo no descuenta: lo que hace es que la tienda meta algo más en la caja. Se
  // aplica igual, porque si no se aplica, nadie se entera de que hay que meterlo.
  if (tipo === "regalo") {
    return {
      valido: true,
      tipo,
      pct: 0,
      rotulo: giro.rotulo,
      detalle: texto(premio?.detalle) || giro.rotulo,
    };
  }

  const pct = Number(premio?.descuento_pct ?? 0);
  if (!(pct >= 1 && pct <= 100)) {
    return { valido: false, motivo: "Ese código no trae ningún premio que aplicar." };
  }

  // Un código vale una vez: si ya está puesto en otra solicitud, está apartado. Lo
  // impide igualmente el índice único al cobrar, pero decirlo aquí evita que lo
  // descubra al pulsar «Pagar».
  const { data: usado } = await supabase
    .from("order_requests")
    .select("id")
    .eq("codigo_descuento", codigo)
    .maybeSingle();
  if (usado) {
    return { valido: false, motivo: "Ese código ya está aplicado en otro pedido." };
  }

  return { valido: true, tipo, pct, rotulo: giro.rotulo };
}

/** ¿Se ha pasado probando? Devuelve el motivo, o `null`. Nunca lanza. */
async function demasiados(
  // deno-lint-ignore no-explicit-any
  supabase: any,
  usuarioId: string,
  ipHash: string,
): Promise<string | null> {
  try {
    const desde = new Date(Date.now() - 3600 * 1000).toISOString();
    await supabase.from("codigo_intentos").delete().lt("created_at", desde);

    for (
      const [columna, valor, tope, mensaje] of [
        ["usuario_id", usuarioId, MAX_POR_USUARIO, "Has probado muchos códigos seguidos."],
        ["ip_hash", ipHash, MAX_POR_IP, "Demasiados intentos desde esta conexión."],
      ] as const
    ) {
      const { count } = await supabase
        .from("codigo_intentos")
        .select("id", { count: "exact", head: true })
        .eq(columna, valor)
        .eq("acerto", false)
        .gte("created_at", desde);
      if ((count ?? 0) >= tope) return `${mensaje} Espera un momento.`;
    }
    return null;
  } catch (e) {
    // Aquí **no** se deja pasar a la ligera como en la cotización: lo que protege este
    // freno es dinero. Pero tampoco se bloquea a quien compra por un fallo nuestro, así
    // que se registra y se sigue; si el contador se cae, se ve en los logs.
    console.error("no se pudieron contar los intentos de código", String(e));
    return null;
  }
}

/** Deja constancia del intento. Nunca lanza: acompaña, no decide. */
async function anotar(
  // deno-lint-ignore no-explicit-any
  supabase: any,
  usuarioId: string,
  ipHash: string,
  acerto: boolean,
): Promise<void> {
  try {
    await supabase
      .from("codigo_intentos")
      .insert({ usuario_id: usuarioId, ip_hash: ipHash, acerto } as never);
  } catch (e) {
    console.error("no se pudo anotar el intento de código", String(e));
  }
}
