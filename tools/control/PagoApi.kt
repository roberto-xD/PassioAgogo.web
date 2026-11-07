package com.passioagogo.control.pago

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cobros con Mercado Pago, desde la app de control.
 *
 * Para pegar en la app de control, al lado de `EnvioApi`. No trae nada de Mercado Pago
 * dentro: **el access token no puede vivir en un APK**, que se descompila, y con él se
 * pueden crear cobros a nombre de la tienda y consultar los pagos de todos. Lo que hay
 * aquí son llamadas a la Edge Function `crear-pago`, que sí lo tiene.
 *
 * Para qué sirve en la práctica: el cliente puede pagar él mismo desde la web —en el
 * acuse o en su historial—, pero muchos van a cerrar el trato por WhatsApp. [enlaceDeCobro]
 * devuelve la URL de Mercado Pago de una solicitud para pegarla en la conversación.
 *
 * **El monto no se manda ni se puede cambiar desde aquí.** Lo calcula la función leyendo
 * los precios de la base. Si se quiere cobrar otra cosa, se cambia la solicitud.
 *
 * Marcar una solicitud como pagada no se hace desde aquí **y no se puede**: las columnas
 * `pago_*` solo las escribe el webhook, con `service_role`, después de comprobar la firma
 * del aviso y de volver a preguntarle a Mercado Pago cuánto se pagó. Si un pago llegó
 * por fuera —efectivo en mano, transferencia directa—, eso no es un cobro de Mercado
 * Pago y registrarlo como tal haría que los números dejaran de cuadrar con su panel.
 */
class PagoApi(private val supabase: SupabaseClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * El enlace de pago de una solicitud, para mandarlo por WhatsApp.
     *
     * Llamarlo dos veces para el mismo monto devuelve el mismo cobro, no dos: la función
     * usa una llave de idempotencia hecha con la solicitud y el importe. Así que se puede
     * volver a pedir el enlace sin miedo a abrir un segundo cargo.
     *
     * Si la solicitud ya está pagada devuelve [ResultadoCobro.Fallo]: cobrar dos veces lo
     * mismo es el error que más cuesta deshacer.
     */
    suspend fun enlaceDeCobro(solicitudId: String): ResultadoCobro {
        // La función exige sesión de administración. Sin ella devolvería un 401 que aquí
        // se leería como un fallo cualquiera, y el motivo real —la sesión caducó— es lo
        // único que hace falta saber para arreglarlo.
        if (supabase.auth.currentSessionOrNull() == null) {
            return ResultadoCobro.Fallo("No hay sesión abierta. Vuelve a entrar.")
        }

        return try {
            val respuesta = supabase.functions("crear-pago") {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(PeticionCobro.serializer(), PeticionCobro(solicitudId))
                )
            }
            // El cuerpo se lee siempre, también en los errores: el motivo va dentro y no
            // en el código de estado.
            val cuerpo = json.decodeFromString<RespuestaCobro>(respuesta.bodyAsText())
            when {
                !cuerpo.checkoutUrl.isNullOrBlank() ->
                    ResultadoCobro.Listo(cuerpo.checkoutUrl, cuerpo.total.orEmpty())
                // Los precios se movieron desde que se armó la solicitud. La función no
                // cobra por su cuenta ni lo de antes ni lo de ahora: lo dice y para.
                cuerpo.estimado != null && cuerpo.ahora != null -> ResultadoCobro.Fallo(
                    "${cuerpo.error.orEmpty()} Decía ${cuerpo.estimado} y el catálogo " +
                        "dice ${cuerpo.ahora}."
                )
                else -> ResultadoCobro.Fallo(cuerpo.error ?: "No se pudo abrir el cobro.")
            }
        } catch (e: Exception) {
            ResultadoCobro.Fallo("No se pudo conectar: ${e.message ?: "error desconocido"}")
        }
    }

    /**
     * El historial de avisos de pago de una solicitud, del más reciente al más viejo.
     *
     * Lee `pagos` directo, sin función: la tabla ya deja leer a administración por RLS.
     * Es lo que hay que mirar cuando un cobro no cuadra — `estadoCrudo` trae las palabras
     * de Mercado Pago sin traducir, que son las que dicen *por qué* se rechazó una
     * tarjeta.
     */
    suspend fun historial(solicitudId: String): List<EventoPago> = try {
        supabase.from("pagos")
            .select(
                Columns.raw(
                    "accion, orden_id, pago_id, monto, neto, comisiones, metodo, " +
                        "estado_crudo, usuario, nota, created_at"
                )
            ) {
                filter { eq("request_id", solicitudId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<EventoPago>()
    } catch (_: Exception) {
        emptyList()
    }
}

@Serializable
private data class PeticionCobro(val solicitudId: String)

@Serializable
private data class RespuestaCobro(
    val checkoutUrl: String? = null,
    val ordenId: String? = null,
    val total: String? = null,
    val error: String? = null,
    val estimado: String? = null,
    val ahora: String? = null,
)

sealed interface ResultadoCobro {
    /** [checkoutUrl] es lo que se pega en WhatsApp. */
    data class Listo(val checkoutUrl: String, val total: String) : ResultadoCobro
    data class Fallo(val mensaje: String) : ResultadoCobro
}

/** Un aviso de pago, tal como lo guardó el webhook (39_pagos.sql). */
@Serializable
data class EventoPago(
    val accion: String = "",
    @SerialName("orden_id") val ordenId: String? = null,
    @SerialName("pago_id") val pagoId: String? = null,
    val monto: Double? = null,
    /**
     * Lo que Mercado Pago acredita, ya sin su comisión.
     *
     * Nulo mientras no lo sepamos: en un pago en efectivo sin pagar no hay neto, y en
     * uno rechazado tampoco. La comisión es `monto - neto`, y en `order_requests` está
     * ya calculada en `pago_comision`.
     */
    val neto: Double? = null,
    /** El desglose crudo de Mercado Pago: `[{type, amount, fee_payer}]`. */
    val comisiones: kotlinx.serialization.json.JsonElement? = null,
    val metodo: String? = null,
    /** Las palabras de Mercado Pago, sin traducir: `accredited`, `cc_rejected_*`… */
    @SerialName("estado_crudo") val estadoCrudo: String? = null,
    val usuario: String? = null,
    val nota: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)
