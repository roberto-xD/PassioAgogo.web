// ============================================================
// Edge Function · notify-admins
// Passion à gogo · Supabase
//
// La dispara un Database Webhook en cada INSERT de order_requests
// y contact_messages. Busca los tokens FCM de todos los admin
// activos y les envía una notificación.
//
// También en UPDATE de order_requests, para las cancelaciones del
// cliente (51_cancelacion.sql). Hay que marcar la casilla UPDATE en
// el webhook de order_requests; sin eso, una cancelación no avisa a
// nadie y el pedido se queda esperando a que alguien mire la lista.
//
// Con UPDATE marcado esta función se llama en **cada** escritura sobre
// la tabla: cada aviso de Mercado Pago, cada guía generada. Por eso
// `construirMensaje` compara con `old_record` y corta antes de tocar la
// base: lo que no es una transición de cancelación sale por `skipped`
// sin haber consultado nada.
//
// Desplegar:
//   supabase functions deploy notify-admins --no-verify-jwt
//
// Secretos necesarios (Project Settings → Edge Functions → Secrets):
//   FIREBASE_PROJECT_ID     · id del proyecto de Firebase
//   FIREBASE_CLIENT_EMAIL   · client_email del JSON de cuenta de servicio
//   FIREBASE_PRIVATE_KEY    · private_key del mismo JSON (con los \n)
//   SUPABASE_URL            · ya viene definido por la plataforma
//   SUPABASE_SERVICE_ROLE_KEY · ya viene definido por la plataforma
//
// --no-verify-jwt es necesario: el webhook no manda token de usuario.
// ============================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// ------------------------------------------------------------
// OAuth2 para FCM HTTP v1
//
// FCM v1 no acepta una API key simple: exige un access token
// obtenido firmando un JWT con la cuenta de servicio de Firebase.
// ------------------------------------------------------------
function pemToBinary(pem: string): ArrayBuffer {
  // El secreto llega de formas distintas según cómo se copie del JSON:
  //   · con \n escapados (lo habitual al copiar el valor)
  //   · con saltos de línea reales (al pegar en un campo multilínea)
  //   · envuelto en comillas dobles (al copiar la línea completa)
  // Las comillas no son espacios: si no se quitan, atob recibe base64
  // corrupto y falla con "incorrect length for PRIVATE".
  const limpio = pem
    .trim()
    .replace(/^["']|["']$/g, "")
    .replace(/\\n/g, "\n")
    .replace(/-----BEGIN [A-Z ]*PRIVATE KEY-----/, "")
    .replace(/-----END [A-Z ]*PRIVATE KEY-----/, "")
    .replace(/\s/g, "");

  if (limpio.length === 0) {
    throw new Error("FIREBASE_PRIVATE_KEY vacía o sin cabecera PEM válida");
  }

  const binario = atob(limpio);
  const bytes = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i++) bytes[i] = binario.charCodeAt(i);
  return bytes.buffer;
}

function base64url(data: string | Uint8Array): string {
  const texto = typeof data === "string"
    ? btoa(data)
    : btoa(String.fromCharCode(...data));
  return texto.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function getAccessToken(): Promise<string> {
  const clientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL")!;
  // El secreto guarda los saltos de línea escapados: hay que restaurarlos.
  // pemToBinary normaliza el formato; aquí solo se comprueba que exista.
  const privateKey = Deno.env.get("FIREBASE_PRIVATE_KEY");
  if (!privateKey) throw new Error("Falta el secreto FIREBASE_PRIVATE_KEY");

  const ahora = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: clientEmail,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: ahora + 3600,
    iat: ahora,
  };

  const sinFirma = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claim))}`;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBinary(privateKey),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const firma = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(sinFirma),
  );
  const jwt = `${sinFirma}.${base64url(new Uint8Array(firma))}`;

  const respuesta = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  const datos = await respuesta.json();
  if (!datos.access_token) {
    throw new Error(`No se obtuvo access token: ${JSON.stringify(datos)}`);
  }
  return datos.access_token;
}

// ------------------------------------------------------------
// Contenido y destinatarios según la tabla que disparó el webhook
//
// `locationId` indica a qué ubicación avisar además de a los admin.
// null = solo administración.
// ------------------------------------------------------------
interface Aviso {
  titulo: string;
  cuerpo: string;
  data: Record<string, string>;
  locationId: string | null;
}

/** ¿Esta columna acaba de pasar de vacía a tener algo? */
export function seAcabaDePoner(
  anterior: Record<string, unknown> | null,
  actual: Record<string, unknown>,
  columna: string,
): boolean {
  return !anterior?.[columna] && Boolean(actual[columna]);
}

export function construirMensaje(
  table: string,
  record: Record<string, unknown>,
  anterior: Record<string, unknown> | null = null,
): Aviso | null {
  if (table === "order_requests") {
    // Una solicitud nueva: no hay fila anterior con la que comparar.
    if (!anterior) {
      return {
        titulo: "Nueva solicitud de pedido",
        cuerpo: `#${record.folio} · ${record.cliente_nombre}`,
        data: { tipo: "solicitud", id: String(record.id ?? "") },
        locationId: null,
      };
    }

    // Pagada y el cliente pide cancelar. Es el aviso que más corre: hasta que
    // alguien lo vea, hay dinero cobrado de un pedido que no se va a preparar.
    if (seAcabaDePoner(anterior, record, "cancelacion_solicitada_at")) {
      const cobrado = Number(record.pago_total ?? 0);
      return {
        titulo: "Cancelación pedida · hay que devolver",
        cuerpo: `#${record.folio} · ${record.cliente_nombre}` +
          (cobrado > 0 ? ` · $${cobrado.toFixed(2)}` : ""),
        data: {
          tipo: "cancelacion_pedida",
          id: String(record.id ?? ""),
          folio: String(record.folio ?? ""),
        },
        locationId: null,
      };
    }

    // Cancelada sin pago de por medio. No hay nada que devolver, así que esto no
    // es una tarea: es saber que ese pedido ya no va.
    //
    // Solo la del cliente. Cuando cancela la tienda —`reembolsar` escribe
    // `cancelada_at` al devolver el dinero— avisar sería contarle a quien acaba
    // de pulsar el botón lo que hizo al pulsarlo.
    if (
      seAcabaDePoner(anterior, record, "cancelada_at") &&
      record.cancelada_por === "cliente" &&
      !record.reembolso_at
    ) {
      return {
        titulo: "Solicitud cancelada por el cliente",
        cuerpo: `#${record.folio} · ${record.cliente_nombre}`,
        data: {
          tipo: "cancelacion",
          id: String(record.id ?? ""),
          folio: String(record.folio ?? ""),
        },
        locationId: null,
      };
    }

    // Cualquier otra escritura sobre la tabla —un aviso de pago, una guía— no es
    // noticia para nadie.
    return null;
  }

  if (table === "contact_messages") {
    return {
      titulo: "Nuevo mensaje de contacto",
      cuerpo: `${record.nombre}: ${String(record.mensaje ?? "").slice(0, 80)}`,
      data: { tipo: "contacto", id: String(record.id ?? "") },
      locationId: null,
    };
  }

  if (table === "transfer_requests") {
    // Avisa a quien debe autorizar, que es la contraparte de quien pidió:
    //   pedido     → la ubicación de origen (null = decide administración)
    //   devolucion → la ubicación de destino
    const esPedido = record.tipo === "pedido";
    const destino = esPedido
      ? (record.from_location_id as string | null)
      : (record.to_location_id as string | null);

    return {
      titulo: esPedido
        ? "Nueva solicitud de mercancía"
        : "Nueva solicitud de devolución",
      cuerpo: `#${record.folio} · requiere tu autorización`,
      data: { tipo: "transferencia", id: String(record.id ?? "") },
      locationId: destino,
    };
  }

  return null;
}

Deno.serve(async (req) => {
  try {
    const payload = await req.json();
    const table: string = payload.table;
    const record: Record<string, unknown> = payload.record ?? {};
    // En un INSERT el webhook manda `old_record: null`. Es lo que distingue una
    // solicitud nueva de una que acaba de cambiar.
    const anterior: Record<string, unknown> | null = payload.old_record ?? null;

    const mensaje = construirMensaje(table, record, anterior);
    if (!mensaje) {
      // Se corta aquí, antes de abrir cliente y consultar perfiles: con UPDATE
      // marcado la mayoría de las llamadas llegan a esta línea.
      return new Response(
        JSON.stringify({ skipped: table, evento: payload.type ?? null }),
        { status: 200 },
      );
    }

    // service_role: ignora RLS, necesario para leer tokens ajenos
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // Los admin siempre reciben: supervisan todo el movimiento.
    const { data: admins, error: errorAdmins } = await supabase
      .from("profiles")
      .select("id")
      .eq("rol", "admin")
      .eq("activo", true);
    if (errorAdmins) throw errorAdmins;

    const destinatarios = new Set<string>((admins ?? []).map((a) => a.id));

    // Y además el personal de la ubicación que debe responder.
    if (mensaje.locationId) {
      const { data: locales, error: errorLocales } = await supabase
        .from("profiles")
        .select("id")
        .eq("location_id", mensaje.locationId)
        .eq("rol", "vendedor")
        .eq("activo", true);
      if (errorLocales) throw errorLocales;
      for (const p of locales ?? []) destinatarios.add(p.id);
    }

    if (destinatarios.size === 0) {
      return new Response(
        JSON.stringify({ enviados: 0, motivo: "sin destinatarios" }),
        { status: 200 },
      );
    }

    const { data: dispositivos, error: errorDisp } = await supabase
      .from("user_devices")
      .select("fcm_token")
      .in("profile_id", [...destinatarios]);
    if (errorDisp) throw errorDisp;
    if (!dispositivos?.length) {
      return new Response(
        JSON.stringify({ enviados: 0, motivo: "sin dispositivos" }),
        { status: 200 },
      );
    }

    const accessToken = await getAccessToken();
    const projectId = Deno.env.get("FIREBASE_PROJECT_ID")!;
    const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

    let enviados = 0;
    const invalidos: string[] = [];

    for (const { fcm_token } of dispositivos) {
      const respuesta = await fetch(url, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: fcm_token,
            notification: { title: mensaje.titulo, body: mensaje.cuerpo },
            data: mensaje.data,
            android: {
              priority: "HIGH",
              notification: { channel_id: "passion_alertas" },
            },
          },
        }),
      });

      if (respuesta.ok) {
        enviados++;
      } else {
        // 404/400 = token muerto (app desinstalada, token rotado)
        if (respuesta.status === 404 || respuesta.status === 400) {
          invalidos.push(fcm_token);
        }
        console.error("FCM error", respuesta.status, await respuesta.text());
      }
    }

    // Limpieza: si no se borran, cada envío los reintenta para siempre
    if (invalidos.length) {
      await supabase.from("user_devices").delete().in("fcm_token", invalidos);
    }

    return new Response(
      JSON.stringify({ enviados, descartados: invalidos.length }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    );
  } catch (e) {
    console.error(e);
    return new Response(JSON.stringify({ error: String(e) }), { status: 500 });
  }
});