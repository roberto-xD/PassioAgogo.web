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

/**
 * El token, limpio de lo que suele venirse pegado al copiarlo.
 *
 * Un espacio al final, un salto de línea o un «Bearer » delante sobreviven al
 * copiar-y-pegar sin verse, y Envia los rechaza con un 401 idéntico al de un token
 * equivocado. Quitarlos aquí ahorra buscar el error donde no está.
 */
const ENVIA_TOKEN = (Deno.env.get("ENVIA_TOKEN") ?? "")
  .trim()
  .replace(/^Bearer\s+/i, "")
  .trim();
const ENVIA_BASE = (Deno.env.get("ENVIA_BASE_URL") ?? "https://api-test.envia.com")
  .replace(/\/+$/, "");
/**
 * La API de consultas, en el mismo entorno que la de envíos.
 *
 * Tiene host propio por entorno y los tokens no son intercambiables. Estuvo apuntando
 * siempre a producción mientras el token era de sandbox: contestaba 401, la lista de
 * paqueterías caía al respaldo escrito a mano, y como ese respaldo acertaba, nadie lo
 * notó durante varias rondas. Por eso se deduce del host de envíos en vez de escribirse
 * aparte: así no pueden desalinearse.
 */
const ENVIA_QUERIES = (Deno.env.get("ENVIA_QUERIES_URL") ??
  (ENVIA_BASE.includes("api-test")
    ? "https://queries.test.envia.com"
    : "https://queries.envia.com"))
  .replace(/\/+$/, "");
const DEBUG = Deno.env.get("ENVIA_DEBUG") === "1";

/**
 * Paqueterías a consultar, separadas por coma. Vacío = preguntárselas a Envia.
 *
 * Sirve para dejar fuera las que no interesen —por ejemplo, las que no pasan a recoger—
 * sin volver a desplegar.
 */
const CARRIERS_FIJOS = (Deno.env.get("ENVIA_CARRIERS") ?? "")
  .split(",")
  .map((c) => c.trim().toLowerCase())
  .filter((c) => c.length > 0);

/**
 * Con las que se cotiza si el catálogo de Envia no contesta.
 *
 * Son las grandes de México y **es una lista a ojo**: los nombres exactos los manda
 * Envia, y lo correcto es preguntárselos. Esto solo evita que un fallo suyo deje la
 * pantalla sin ninguna opción.
 */
const CARRIERS_DE_RESPALDO = ["fedex", "dhl", "estafeta", "redpack", "paquetexpress"];

/** País de la tienda. El catálogo es mundial y solo interesa una parte. */
const PAIS = (Deno.env.get("ENVIA_PAIS") ?? "MX").toUpperCase();

/** Paquetería, frente a carga por tarima. Es el `type` que va en la petición. */
const TIPO_PAQUETE = 1;

/**
 * Tope de paqueterías a consultar por cotización.
 *
 * El catálogo trae más de doscientas, y unas cuarenta solo de México. Cotizar es **una
 * llamada por paquetería**: sin tope, cada vez que alguien pulsa «Ver opciones de envío»
 * se abren cuarenta peticiones a Envia, la mayoría a empresas sin contrato. Tarda, y es
 * ruido que no se pidió.
 *
 * Lo sensato en producción es fijar `ENVIA_CARRIERS` con las que de verdad se usan; este
 * tope es la red para cuando no está puesto.
 */
const MAX_CARRIERS = Number(Deno.env.get("ENVIA_MAX_CARRIERS") ?? "8");

/** Tope de opciones en la lista final. */
const MAX_OPCIONES = Number(Deno.env.get("ENVIA_MAX_OPCIONES") ?? "8");

/**
 * Cuántos servicios enseñar de cada paquetería, repartidos por precio.
 *
 * Con 1 sale la más barata. Con 2, **la más barata y la más cara**: casi todas tienen un
 * terrestre lento y un express, y hay quien paga por el express. Enseñar solo lo barato
 * se lo esconde a quien tiene prisa. Con 3 o más se reparten entre esos dos extremos.
 *
 * 0 = sin límite por paquetería; entonces manda [MAX_OPCIONES].
 */
const POR_PAQUETERIA = Number(Deno.env.get("ENVIA_OPCIONES_POR_PAQUETERIA") ?? "2");

/** Esconder los servicios que terminan en sucursal. Ver [llegaADomicilio]. */
const SOLO_A_DOMICILIO = (Deno.env.get("ENVIA_SOLO_A_DOMICILIO") ?? "1") !== "0";

/**
 * Descarta lo que cueste más de N veces la opción más barata. 0 = no descartar nada.
 *
 * Es el filtro **por regla** para no enseñar disparates, en lugar de tachar paqueterías
 * por su nombre: lo caro depende de la ruta, no de la empresa. FedEx pedía 501 pesos a
 * Morelia por recargos de zona extendida y combustible, y en una entrega dentro de la
 * ciudad puede ser la más barata. Tachándola por nombre se perdería ese caso.
 */
const FACTOR_MAX = Number(Deno.env.get("ENVIA_FACTOR_MAX") ?? "0");
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

/**
 * Las filas que se leen, escritas a mano.
 *
 * El cliente de Supabase no conoce el esquema, así que sin esto todo lo que vuelve es
 * «algo»: el compilador no avisaría de un nombre de columna mal escrito y el fallo
 * aparecería en producción como un campo vacío en la petición a la paquetería.
 */
interface FilaOrigen {
  nombre: string | null;
  contacto: string | null;
  telefono: string | null;
  calle: string | null;
  numero: string | null;
  interior: string | null;
  colonia: string | null;
  ciudad: string | null;
  estado_codigo: string | null;
  cp: string | null;
  pais: string | null;
}

interface FilaVariante {
  id: string;
  precio_venta: number | null;
  peso_gr: number;
  largo_cm: number;
  ancho_cm: number;
  alto_cm: number;
}

interface Opcion {
  paqueteria: string;
  servicio: string;
  descripcion: string;
  precio: number;
  moneda: string;
  /** Días estimados, cuando se sabe el número. */
  dias: number | null;
  /** El nombre como se escribe: «FedEx», no «fedex». */
  nombre: string;
  /** Logo de la paquetería, si el catálogo lo trae. Vacío si no. */
  logo: string;
  /** Lo que promete la paquetería con sus palabras: «2-4 días». */
  entrega: string;
  /**
   * El paquete llega a la puerta del cliente.
   *
   * Falso en los servicios «ocurre» de destino, donde el cliente recoge en sucursal.
   * Son más baratos y tienen su sitio, pero no son lo que alguien entiende por «envío a
   * domicilio»: ofrecerlos ahí sin decirlo se descubre cuando el paquete no llega.
   */
  aDomicilio: boolean;
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

/**
 * Ciudad y estado del código postal de destino, del catálogo público de Envia.
 *
 * Una cotización por código postal debería bastar, pero varias paqueterías validan la
 * dirección entera antes de dar precio y rechazan la petición si llega a medias. Esto la
 * completa sin pedirle nada más a la persona: el domicilio sigue sin salir de su sobre,
 * porque de un código postal no se deduce una puerta.
 *
 * Si no contesta se sigue adelante con lo que haya: mejor intentar la cotización que no
 * intentarla.
 */
async function lugarDe(
  cp: string,
): Promise<{ ciudad: string; estado: string; colonia: string } | null> {
  try {
    const r = await fetch(`https://geocodes.envia.com/zipcode/MX/${cp}`, {
      signal: AbortSignal.timeout(6000),
    });
    if (!r.ok) return null;
    const filas = await r.json();
    const fila = Array.isArray(filas) ? filas[0] : null;
    const codigo = fila?.state?.code?.["2digit"] ?? "";
    if (!codigo) return null;

    // Una colonia cualquiera de ese código postal. Paquetexpress rechaza la cotización
    // sin ella —«El nombre de la Colonia destino es obligatoria»—, y para dar precio le
    // basta con que sea válida: lo que mueve la tarifa es el código postal. Así se la
    // contenta **sin preguntarle a la persona dónde vive**: no mandamos su colonia,
    // mandamos una del código. La de verdad hará falta al generar la guía, y para
    // entonces la abre la app de control.
    const colonias: string[] = Array.isArray(fila.suburbs) ? fila.suburbs : [];

    return {
      ciudad: fila.locality ?? "",
      estado: codigo,
      colonia: colonias.find((c) => typeof c === "string" && c.trim()) ?? "",
    };
  } catch (_e) {
    return null;
  }
}

/**
 * `n` opciones de una lista ya ordenada por precio, repartidas de punta a punta.
 *
 * Con 1 sale la más barata; con 2, la más barata y la más cara; con más, se reparten
 * entre ambas. Coger «las n más baratas» daría tres sabores de lo mismo y dejaría fuera
 * justo el express, que es el que busca quien tiene prisa.
 */
function repartidas(lista: Opcion[], n: number): Opcion[] {
  if (lista.length <= n) return lista;
  if (n === 1) return [lista[0]];

  const elegidas: Opcion[] = [];
  for (let i = 0; i < n; i++) {
    const posicion = Math.round((i * (lista.length - 1)) / (n - 1));
    const opcion = lista[posicion];
    // Con pocas opciones, dos posiciones pueden caer en la misma.
    if (!elegidas.includes(opcion)) elegidas.push(opcion);
  }
  return elegidas;
}

/**
 * De todas las tarifas, las que vale la pena enseñar.
 *
 * El orden importa: primero se quita lo que no es lo que se pidió —un servicio que
 * termina en sucursal no es envío a domicilio—, y solo después se recorta por precio y
 * por cantidad. Al revés, la lista podría quedarse con opciones baratas que no sirven.
 */
function elegirOpciones(todas: Opcion[]): Opcion[] {
  let lista = [...todas].sort((a, b) => a.precio - b.precio);

  if (SOLO_A_DOMICILIO) lista = lista.filter((o) => o.aDomicilio);

  if (POR_PAQUETERIA > 0) {
    const porNombre = new Map<string, Opcion[]>();
    for (const o of lista) {
      const suyas = porNombre.get(o.paqueteria) ?? [];
      suyas.push(o);
      porNombre.set(o.paqueteria, suyas);
    }
    lista = [...porNombre.values()]
      .flatMap((suyas) => repartidas(suyas, POR_PAQUETERIA))
      .sort((a, b) => a.precio - b.precio);
  }

  if (FACTOR_MAX > 0 && lista.length > 0) {
    const masBarata = lista[0].precio;
    lista = lista.filter((o) => o.precio <= masBarata * FACTOR_MAX);
  }

  return MAX_OPCIONES > 0 ? lista.slice(0, MAX_OPCIONES) : lista;
}

/** Paqueterías descubiertas, mientras esta instancia siga viva. */
let carriersEnMemoria: string[] | null = null;

/**
 * Cómo se llama y cómo se ve cada paquetería, del catálogo de Envia.
 *
 * El nombre bonito —«FedEx», no «fedex»— y el logo, si lo trae. Un logo se reconoce de
 * un vistazo y el nombre en minúsculas parece un error de la página.
 */
const marcas = new Map<string, { nombre: string; logo: string }>();

/** La primera fila del catálogo tal cual, para ver qué campos trae de verdad. */
let catalogoCrudo: unknown = null;

/** El logo, se llame como se llame en el catálogo. */
function logoDe(f: Record<string, unknown>): string {
  for (const clave of ["logo", "image", "img", "icon", "logoUrl", "logo_url", "url"]) {
    const v = f?.[clave];
    if (typeof v === "string" && v.startsWith("http")) return v;
  }
  return "";
}

/**
 * Dónde están los logos de las paqueterías.
 *
 * Admite `{carrier}` en cualquier parte —`.../logos/{carrier}.png`— y, si no lo lleva,
 * se trata como una carpeta y se le añade `/<carrier>.png`.
 *
 * **Vacío = sin logos**, que es el valor por defecto a propósito. Los SVG que sirve
 * Envia colorean con clases CSS y el decodificador de la web no aplica hojas de estilo:
 * salen cuadros negros. Hay que servir PNG desde un bucket propio, y hasta que exista
 * es mejor ninguna imagen que una negra.
 */
const LOGOS_URL = (Deno.env.get("ENVIA_LOGOS_URL") ?? "").trim();

/** La URL del logo de una paquetería. Vacía si no hay de dónde sacarla. */
function logoDeCarrier(clave: string): string {
  const limpia = clave.trim().toLowerCase();
  // Solo letras y números: la clave entra en una URL, y lo que no se valida se cuela.
  if (!limpia || !/^[a-z0-9]+$/.test(limpia) || !LOGOS_URL) return "";

  return LOGOS_URL.includes("{carrier}")
    ? LOGOS_URL.replace(/\{carrier\}/g, limpia)
    : `${LOGOS_URL.replace(/\/+$/, "")}/${limpia}.png`;
}

/**
 * Con qué paqueterías cotizar.
 *
 * `/ship/rate` **exige** una: pedir sin `carrier` devuelve «Required property missing».
 * Así que la lista hay que tenerla, y se le pregunta a Envia en vez de escribirla a mano,
 * que es como se queda vieja.
 */
async function listarCarriers(): Promise<string[]> {
    if (CARRIERS_FIJOS.length > 0) return CARRIERS_FIJOS;
  if (carriersEnMemoria) return carriersEnMemoria;

  try {
    // Se pregunta por **servicios**, no por paqueterías. El catálogo de paqueterías no
    // distingue quién lleva cajas de quién mueve tarimas: ahí salen igual Estafeta y
    // una transportista de carga, y cotizar una caja de 200 g con esta última es una
    // llamada tirada. La lista de servicios sí lo dice.
    const r = await fetch(`${ENVIA_QUERIES}/service?country_code=${PAIS}`, {
      headers: { "Authorization": `Bearer ${ENVIA_TOKEN}` },
      signal: AbortSignal.timeout(8000),
    });
    if (r.ok) {
      const cuerpo = await r.json();
      const filas = Array.isArray(cuerpo?.data) ? cuerpo.data : [];
      catalogoCrudo = filas[0] ?? null;

      const nombres = filas
        .filter((f: Record<string, unknown>) =>
          String(f?.country_code ?? "").toUpperCase() === PAIS &&
          // `service_shipment_type_id` 1 es paquetería; 2 es carga por tarima. Es el
          // mismo `type` que mandamos en la petición, así que pedir el otro sobraba.
          Number(f?.service_shipment_type_id) === TIPO_PAQUETE &&
          Number(f?.active) === 1 &&
          Number(f?.international) === 0
        )
        .map((f: Record<string, unknown>) => {
          const clave = String(f?.carrier_name ?? "").trim().toLowerCase();
          // Envia deja en su catálogo de pruebas unas entradas `_test_tp_carrier`.
          if (clave && !clave.startsWith("_test")) {
            marcas.set(clave, { nombre: "", logo: logoDe(f) });
            return clave;
          }
          return "";
        })
        .filter((n: string) => n.length > 0);

      if (nombres.length > 0) {
        carriersEnMemoria = [...new Set(nombres)].slice(0, MAX_CARRIERS) as string[];
        return carriersEnMemoria;
      }
    }
    console.error("envia /service", r.status, (await r.text()).slice(0, 500));
  } catch (e) {
    console.error("envia /carrier", String(e));
  }

  return CARRIERS_DE_RESPALDO;
}

/**
 * Cotiza con una paquetería.
 *
 * Que una falle es normal y no es un error del carrito: hay rutas que no cubre, y pesos
 * o medidas que no acepta. Por eso devuelve lo que consiguió y el motivo por separado,
 * en vez de cortar la cotización entera.
 */
async function cotizarCon(
  carrier: string,
  base: Record<string, unknown>,
): Promise<
  { carrier: string; opciones: Opcion[]; motivo?: string; ejemplo?: unknown }
> {
  const peticion = {
    ...base,
    shipment: { ...(base.shipment as Record<string, unknown>), carrier },
  };

  try {
    const r = await fetch(`${ENVIA_BASE}/ship/rate/`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${ENVIA_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(peticion),
      signal: AbortSignal.timeout(12000),
    });

    const texto = await r.text().catch(() => "");
    let cuerpo: Record<string, unknown> | null = null;
    try {
      cuerpo = texto ? JSON.parse(texto) : null;
    } catch (_e) {
      cuerpo = null;
    }

    // Envia contesta 200 con `meta: "error"` cuando rechaza los datos: mirar solo el
    // código HTTP daba por buena una respuesta que venía vacía de tarifas.
    const esError = !r.ok || cuerpo?.meta === "error";
    if (esError) {
      const detalle = cuerpo?.error as Record<string, unknown> | undefined;
      return {
        carrier,
        opciones: [],
        motivo: String(detalle?.message ?? detalle?.description ?? r.status),
      };
    }

    const tarifas = Array.isArray(cuerpo?.data)
      ? (cuerpo.data as Record<string, unknown>[])
      : [];
    const opciones = tarifas
      .map((t) => aOpcion(t, carrier))
      .filter((o): o is Opcion => o !== null);

    // Una tarifa tal y como llega, para poder mapear campos que no están documentados
    // sin tener que adivinar dos veces.
    return { carrier, opciones, ...(DEBUG ? { ejemplo: tarifas[0] } : {}) };
  } catch (e) {
    return { carrier, opciones: [], motivo: String(e) };
  }
}

/**
 * Qué mirar según cómo rechazó Envia.
 *
 * Solo aparece con el depurador encendido. No adivina el fallo: dice dónde buscarlo,
 * que es lo que se agradece cuando el cuerpo de la respuesta viene vacío.
 */
function pistaDe(estado: number): string | undefined {
  if (estado === 401 || estado === 403) {
    return "Envia rechazó el token, no la petición. Lo más común es que el token y la " +
      "URL sean de entornos distintos: los de sandbox no sirven en producción ni al " +
      `revés. Ahora mismo se está llamando a ${ENVIA_BASE}, así que ENVIA_TOKEN tiene ` +
      "que ser el de ese entorno. Revisa también que el secret no lleve espacios ni " +
      "comillas alrededor.";
  }
  if (estado === 400 || estado === 422) {
    return "Envia aceptó el token y rechazó los datos. El motivo suele venir en " +
      "`detalle`; mira qué campo nombra.";
  }
  if (estado >= 500) {
    return "El fallo es de Envia, no nuestro. Reintentar más tarde.";
  }
  return undefined;
}

/** Normaliza una tarifa de Envia a lo poco que la pantalla enseña. */
function aOpcion(t: Record<string, unknown>, carrier: string): Opcion | null {
  const precio = Number(t.totalPrice ?? t.total_price ?? t.price ?? NaN);
  if (!Number.isFinite(precio)) return null;

  const clave = String(t.carrier ?? carrier).trim();
  const marca = marcas.get(clave.toLowerCase());

  return {
    // Si la tarifa no se nombra a sí misma, se sabe igual: se pidió a esa paquetería.
    paqueteria: clave,
    // La propia tarifa trae el nombre escrito; el catálogo es el respaldo. Sin ninguno
    // de los dos, la clave con la inicial en mayúscula antes que «fedex» en minúsculas.
    nombre: String(t.carrierDescription ?? marca?.nombre ?? "").trim() ||
      clave.replace(/^./, (c) => c.toUpperCase()),
    // **El bucket propio gana al catálogo.** El catálogo sí trae logo, pero son los
    // SVG de Envia, que colorean con clases CSS y salen en negro. Si hay `ENVIA_LOGOS_URL`
    // configurado es justo para sustituirlos; dejar que el catálogo pisara esa URL
    // devolvería los cuadros negros sin que nadie entendiera por qué.
    logo: logoDeCarrier(clave) || marca?.logo || "",
    servicio: String(t.service ?? "").trim(),
    // Envia devuelve descripciones con espacios de sobra («DHL Economy »).
    descripcion: String(t.serviceDescription ?? t.service_description ?? "").trim(),
    precio,
    moneda: String(t.currency ?? "MXN").trim(),
    dias: diasDe(t),
    // Viene como texto —«2-4 días»—, no como número. Se pasa tal cual: es lo que la
    // paquetería promete y con sus palabras, y un rango dice más que una cifra sola.
    entrega: String(t.deliveryEstimate ?? "").trim(),
    aDomicilio: llegaADomicilio(String(t.service ?? "")),
  };
}

/**
 * Días estimados, de donde Envia los pone de verdad.
 *
 * `deliveryEstimate` es texto («2-4 días») y `deliveryDate` un objeto con la fecha y los
 * días de diferencia. Ninguno de los dos era lo que supuse al principio.
 */
function diasDe(t: Record<string, unknown>): number | null {
  const fecha = t.deliveryDate as Record<string, unknown> | undefined;
  const diferencia = Number(fecha?.dateDifference ?? NaN);
  if (Number.isFinite(diferencia) && diferencia > 0) return Math.round(diferencia);

  // Por si alguna paquetería lo manda como número suelto.
  for (const c of [t.deliveryEstimate, t.deliveryDays, t.delivery_days]) {
    const n = Number(c);
    if (Number.isFinite(n) && n > 0) return Math.round(n);
  }
  return null;
}

/**
 * Si el servicio deja el paquete en la puerta del cliente.
 *
 * Los nombres acaban en dos letras que dicen origen y destino: `o` de «ocurre»
 * —sucursal— y `d` de «domicilio». `ground_od` sale de sucursal y llega a domicilio;
 * `ground_do` sale de domicilio y **termina en sucursal**, con el cliente yendo a
 * recogerlo. Sin sufijo se entiende puerta a puerta, que es como cotiza FedEx.
 *
 * La diferencia no es de matiz: son los servicios más baratos de la lista, y colarlos
 * bajo «envío a domicilio» se descubre el día que el paquete no llega a la casa.
 */
function llegaADomicilio(servicio: string): boolean {
  const sufijo = servicio.toLowerCase().match(/_([od])([od])$/);
  if (!sufijo) return true;
  return sufijo[2] === "d";
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
  const { data: filaOrigen } = await supabase
    .from("locations")
    .select(
      "nombre, contacto, telefono, calle, numero, interior, colonia, ciudad, " +
        "estado_codigo, cp, pais",
    )
    .eq("es_origen", true)
    .maybeSingle();
  const origen = filaOrigen as FilaOrigen | null;

  if (!origen?.cp || !origen?.estado_codigo || !origen?.telefono) {
    return json(
      { error: "Falta configurar la dirección de origen de la tienda." },
      503,
      origin,
    );
  }

  // ---- los pesos: de la base, nunca del navegador ----
  const ids = renglones.map((r) => r.variantId);
  const { data: filasVariantes } = await supabase
    .from("product_variants")
    .select("id, precio_venta, peso_gr, largo_cm, ancho_cm, alto_cm")
    .in("id", ids);
  const variantes = (filasVariantes ?? []) as FilaVariante[];

  const porId = new Map(variantes.map((v) => [v.id, v]));
  if (porId.size !== new Set(ids).size) {
    return json({ error: "Alguno de los artículos ya no está disponible." }, 409, origin);
  }

  const filas = renglones.map((r) => porId.get(r.variantId)!);
  const cantidades = renglones.map((r) => r.cantidad);
  const paquete = armarPaquete(filas, cantidades);

  const valor = renglones.reduce((suma, r) => {
    const precio = Number(porId.get(r.variantId)?.precio_venta ?? 0);
    return suma + precio * r.cantidad;
  }, 0);

  // ---- la petición a Envia ----
  const destino = await lugarDe(cpDestino);

  // `shipment.carrier` lo pone cada llamada: una por paquetería, porque `/ship/rate`
  // exige nombrarla. Lo demás es igual para todas.
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
      // De la persona no viaja nada suyo: ni su nombre, ni su calle, ni su teléfono.
      // Para cotizar basta con saber a qué código postal hay que llegar, y ciudad y
      // estado salen de ese mismo código, no de su dirección.
      name: "Destinatario",
      phone: origen.telefono,
      street: "Domicilio conocido",
      district: destino?.colonia ?? "",
      city: destino?.ciudad ?? "",
      state: destino?.estado ?? "",
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

  // Una llamada por paquetería, todas a la vez: en serie, diez paqueterías serían diez
  // esperas seguidas y nadie aguanta eso mirando un botón.
  const carriers = await listarCarriers();
  const resultados = await Promise.all(carriers.map((c) => cotizarCon(c, peticion)));

  const todas = resultados.flatMap((r) => r.opciones);
  const opciones = elegirOpciones(todas);

  // Que todas fallen sí es un problema; que fallen algunas es lo normal, porque no
  // todas cubren todas las rutas.
  if (opciones.length === 0) {
    const motivos = resultados
      .filter((r) => r.motivo)
      .map((r) => `${r.carrier}: ${r.motivo}`);
    console.error("envia /ship/rate sin opciones", motivos.join(" | ").slice(0, 2000));

    return json(
      {
        error: "No encontramos opciones de envío a ese código postal.",
        // Con el depurador encendido el motivo viaja en la respuesta. Sin esto hay que
        // ir a buscarlo a los registros, que es lo último que apetece cuando lo que
        // falla es justo lo que se está afinando.
        ...(DEBUG ? { carriers, motivos, peticion } : {}),
      },
      502,
      origin,
    );
  }

  return json(
    {
      opciones,
      paquete: { peso: paquete.peso, largo: paquete.largo, ancho: paquete.ancho, alto: paquete.alto },
      // Avisa de que lo cotizado es la caja tope y el bulto real la excede.
      recortado: paquete.recortado,
      ...(DEBUG
        ? {
          carriers,
          // Qué devolvió cada una. Es lo que dice si una paquetería no sirve la ruta o
          // es que el nombre con el que la pedimos no es el suyo.
          porCarrier: resultados.map((r) => ({
            carrier: r.carrier,
            tarifas: r.opciones.length,
            motivo: r.motivo,
          })),
          // Una tarifa cruda, la primera que haya: es lo que falta para nombrar bien el
          // campo de los días de entrega, que Envia no documenta.
          ejemploCrudo: resultados.find((r) => r.ejemplo)?.ejemplo,
          // Una fila del catálogo de paqueterías, para saber si trae logo y con qué
          // nombre lo llama. Sin esto habría que adivinarlo.
          catalogoCrudo,
          // Lo que se descartó al filtrar, para poder ajustar los topes con datos.
          descartadas: todas
            .filter((o) => !opciones.includes(o))
            .map((o) => `${o.paqueteria} ${o.servicio} $${o.precio}` +
              (o.aDomicilio ? "" : " (termina en sucursal)")),
        }
        : {}),
    },
    200,
    origin,
  );
});
