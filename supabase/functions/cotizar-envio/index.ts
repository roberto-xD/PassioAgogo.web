// ============================================================
// Edge Function · cotizar-envio
//
// Cotiza el envío de un carrito contra Envia y devuelve las opciones
// que la pantalla enseña.
//
// **El token de Envia vive aquí y en ningún otro sitio.** `agogo.js` se
// descarga en el navegador: un token puesto ahí sería público, y el de
// producción genera guías reales y cobra.
//
// **Los pesos y los precios se leen de la base, no de lo que manda el
// navegador.** El cliente solo dice qué variantes y cuántas. Si mandara
// el peso, bastaría con editar la petición para declarar un gramo y
// pagar el envío más barato. Es el mismo criterio que la ruleta: lo que
// decide un cobro no se decide en el navegador.
//
// Lo que **no** recibe ni guarda: la dirección. Solo el código postal de
// destino, que llega suelto para esta consulta y no se escribe en
// ninguna parte. El domicilio sigue cifrado extremo a extremo.
//
// Secrets (Supabase → Edge Functions → Secrets):
//   ENVIA_TOKEN             token de Envia. **Empezar con el de
//                           sandbox**: el de producción cobra.
//   ENVIA_BASE_URL          https://api-test.envia.com (default) o
//                           https://api.envia.com en producción.
//   ENVIA_DEBUG             "1" añade la respuesta cruda de Envia a lo
//                           que se devuelve, para afinar el mapeo.
//   CONTACT_ALLOWED_ORIGINS orígenes permitidos, separados por coma.
// SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY los inyecta la plataforma.
// ============================================================

import { createClient } from "jsr:@supabase/supabase-js@2";

const ENVIA_TOKEN = Deno.env.get("ENVIA_TOKEN") ?? "";
const ENVIA_BASE = (Deno.env.get("ENVIA_BASE_URL") ?? "https://api-test.envia.com")
  .replace(/\/+$/, "");
const DEBUG = Deno.env.get("ENVIA_DEBUG") === "1";
const ALLOWED_ORIGINS = (Deno.env.get("CONTACT_ALLOWED_ORIGINS") ?? "*")
  .split(",")
  .map((o) => o.trim())
  .filter((o) => o.length > 0);

/** Lo que la tienda declara en la guía. Nunca el nombre del producto. */
const CONTENIDO = "Articulos de higiene personal";

/** La caja más grande que maneja la tienda, en centímetros, y su tope de peso. */
const CAJA_MAX = { largo: 100, ancho: 50, alto: 50 };
const PESO_MAX_KG = 40;

/** Topes de la petición: un carrito de verdad no se parece a esto ni de lejos. */
const MAX_RENGLONES = 50;
const MAX_CANTIDAD = 99;

const CP_VALIDO = /^[0-9]{5}$/;

interface Renglon {
  variantId: string;
  cantidad: number;
}

interface Opcion {
  paqueteria: string;
  servicio: string;
  descripcion: string;
  precio: number;
  moneda: string;
  dias: number | null;
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

/**
 * Arma el paquete único del carrito.
 *
 * Todo va en una caja, que es como se envía de hecho. El peso se suma; las medidas
 * parten de la pieza más grande —la caja tiene que poder contenerla— y crecen **en los
 * tres lados a la vez** hasta alcanzar el volumen de todo lo demás.
 *
 * Que crezcan a la vez importa: estirando solo el lado largo, doce cubos de 10 cm daban
 * una caja de 100x10x10, un tubo de un metro que ninguna tienda arma y que las
 * paqueterías cobran caro por volumétrico. Repartido, salen 23x23x23, que es la caja que
 * de verdad se usaría.
 *
 * Al final se recorta a la caja mayor de la tienda. Si lo pedido no cabiera ahí, la
 * cotización saldría por debajo de lo que costará la guía: es un caso raro y la
 * diferencia la absorbe la tienda, que es la decisión que se tomó. Lo que no se hace es
 * disimularlo: se devuelve `recortado` para poder decir que es estimada.
 */
export function armarPaquete(
  filas: Array<{ peso_gr: number; largo_cm: number; ancho_cm: number; alto_cm: number }>,
  cantidades: number[],
) {
  let pesoGr = 0;
  let volumen = 0;
  // El lado mayor de la pieza más grande, el medio de la más grande, etc. Ordenar los
  // lados de cada pieza evita que una caja «plana» y otra «de pie» se midan distinto.
  const base = [0, 0, 0];

  filas.forEach((fila, i) => {
    const cantidad = cantidades[i];
    pesoGr += fila.peso_gr * cantidad;
    volumen += fila.largo_cm * fila.ancho_cm * fila.alto_cm * cantidad;

    const lados = [fila.largo_cm, fila.ancho_cm, fila.alto_cm].sort((a, b) => b - a);
    for (let j = 0; j < 3; j++) base[j] = Math.max(base[j], lados[j]);
  });

  let largo = Math.max(1, base[0]);
  let ancho = Math.max(1, base[1]);
  let alto = Math.max(1, base[2]);

  // Crecer los tres lados por igual hasta cubrir el volumen, manteniendo la proporción
  // de la pieza más grande.
  const factor = Math.cbrt(volumen / (largo * ancho * alto));
  if (factor > 1) {
    largo = Math.ceil(largo * factor);
    ancho = Math.ceil(ancho * factor);
    alto = Math.ceil(alto * factor);
  }

  largo = Math.min(CAJA_MAX.largo, largo);
  ancho = Math.min(CAJA_MAX.ancho, ancho);
  alto = Math.min(CAJA_MAX.alto, alto);

  // Si al recortar dejó de caber, se estira lo que todavía tenga holgura. El largo es el
  // lado con más margen, así que es el que se aprovecha.
  if (largo * ancho * alto < volumen) {
    largo = Math.min(CAJA_MAX.largo, Math.ceil(volumen / (ancho * alto)));
  }

  // Envia quiere kilos; en la base están los gramos, que es como se leen de una báscula.
  const pesoReal = pesoGr / 1000;
  const pesoKg = Math.min(PESO_MAX_KG, Math.max(0.1, pesoReal));

  return {
    peso: Math.round(pesoKg * 100) / 100,
    largo,
    ancho,
    alto,
    // Lo pedido no cabe en la caja mayor de la tienda, o pesa más de lo que admite: lo
    // que se cotiza va por debajo de lo que costará la guía.
    recortado: pesoReal > PESO_MAX_KG || largo * ancho * alto < volumen,
  };
}

/** Normaliza una tarifa de Envia a lo poco que la pantalla enseña. */
function aOpcion(t: Record<string, unknown>): Opcion | null {
  const precio = Number(t.totalPrice ?? t.total_price ?? t.price ?? NaN);
  if (!Number.isFinite(precio)) return null;

  const dias = Number(
    t.deliveryEstimate ?? t.delivery_estimate ?? t.deliveryDays ?? NaN,
  );

  return {
    paqueteria: String(t.carrier ?? ""),
    servicio: String(t.service ?? ""),
    descripcion: String(t.serviceDescription ?? t.service_description ?? ""),
    precio,
    moneda: String(t.currency ?? "MXN"),
    dias: Number.isFinite(dias) ? dias : null,
  };
}

Deno.serve(async (req: Request): Promise<Response> => {
  const origin = req.headers.get("origin");

  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders(origin) });
  }
  if (req.method !== "POST") {
    return json({ error: "Método no permitido" }, 405, origin);
  }
  if (!ENVIA_TOKEN) {
    return json({ error: "La cotización no está configurada." }, 503, origin);
  }

  // ---- lo que manda el navegador, que es poco y se revisa todo ----
  let cuerpo: { cpDestino?: unknown; items?: unknown };
  try {
    cuerpo = await req.json();
  } catch (_e) {
    return json({ error: "Petición ilegible" }, 400, origin);
  }

  const cpDestino = String(cuerpo.cpDestino ?? "").trim();
  if (!CP_VALIDO.test(cpDestino)) {
    return json({ error: "Código postal de destino inválido" }, 400, origin);
  }

  const crudos = Array.isArray(cuerpo.items) ? cuerpo.items : [];
  if (crudos.length === 0 || crudos.length > MAX_RENGLONES) {
    return json({ error: "Carrito vacío o demasiado grande" }, 400, origin);
  }

  const renglones: Renglon[] = [];
  for (const it of crudos) {
    const variantId = String((it as Renglon)?.variantId ?? "").trim();
    const cantidad = Math.floor(Number((it as Renglon)?.cantidad ?? 0));
    if (!variantId || !(cantidad >= 1 && cantidad <= MAX_CANTIDAD)) {
      return json({ error: "Artículo inválido en el carrito" }, 400, origin);
    }
    renglones.push({ variantId, cantidad });
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  );

  // ---- el origen: la ubicación marcada como tal ----
  const { data: origen } = await supabase
    .from("locations")
    .select(
      "nombre, contacto, telefono, calle, numero, interior, colonia, ciudad, " +
        "estado_codigo, cp, pais",
    )
    .eq("es_origen", true)
    .maybeSingle();

  if (!origen?.cp || !origen?.estado_codigo || !origen?.telefono) {
    return json(
      { error: "Falta configurar la dirección de origen de la tienda." },
      503,
      origin,
    );
  }

  // ---- los pesos: de la base, nunca del navegador ----
  const ids = renglones.map((r) => r.variantId);
  const { data: variantes } = await supabase
    .from("product_variants")
    .select("id, precio_venta, peso_gr, largo_cm, ancho_cm, alto_cm")
    .in("id", ids);

  const porId = new Map((variantes ?? []).map((v) => [v.id as string, v]));
  if (porId.size !== new Set(ids).size) {
    return json({ error: "Alguno de los artículos ya no está disponible." }, 409, origin);
  }

  const filas = renglones.map((r) => porId.get(r.variantId)!);
  const cantidades = renglones.map((r) => r.cantidad);
  const paquete = armarPaquete(
    filas as Array<{ peso_gr: number; largo_cm: number; ancho_cm: number; alto_cm: number }>,
    cantidades,
  );

  const valor = renglones.reduce((suma, r) => {
    const precio = Number(porId.get(r.variantId)?.precio_venta ?? 0);
    return suma + precio * r.cantidad;
  }, 0);

  // ---- la petición a Envia ----
  // Sin `carrier`: así devuelve todas las paqueterías de la ruta y se eligen por precio.
  const peticion = {
    origin: {
      name: origen.nombre ?? "Passion à gogo",
      company: origen.nombre ?? "Passion à gogo",
      phone: origen.telefono,
      street: origen.calle ?? "",
      number: origen.numero ?? "",
      district: origen.colonia ?? "",
      city: origen.ciudad ?? "",
      state: origen.estado_codigo,
      country: origen.pais ?? "MX",
      postalCode: origen.cp,
    },
    destination: {
      // De la persona solo se manda lo que hace falta para saber cuánto cuesta llegar
      // a su código postal. Nombre, calle y teléfono no se necesitan para cotizar.
      name: "Destinatario",
      phone: origen.telefono,
      street: "",
      city: "",
      state: "",
      country: "MX",
      postalCode: cpDestino,
    },
    packages: [{
      type: "box",
      content: CONTENIDO,
      amount: 1,
      declaredValue: Math.round(valor * 100) / 100,
      lengthUnit: "CM",
      weightUnit: "KG",
      weight: paquete.peso,
      dimensions: {
        length: paquete.largo,
        width: paquete.ancho,
        height: paquete.alto,
      },
    }],
    shipment: { type: 1 },
  };

  let respuesta: Response;
  try {
    respuesta = await fetch(`${ENVIA_BASE}/ship/rate/`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${ENVIA_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(peticion),
      // Nadie espera de pie a que una paquetería conteste.
      signal: AbortSignal.timeout(15000),
    });
  } catch (_e) {
    return json({ error: "La paquetería no respondió. Inténtalo de nuevo." }, 504, origin);
  }

  const crudo = await respuesta.json().catch(() => null);
  if (!respuesta.ok) {
    // El detalle se queda en los registros: a quien compra no le dice nada, y repetirlo
    // en pantalla solo enseña cómo está armada la petición.
    console.error("envia /ship/rate", respuesta.status, JSON.stringify(crudo));
    return json({ error: "No pudimos cotizar el envío ahora mismo." }, 502, origin);
  }

  const tarifas = Array.isArray(crudo?.data) ? crudo.data : [];
  const opciones = tarifas
    .map((t: Record<string, unknown>) => aOpcion(t))
    .filter((o: Opcion | null): o is Opcion => o !== null)
    .sort((a: Opcion, b: Opcion) => a.precio - b.precio);

  return json(
    {
      opciones,
      paquete: { peso: paquete.peso, largo: paquete.largo, ancho: paquete.ancho, alto: paquete.alto },
      // Avisa de que lo cotizado es la caja tope y el bulto real la excede.
      recortado: paquete.recortado,
      ...(DEBUG ? { crudo } : {}),
    },
    200,
    origin,
  );
});
