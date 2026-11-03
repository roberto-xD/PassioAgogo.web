package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class PeticionPago(@SerialName("solicitudId") val solicitudId: String)

@Serializable
private data class RespuestaPago(
    val checkoutUrl: String? = null,
    val ordenId: String? = null,
    val total: String? = null,
    val error: String? = null,
    /** Lo que decía la solicitud y lo que dice el catálogo ahora, cuando no cuadran. */
    val estimado: String? = null,
    val ahora: String? = null,
)

/** Hay a dónde mandar a la persona, o hay un motivo que contarle. */
sealed interface PagoResult {
    /** La URL de Mercado Pago. Lo único que queda es llevar el navegador allí. */
    data class Listo(val checkoutUrl: String) : PagoResult
    data class Fallo(val mensaje: String) : PagoResult
    data object SinSesion : PagoResult
}

/**
 * Abre un cobro con Mercado Pago contra la Edge Function `crear-pago`.
 *
 * **Aquí no se manda ningún monto.** Solo el id de la solicitud: la función suma los
 * precios leyéndolos de la base. Si el importe lo pusiera el navegador, bastaría con
 * editar la petición para pagar un peso.
 *
 * Tampoco se manda la dirección: Mercado Pago no la necesita para cobrar, y sigue
 * cifrada extremo a extremo.
 */
class PaymentsRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
    private val client: HttpClient = HttpClient(Js),
) {
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    suspend fun abrirCobro(solicitudId: String): PagoResult {
        if (!SupabaseConfig.isConfigured) {
            return PagoResult.Fallo("El pago en línea aún no está configurado.")
        }
        if (solicitudId.isBlank()) {
            return PagoResult.Fallo("No sabemos qué solicitud cobrar.")
        }

        // La función exige la sesión de la persona, no la llave anónima: lo que se
        // comprueba allí es que la solicitud sea suya.
        val token = supabase.auth.currentSessionOrNull()?.accessToken
            ?: return PagoResult.SinSesion

        return runCatching {
            val respuesta = client.post("${SupabaseConfig.URL}/functions/v1/crear-pago") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("apikey", SupabaseConfig.ANON_KEY)
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        PeticionPago.serializer(),
                        PeticionPago(solicitudId),
                    )
                )
            }

            // El cuerpo se lee siempre: cuando la función rechaza, el motivo va dentro y
            // no en el código de estado.
            val cuerpo = json.decodeFromString<RespuestaPago>(respuesta.bodyAsText())
            val url = cuerpo.checkoutUrl
            when {
                !url.isNullOrBlank() -> PagoResult.Listo(url)
                cuerpo.error != null -> PagoResult.Fallo(cuerpo.error)
                else -> PagoResult.Fallo("No pudimos abrir el pago. Inténtalo de nuevo.")
            }
        }.getOrElse {
            PagoResult.Fallo("No se pudo conectar con el pago. Inténtalo de nuevo.")
        }
    }
}
