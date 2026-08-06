// ============================================================
// Edge Function · contact
//
// Recibe el formulario de contacto de la web pública y, antes de guardar:
//   1. valida los campos,
//   2. verifica el token de Cloudflare Turnstile contra Cloudflare,
//   3. aplica un límite de envíos por origen (IP con hash + sal).
//
// Escribe con service_role, que ignora RLS. La tabla `contact_messages` no
// tiene política de INSERT, así que este es el único camino de entrada.
//
// Secrets (Supabase → Edge Functions → Secrets):
//   TURNSTILE_SECRET        clave secreta del widget de Turnstile.
//                           Si está vacía, se omite la verificación (solo dev).
//   CONTACT_IP_SALT         cadena aleatoria para el hash del IP.
//   CONTACT_ALLOWED_ORIGINS orígenes permitidos, separados por coma.
//                           Por defecto "*" (conviene fijarlo en producción).
//   CONTACT_MAX_PER_HOUR    máximo de mensajes por origen y hora (default 5).
// SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY los inyecta la plataforma.
// ============================================================

import { createClient } from "jsr:@supabase/supabase-js@2";

const TURNSTILE_SECRET = Deno.env.get("TURNSTILE_SECRET") ?? "";
const IP_SALT = Deno.env.get("CONTACT_IP_SALT") ?? "passio-agogo";
const ALLOWED_ORIGINS = (Deno.env.get("CONTACT_ALLOWED_ORIGINS") ?? "*")
  .split(",")
  .map((o) => o.trim())
  .filter((o) => o.length > 0);
const MAX_PER_HOUR = Number(Deno.env.get("CONTACT_MAX_PER_HOUR") ?? "5");

const TURNSTILE_VERIFY_URL =
  "https://challenges.cloudflare.com/turnstile/v0/siteverify";

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

function json(
  body: unknown,
  status: number,
  origin: string | null,
): Response {
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

function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

async function verifyTurnstile(token: string, ip: string): Promise<boolean> {
  // Sin secret configurado se omite la verificación (entorno de desarrollo).
  if (!TURNSTILE_SECRET) return true;
  if (!token) return false;

  const form = new FormData();
  form.append("secret", TURNSTILE_SECRET);
  form.append("response", token);
  if (ip && ip !== "desconocido") form.append("remoteip", ip);

  try {
    const res = await fetch(TURNSTILE_VERIFY_URL, { method: "POST", body: form });
    const out = await res.json();
    return out?.success === true;
  } catch {
    return false;
  }
}

Deno.serve(async (req: Request): Promise<Response> => {
  const origin = req.headers.get("origin");

  // Preflight. Debe responder 2xx o el navegador aborta la petición real.
  // Requiere además verify_jwt = false (ver supabase/config.toml): el navegador
  // no envía Authorization en el preflight y el gateway lo rechazaría antes de
  // llegar aquí.
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders(origin) });
  }
  if (req.method !== "POST") {
    return json({ ok: false, error: "metodo-no-permitido" }, 405, origin);
  }

  let payload: {
    nombre?: string;
    email?: string;
    mensaje?: string;
    token?: string;
  };
  try {
    payload = await req.json();
  } catch {
    return json({ ok: false, error: "datos-invalidos" }, 400, origin);
  }

  const nombre = (payload.nombre ?? "").trim();
  const email = (payload.email ?? "").trim();
  const mensaje = (payload.mensaje ?? "").trim();
  const token = payload.token ?? "";

  if (
    nombre.length < 1 || nombre.length > 80 ||
    email.length < 5 || email.length > 120 || !isValidEmail(email) ||
    mensaje.length < 10 || mensaje.length > 2000
  ) {
    return json({ ok: false, error: "datos-invalidos" }, 400, origin);
  }

  const ip = clientIp(req);

  if (!(await verifyTurnstile(token, ip))) {
    return json({ ok: false, error: "captcha-invalido" }, 403, origin);
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } },
  );

  const ipHash = await hashIp(ip);
  const desde = new Date(Date.now() - 60 * 60 * 1000).toISOString();

  const { count, error: countError } = await supabase
    .from("contact_messages")
    .select("id", { count: "exact", head: true })
    .eq("ip_hash", ipHash)
    .gte("created_at", desde);

  if (!countError && (count ?? 0) >= MAX_PER_HOUR) {
    return json({ ok: false, error: "limite-alcanzado" }, 429, origin);
  }

  const { error } = await supabase
    .from("contact_messages")
    .insert({ nombre, email, mensaje, ip_hash: ipHash });

  if (error) {
    console.error("Error al guardar el mensaje:", error.message);
    return json({ ok: false, error: "error-servidor" }, 500, origin);
  }

  return json({ ok: true }, 200, origin);
});
