package network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * Lo que devuelve la función `geocode`, que ya viene masticado: la forma es nuestra, no
 * la de Envia, y solo trae lo que la pantalla usa.
 */
@Serializable
private data class LugarDto(
    val cp: String? = null,
    val ciudad: String? = null,
    val estado: String? = null,
    val estadoCodigo: String? = null,
    val colonias: List<String> = emptyList(),
)

/** Lo que un código postal dice de sí mismo. */
data class LugarDelCp(
    val cp: String,
    val ciudad: String,
    val estado: String,
    /** El de dos letras, que es el que viaja a Envia: CDMX es «CX», no «CDMX». */
    val estadoCodigo: String,
    val colonias: List<String>,
)

/**
 * Resuelve un código postal mexicano en ciudad, estado y sus colonias.
 *
 * Existe para que la dirección se escriba como la paquetería la entiende. Un campo de
 * texto libre invita a escribir «CDMX», «D.F.» o «Ciudad de Mexico» para el mismo sitio,
 * y Envia espera un código de dos letras; esa diferencia no se nota al capturar, se nota
 * el día que la guía se rechaza y el paquete no sale.
 *
 * Va contra la Edge Function `geocode` y no contra Envia directamente. El catálogo de
 * Envia es público y el navegador podría consultarlo solo, pero entonces Envia vería la
 * IP de cada cliente y el código postal que teclea, incluso el de quien nunca llega a
 * comprar. Por la función solo ven el servidor.
 *
 * Nunca estorba: si la función no contesta o el código no existe, devuelve `null` y la
 * dirección se sigue escribiendo a mano. Es una ayuda, no un requisito.
 */
class GeocodeRepository(
    private val client: HttpClient = HttpClient(Js),
) {
    /** Lo ya consultado en esta pestaña. Teclear y borrar un dígito no vuelve a preguntar. */
    private val cache = mutableMapOf<String, LugarDelCp?>()

    suspend fun consultar(cp: String): LugarDelCp? {
        val limpio = cp.filter { it.isDigit() }
        if (limpio.length != 5) return null
        if (cache.containsKey(limpio)) return cache[limpio]
        if (!SupabaseConfig.isConfigured) return null

        val lugar = runCatching {
            val respuesta = client.get(
                "${SupabaseConfig.URL}/functions/v1/geocode?cp=$limpio"
            ) {
                header(HttpHeaders.Authorization, "Bearer ${SupabaseConfig.ANON_KEY}")
                header("apikey", SupabaseConfig.ANON_KEY)
            }
            if (!respuesta.status.isSuccess()) return@runCatching null

            // Un código que no existe contesta 200 con `{"encontrado": false}`: sin
            // código de estado, que es lo único que no se puede suplir a mano.
            val fila = json.decodeFromString<LugarDto>(respuesta.bodyAsText())
            val codigo = fila.estadoCodigo.orEmpty()
            if (codigo.isBlank()) return@runCatching null

            LugarDelCp(
                cp = fila.cp ?: limpio,
                ciudad = fila.ciudad.orEmpty(),
                estado = fila.estado.orEmpty(),
                estadoCodigo = codigo,
                colonias = fila.colonias.filter { it.isNotBlank() },
            )
        }.getOrNull()

        // Se recuerda también el fallo: un código que no existe no existirá al siguiente
        // dígito tampoco, y repreguntar por cada tecla sería castigar a quien se equivoca.
        cache[limpio] = lugar
        return lugar
    }
}
