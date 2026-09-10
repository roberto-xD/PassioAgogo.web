// ============================================================
// Edge Function · spin
//
// Decide el premio de la ruleta y emite un código verificable.
//
// **El premio no se decide en el navegador a propósito.** Si lo hiciera,
// bastaría recargar para volver a girar hasta que saliera el mejor, y
// quien abriera las herramientas del navegador podría forzar el
// resultado. Aquí el sorteo ocurre en el servidor, con los pesos que
// nunca salen de la base, y queda registrado antes de responder.
//
// La ruleta de la web gira igual de bonito: cuando empieza a girar el
// resultado ya está decidido, y la animación solo se encarga de frenar
// en el sector que toca.
//
// Escribe con service_role, que ignora RLS. `wheel_spins` no tiene
// política de INSERT, así que este es el único camino de entrada.
//
// Secrets (Supabase → Edge Functions → Secrets):
//   CONTACT_IP_SALT         sal para el hash del IP. Se comparte con la
//                           función `contact`: es la misma instalación.
//   CONTACT_ALLOWED_ORIGINS orígenes permitidos, separados por coma.
//   SPIN_COOLDOWN_HOURS     horas entre giros por origen (default 24).
//                           0 = sin límite, solo para probar.
// SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY los inyecta la plataforma.
// ============================================================

import { createClient } from "jsr:@supabase/supabase-js@2";

const IP_SALT = Deno.env.get("CONTACT_IP_SALT") ?? "passio-agogo";
const ALLOWED_ORIGINS = (Deno.env.get("CONTACT_ALLOWED_ORIGINS") ?? "*")
  .split(",")
  .map((o) => o.trim())
  .filter((o) => o.length > 0);
const COOLDOWN_HOURS = Number(Deno.env.get("SPIN_COOLDOWN_HOURS") ?? "24");

/** Sin vocales ni caracteres que se confundan al dictarlos por teléfono. */
const ALFABETO_CODIGO = "0123456789ACDEFGHJKLMNPQRTUVWXY";
const LARGO_CODIGO = 6;
const PREFIJO_CODIGO = "PAG-";

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
  return new Response(JSON.stringify(body), {
    status,
    headers: corsHeaders(origin),
  });
}

function clientIp(req: Request): string {
  const forwarded = req.headers.get("x-forwarded-for") ?? "";
  return forwarded.split(",")[0].trim() || "desconocido";
}

async function hashIp(ip: string): Promise<string> {
  const bytes = new TextEncoder().encode(`${IP_SALT}:${ip}`);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/** Código corto y legible al dictarlo. Aleatoriedad criptográfica, no Math.random. */
function nuevoCodigo(): string {
  const bytes = new Uint8Array(LARGO_CODIGO);
  crypto.getRandomValues(bytes);
  let salida = "";
  for (const b of bytes) salida += ALFABETO_CODIGO[b % ALFABETO_CODIGO.length];
  return PREFIJO_CODIGO + salida;
}

type Premio = {
  id: string;
  rotulo: string;
  detalle: string | null;
  peso: number;
  otorga_premio: boolean;
};

/**
 * Sorteo ponderado: se tira un número dentro de la suma de pesos y se avanza
 * por la lista hasta pasarlo. Un premio con peso 0 nunca sale, pero se sigue
 * dibujando en la ruleta.
 */
function sortear(premios: Premio[]): Premio | null {
  const total = premios.reduce((suma, p) => suma + Math.max(0, p.peso), 0);
  if (total <= 0) return null;

  // Uniforme en [0, total) a partir de bytes criptográficos.
  const bytes = new Uint32Array(1);
  crypto.getRandomValues(bytes);
  let punto = (bytes[0] / 0x1_0000_0000) * total;

  for (const p of premios) {
    punto -= Math.max(0, p.peso);
    if (punto < 0) return p;
  }
  return premios[premios.length - 1];
}

Deno.serve(async (req: Request): Promise<Response> => {
  const origin = req.headers.get("origin");

  // Preflight. Requiere verify_jwt = false (ver supabase/config.toml): el
  // navegador no envía Authorization en el preflight y el gateway lo
  // rechazaría antes de llegar aquí.
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders(origin) });
  }
  if (req.method !== "POST") {
    return json({ ok: false, error: "metodo-no-permitido" }, 405, origin);
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } },
  );

  const ipHash = await hashIp(clientIp(req));

  // ¿Ya giró dentro de la ventana? Se devuelve lo que ganó entonces en lugar
  // de un error: la persona no ha hecho nada malo y su código sigue siendo
  // válido, así que verlo otra vez es más útil que un «no puedes».
  if (COOLDOWN_HOURS > 0) {
    const desde = new Date(Date.now() - COOLDOWN_HOURS * 3600 * 1000)
      .toISOString();
    const { data: previo } = await supabase
      .from("wheel_spins")
      .select("codigo, rotulo, prize_id, otorga_premio, created_at")
      .eq("ip_hash", ipHash)
      .gte("created_at", desde)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (previo) {
      return json({
        ok: true,
        yaGiro: true,
        prizeId: previo.prize_id,
        rotulo: previo.rotulo,
        detalle: null,
        codigo: previo.codigo,
        otorgaPremio: previo.otorga_premio,
      }, 200, origin);
    }
  }

  const { data: premios, error: errorPremios } = await supabase
    .from("wheel_prizes")
    .select("id, rotulo, detalle, peso, otorga_premio")
    .eq("activo", true)
    .order("orden", { ascending: true });

  if (errorPremios || !premios || premios.length === 0) {
    console.error("No hay premios activos:", errorPremios?.message);
    return json({ ok: false, error: "sin-premios" }, 503, origin);
  }

  const ganador = sortear(premios as Premio[]);
  if (!ganador) {
    return json({ ok: false, error: "sin-premios" }, 503, origin);
  }

  // Reintento por si el código colisiona con uno ya emitido. Con 31^6
  // combinaciones es improbable, pero la columna es única y una colisión
  // devolvería un error feo en lugar de un premio.
  let codigo = "";
  let guardado = false;
  for (let intento = 0; intento < 5 && !guardado; intento++) {
    codigo = nuevoCodigo();
    const { error } = await supabase.from("wheel_spins").insert({
      codigo,
      prize_id: ganador.id,
      rotulo: ganador.rotulo,
      otorga_premio: ganador.otorga_premio,
      ip_hash: ipHash,
    });
    if (!error) guardado = true;
    else if (!error.message.includes("duplicate")) {
      console.error("Error al guardar el giro:", error.message);
      return json({ ok: false, error: "error-servidor" }, 500, origin);
    }
  }

  if (!guardado) {
    return json({ ok: false, error: "error-servidor" }, 500, origin);
  }

  return json({
    ok: true,
    yaGiro: false,
    prizeId: ganador.id,
    rotulo: ganador.rotulo,
    detalle: ganador.detalle,
    codigo,
    otorgaPremio: ganador.otorga_premio,
  }, 200, origin);
});
