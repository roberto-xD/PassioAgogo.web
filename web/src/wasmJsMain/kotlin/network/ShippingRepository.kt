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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import models.CartItem
import models.CotizacionDto
import models.OpcionEnvio

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class RenglonDto(val variantId: String, val cantidad: Int)

@Serializable
private data class PeticionDto(val cpDestino: String, val items: List<RenglonDto>)

/** Salió la cotización, o hay un motivo que contarle a la persona. */
sealed interface CotizacionResult {
    data class Lista(val opciones: List<OpcionEnvio>, val recortado: Boolean) : CotizacionResult
    data class Fallo(val mensaje: String) : CotizacionResult
}

/**
 * Cotización de envío contra la Edge Function `cotizar-envio`.
 *
 * **Aquí no se manda ni el peso ni el precio de nada.** Solo qué variantes y cuántas: la
 * función busca los pesos en la base. Si los pusiera el navegador, bastaría con editar la
 * petición para declarar un gramo y pagar el envío más barato.
 *
 * Del domicilio solo viaja el código postal, y porque sin él no hay a dónde cotizar.
 */
class ShippingRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
    private val client: HttpClient = HttpClient(Js),
) {
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    suspend fun cotizar(cpDestino: String, items: List<CartItem>): CotizacionResult {
        if (!SupabaseConfig.isConfigured) {
            return CotizacionResult.Fallo("La cotización de envíos aún no está configurada.")
        }
        val cp = cpDestino.filter { it.isDigit() }
        if (cp.length != 5) {
            return CotizacionResult.Fallo(
                "Necesitamos tu código postal para calcular el envío."
            )
        }
        if (items.isEmpty()) return CotizacionResult.Fallo("No hay nada que enviar.")

        // Con la sesión, no con la llave anónima. La función la exige: con la anónima
        // —que es pública— cualquiera podía pedir cotizaciones en bucle, y cada una se
        // abre en abanico contra Envia. No estorba a nadie: para cotizar hace falta una
        // dirección, y para tenerla hace falta cuenta.
        val token = supabase.auth.currentSessionOrNull()?.accessToken
            ?: return CotizacionResult.Fallo(
                "Vuelve a entrar para calcular el envío."
            )

        return runCatching {
            val respuesta = client.post("${SupabaseConfig.URL}/functions/v1/cotizar-envio") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("apikey", SupabaseConfig.ANON_KEY)
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        PeticionDto.serializer(),
                        PeticionDto(
                            cpDestino = cp,
                            items = items.map { RenglonDto(it.variantId, it.cantidad) },
                        ),
                    )
                )
            }

            // El cuerpo se lee siempre: cuando la función rechaza, el motivo va dentro,
            // no en el código de estado.
            val cuerpo = json.decodeFromString<CotizacionDto>(respuesta.bodyAsText())
            when {
                cuerpo.error != null -> CotizacionResult.Fallo(cuerpo.error)
                cuerpo.opciones.isEmpty() -> CotizacionResult.Fallo(
                    "No encontramos opciones de envío a tu código postal. " +
                        "Escríbenos y lo vemos."
                )
                else -> CotizacionResult.Lista(cuerpo.opciones, cuerpo.recortado)
            }
        }.getOrElse {
            CotizacionResult.Fallo("No se pudo calcular el envío. Inténtalo de nuevo.")
        }
    }
}
