package network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ContactRequest(
    val nombre: String,
    val email: String,
    val mensaje: String,
    val token: String,
)

@Serializable
private data class ContactResponse(
    val ok: Boolean = false,
    val error: String? = null,
)

/**
 * Envía el formulario de contacto a la Edge Function `contact`.
 *
 * El mensaje **no se inserta desde el navegador**: la función verifica el token de
 * Turnstile y aplica límite de envíos antes de escribir con `service_role`. La tabla
 * `contact_messages` no tiene política de INSERT, así que un cliente con la anon key
 * no puede saltarse ese camino.
 *
 * Se usa Ktor directamente (en vez del módulo functions-kt) para no añadir otra
 * dependencia: la llamada es un POST con la anon key, que es lo que exige el gateway
 * de Supabase.
 */
class ContactRepository(
    private val client: HttpClient = HttpClient(Js),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Devuelve un mensaje de error legible, o `null` si el envío fue correcto. */
    suspend fun send(request: ContactRequest): String? {
        if (!SupabaseConfig.isConfigured) {
            return "El formulario aún no está configurado."
        }

        return try {
            val response = client.post("${SupabaseConfig.URL}/functions/v1/contact") {
                header(HttpHeaders.Authorization, "Bearer ${SupabaseConfig.ANON_KEY}")
                header("apikey", SupabaseConfig.ANON_KEY)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ContactRequest.serializer(), request))
            }

            val body = runCatching {
                json.decodeFromString(ContactResponse.serializer(), response.bodyAsText())
            }.getOrNull()

            when {
                response.status.isSuccess() && body?.ok == true -> null
                response.status.value == 429 ->
                    "Demasiados envíos desde esta conexión. Inténtalo más tarde."
                else -> body?.error?.let(::friendlyError)
                    ?: "No se pudo enviar el mensaje (código ${response.status.value})."
            }
        } catch (e: Exception) {
            "No se pudo conectar. Revisa tu conexión e inténtalo de nuevo."
        }
    }

    private fun friendlyError(code: String): String = when (code) {
        "captcha-invalido" -> "No pudimos verificar que no eres un robot. Inténtalo de nuevo."
        "datos-invalidos" -> "Revisa los datos del formulario."
        else -> "No se pudo enviar el mensaje. Inténtalo de nuevo."
    }
}
