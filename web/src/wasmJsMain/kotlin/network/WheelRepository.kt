package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import models.SiteSettingDto
import models.SpinOutcome
import models.WheelPrize
import models.WheelPrizeDto
import models.WheelTerm
import models.WheelTermDto

/**
 * Lo que necesita la pantalla: si la sección está encendida, qué sectores dibujar y las
 * bases de la promoción.
 */
data class WheelBundle(
    val visible: Boolean = false,
    val prizes: List<WheelPrize> = emptyList(),
    val terms: List<WheelTerm> = emptyList(),
)

@Serializable
private data class SpinResponse(
    val ok: Boolean = false,
    val yaGiro: Boolean = false,
    val prizeId: String? = null,
    val rotulo: String? = null,
    val detalle: String? = null,
    val codigo: String? = null,
    val otorgaPremio: Boolean = false,
    val error: String? = null,
)

/** El giro salió bien, o hay un motivo que contarle a la persona. */
sealed interface SpinResult {
    data class Ganado(val outcome: SpinOutcome) : SpinResult
    data class Fallo(val mensaje: String) : SpinResult
}

/**
 * Ruleta de descuentos.
 *
 * Los sectores se leen de `wheel_prizes` para dibujarlos, pero **el premio lo decide la
 * Edge Function `spin`**, no esta clase. Aquí no hay ningún sorteo: si lo hubiera,
 * bastaría recargar la página para volver a tirar hasta que saliera el mejor.
 */
class WheelRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
    private val client: HttpClient = HttpClient(Js),
) {
    private val supabase: SupabaseClient by lazy(supabaseProvider)
    private val json = Json { ignoreUnknownKeys = true }

    /** Interruptor y sectores activos, en el orden en que se dibujan. */
    fun getWheel(): Flow<NetworkResult<WheelBundle>> = toResultFlow {
        if (!SupabaseConfig.isConfigured) {
            return@toResultFlow NetworkResult.Success(WheelBundle())
        }

        val visible = supabase.from(SupabaseConfig.SITE_SETTINGS_TABLE)
            .select(columns = Columns.raw("clave, valor")) {
                filter { eq("clave", SupabaseConfig.SETTING_WHEEL) }
            }
            .decodeList<SiteSettingDto>()
            .firstOrNull()
            ?.valor
            ?.trim()
            ?.lowercase()
            // Sin la fila, la sección no se muestra: un ajuste sin configurar debe dejar
            // el sitio como estaba.
            .let { it == "true" }

        val prizes = supabase.from(SupabaseConfig.WHEEL_PRIZES_TABLE)
            .select(columns = Columns.raw("id, rotulo, detalle, orden")) {
                order("orden", Order.ASCENDING)
            }
            .decodeList<WheelPrizeDto>()
            .mapNotNull { dto ->
                val id = dto.id ?: return@mapNotNull null
                val rotulo = dto.rotulo?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WheelPrize(id = id, rotulo = rotulo.trim(), detalle = dto.detalle.orEmpty().trim())
            }

        // Las bases van aparte y toleran el fallo: si la tabla todavía no existe —el
        // script 25 no se ha corrido— la ruleta debe seguir girando sin ellas, no caerse
        // entera. Lo único que se pierde es el botón de "Bases de la promoción".
        val terms = runCatching {
            supabase.from(SupabaseConfig.WHEEL_TERMS_TABLE)
                .select(columns = Columns.raw("id, titulo, cuerpo, orden")) {
                    order("orden", Order.ASCENDING)
                }
                .decodeList<WheelTermDto>()
                .mapNotNull { dto ->
                    val id = dto.id ?: return@mapNotNull null
                    val titulo = dto.titulo?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val cuerpo = dto.cuerpo?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    WheelTerm(id = id, titulo = titulo.trim(), cuerpo = cuerpo.trim())
                }
        }.getOrDefault(emptyList())

        NetworkResult.Success(WheelBundle(visible = visible, prizes = prizes, terms = terms))
    }

    /** Pide un giro. El resultado ya viene decidido y registrado del servidor. */
    suspend fun spin(): SpinResult {
        if (!SupabaseConfig.isConfigured) {
            return SpinResult.Fallo("La ruleta aún no está configurada.")
        }

        return try {
            val response = client.post("${SupabaseConfig.URL}/functions/v1/spin") {
                header(HttpHeaders.Authorization, "Bearer ${SupabaseConfig.ANON_KEY}")
                header("apikey", SupabaseConfig.ANON_KEY)
                contentType(ContentType.Application.Json)
            }

            val body = runCatching {
                json.decodeFromString(SpinResponse.serializer(), response.bodyAsText())
            }.getOrNull()

            when {
                response.status.isSuccess() && body?.ok == true && body.rotulo != null ->
                    SpinResult.Ganado(
                        SpinOutcome(
                            prizeId = body.prizeId.orEmpty(),
                            rotulo = body.rotulo,
                            detalle = body.detalle.orEmpty(),
                            codigo = body.codigo.orEmpty(),
                            otorgaPremio = body.otorgaPremio,
                            yaGiro = body.yaGiro,
                        )
                    )
                body?.error == "sin-premios" ->
                    SpinResult.Fallo("La ruleta no tiene premios disponibles ahora mismo.")
                else -> SpinResult.Fallo(
                    "No se pudo girar la ruleta (código ${response.status.value})."
                )
            }
        } catch (e: Exception) {
            SpinResult.Fallo("No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.")
        }
    }
}
