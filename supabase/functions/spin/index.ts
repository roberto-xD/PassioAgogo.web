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
// Escribe con service_role, que ignora RLS. Ni `wheel_spins` ni
// `codigos` tienen política de INSERT para el cliente, así que este es
// el único camino de entrada.
//
// Escribe en las dos: en `codigos` lo que se canjeará al pagar
// (48_codigos.sql) y en `wheel_spins` el registro de quién giró. Eran
// una sola tabla hasta que hubo códigos que no venían de ningún giro
// —los de campaña— y giros que no dan código.
//
// Requiere 45_premio_tipo.sql y 48_codigos.sql: lee `tipo`,
// `descuento_pct` y `minimo_compra` de `wheel_prizes`. Sin esos scripts
// la consulta de premios falla y la ruleta contesta «sin-premios», que
// es mejor que repartir códigos que nadie podría canjear.
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
  tipo?: string | null;
  descuento_pct?: number | null;
  minimo_compra?: number | null;
  vigencia_dias?: number | null;
};

/**
 * Qué se canjea con este premio, en los términos de `codigos`, o null si no se canjea.
 *
 * `codigos` es la tabla de lo que se canjea al pagar (48_codigos.sql); `wheel_spins`
 * sigue siendo el registro de quién giró y cuándo. Esta función traduce un sector de la
 * rueda a una fila de la primera.
 *
 * Devuelve null —y no una fila a medias— cuando el premio está mal configurado: un
 * `descuento` sin porcentaje no descuenta nada y el check de coherencia de la tabla lo
 * rechazaría. Mejor un giro sin código que un código que falla al canjearse.
 */
function premioCanjeable(premio: Premio): Record<string, unknown> | null {
  const tipo = (premio.tipo ?? "descuento").trim();
  const minimo = premio.minimo_compra ?? null;

  if (tipo === "envio_gratis") {
    return { tipo, descuento_pct: null, regalo: null, minimo_compra: minimo };
  }
  if (tipo === "regalo") {
    const regalo = (premio.detalle ?? premio.rotulo ?? "").trim();
    if (!regalo) return null;
    return { tipo, descuento_pct: null, regalo, minimo_compra: minimo };
  }
  if (tipo === "descuento") {
    const pct = Number(premio.descuento_pct ?? 0);
    if (!(pct >= 1 && pct <= 100)) return null;
    return { tipo, descuento_pct: pct, regalo: null, minimo_compra: minimo };
  }
  return null;
}

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
    .select(
      "id, rotulo, detalle, peso, otorga_premio, vigencia_dias, " +
        "tipo, descuento_pct, minimo_compra",
    )
    .eq("activo", true)
    .order("orden", { ascending: true });

  if (errorPremios || !premios || premios.length === 0) {
    console.error("No hay premios activos:", errorPremios?.message);
    return json({ ok: false, error: "sin-premios" }, 503, origin);
  }

  // `as unknown` por medio: el cliente genérico de Supabase no sabe la forma de esta
  // consulta y la infiere como un error de cadena, así que un cast directo no compila.
  const ganador = sortear(premios as unknown as Premio[]);
  if (!ganador) {
    return json({ ok: false, error: "sin-premios" }, 503, origin);
  }

  // El código emitido. Queda vacío cuando el sector no otorga nada: la web solo lo
  // enseña con premio, así que un giro sin premio no necesita ninguno.
  let codigo = "";
  // La caducidad se graba **en el código**, no se deduce del premio al canjear. Si
  // mañana acortas la vigencia, los códigos ya emitidos conservan la que se les
  // prometió: no se le cambia el plazo a nadie a toro pasado. Sin vigencia, nulo, que
  // es «no caduca».
  const dias = Number((ganador as { vigencia_dias?: number | null }).vigencia_dias ?? 0);
  const expiraAt = dias > 0
    ? new Date(Date.now() + dias * 86_400_000).toISOString()
    : null;

  // El código se emite **antes** de anotar el giro, y en este orden a propósito. Lo que
  // hace que el premio valga algo es la fila de `codigos`: es la que se canjea al pagar.
  // Si se anotara primero el giro y fallara el código, la persona se iría con un código
  // que al teclearlo en el carrito contesta «no existe», y el enfriamiento por origen no
  // la dejaría girar otra vez. Al revés, lo peor que pasa es perder una fila de
  // `wheel_spins`, que es registro, no premio.
  //
  // Un premio que no otorga nada no emite código: no hay nada que canjear, y la web
  // tampoco le enseña ninguno.
  const canjeable = ganador.otorga_premio ? premioCanjeable(ganador as Premio) : null;
  if (ganador.otorga_premio && !canjeable) {
    // Un «descuento» sin porcentaje, un «regalo» sin detalle. Se registra y se sigue:
    // el giro se anota igual, pero sin código, que es lo honesto cuando no hay premio
    // que entregar.
    console.error("Premio sin nada que canjear:", ganador.id, ganador.rotulo);
  }

  if (canjeable) {
    for (let intento = 0; intento < 5 && !codigo; intento++) {
      const candidato = nuevoCodigo();
      const { error } = await supabase.from("codigos").insert({
        ...canjeable,
        codigo: candidato,
        usos_max: 1,
        expira_at: expiraAt,
        origen: "ruleta",
        prize_id: ganador.id,
      });
      if (!error) codigo = candidato;
      else if (!error.message.includes("duplicate")) {
        console.error("Error al emitir el código:", error.message);
        return json({ ok: false, error: "error-servidor" }, 500, origin);
      }
    }
    // Cinco colisiones seguidas entre 31^6 combinaciones no es mala suerte, es otra
    // cosa. Antes que entregar un premio sin código, se dice que falló.
    if (!codigo) return json({ ok: false, error: "error-servidor" }, 500, origin);
  }

  // El giro se anota siempre: es lo que sostiene el enfriamiento por origen y lo que
  // dice qué sale de verdad en la rueda. Su columna `codigo` es única y obligatoria, así
  // que un giro sin premio lleva uno propio que no canjea nada.
  const { error: errorGiro } = await supabase.from("wheel_spins").insert({
    codigo: codigo || nuevoCodigo(),
    prize_id: ganador.id,
    rotulo: ganador.rotulo,
    otorga_premio: ganador.otorga_premio,
    ip_hash: ipHash,
    expira_at: expiraAt,
  });
  // No se aborta: el código ya está emitido y es válido. Perder el registro del giro
  // cuesta una fila de estadística; negar el premio cuesta el premio.
  if (errorGiro) console.error("Error al guardar el giro:", errorGiro.message);

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
