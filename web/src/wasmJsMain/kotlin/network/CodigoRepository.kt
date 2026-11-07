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

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class PeticionCodigo(val codigo: String)

@Serializable
private data class RespuestaCodigo(
    val valido: Boolean = false,
    val motivo: String? = null,
    val pct: Int? = null,
    val rotulo: String? = null,
    val error: String? = null,
)

/** El código vale, o hay un motivo que contarle a la persona. */
sealed interface CodigoResult {
    data class Vale(val codigo: String, val pct: Int, val rotulo: String) : CodigoResult
    data class NoVale(val motivo: String) : CodigoResult
    data object SinSesion : CodigoResult
}

/**
 * Comprueba un código de la ruleta contra `validar-codigo`.
 *
 * **Esto es para enseñar, no para cobrar.** Lo que diga aquí solo sirve para que la
 * persona vea su total antes de pulsar «Pagar»; quien aplica el descuento de verdad es
 * `crear-pago`, que vuelve a validarlo por su cuenta. Si esta respuesta se manipulara,
 * el cobro saldría igual de bien: el navegador manda el código, nunca el importe.
 */
class CodigoRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
    private val client: HttpClient = HttpClient(Js),
) {
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    suspend fun validar(codigo: String): CodigoResult {
        val limpio = codigo.trim().uppercase()
        if (limpio.isBlank()) return CodigoResult.NoVale("Escribe tu código.")
        if (!SupabaseConfig.isConfigured) {
            return CodigoResult.NoVale("Los códigos aún no están configurados.")
        }

        val token = supabase.auth.currentSessionOrNull()?.accessToken
            ?: return CodigoResult.SinSesion

        return runCatching {
            val respuesta = client.post("${SupabaseConfig.URL}/functions/v1/validar-codigo") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("apikey", SupabaseConfig.ANON_KEY)
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(PeticionCodigo.serializer(), PeticionCodigo(limpio))
                )
            }

            // El cuerpo se lee siempre: el motivo va dentro, no en el código de estado.
            val cuerpo = json.decodeFromString<RespuestaCodigo>(respuesta.bodyAsText())
            when {
                cuerpo.valido && (cuerpo.pct ?: 0) > 0 ->
                    CodigoResult.Vale(limpio, cuerpo.pct!!, cuerpo.rotulo.orEmpty())
                cuerpo.motivo != null -> CodigoResult.NoVale(cuerpo.motivo)
                cuerpo.error != null -> CodigoResult.NoVale(cuerpo.error)
                else -> CodigoResult.NoVale("No pudimos comprobar ese código.")
            }
        }.getOrElse {
            CodigoResult.NoVale("No pudimos comprobar ese código. Inténtalo de nuevo.")
        }
    }
}
