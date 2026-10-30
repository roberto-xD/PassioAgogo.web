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

/**
 * Cómo fue la consulta.
 *
 * Importa separar «ese código no existe» de «no pudimos preguntar». Lo primero es culpa
 * de quien teclea y se arregla corrigiendo un dígito; lo segundo no es culpa suya y no
 * se arregla reescribiendo nada. Decirle lo primero cuando pasa lo segundo lo manda a
 * revisar un dato que estaba bien.
 */
sealed interface ResultadoCp {
    data class Encontrado(val lugar: LugarDelCp) : ResultadoCp
    /** Contestó, y ese código postal no existe. */
    data object NoExiste : ResultadoCp
    /** No se pudo preguntar: sin red, función caída, origen no permitido. */
    data object NoDisponible : ResultadoCp
}

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
    private val cache = mutableMapOf<String, ResultadoCp>()

    suspend fun consultar(cp: String): ResultadoCp {
        val limpio = cp.filter { it.isDigit() }
        if (limpio.length != 5) return ResultadoCp.NoDisponible
        cache[limpio]?.let { return it }
        if (!SupabaseConfig.isConfigured) return ResultadoCp.NoDisponible

        val resultado = try {
            val respuesta = client.get(
                "${SupabaseConfig.URL}/functions/v1/geocode?cp=$limpio"
            ) {
                header(HttpHeaders.Authorization, "Bearer ${SupabaseConfig.ANON_KEY}")
                header("apikey", SupabaseConfig.ANON_KEY)
            }

            if (!respuesta.status.isSuccess()) {
                // La función contestó mal: no es que el código no exista.
                ResultadoCp.NoDisponible
            } else {
                // Un código que no existe contesta 200 con `{"encontrado": false}`: sin
                // código de estado, que es lo único que no se puede suplir a mano.
                val fila = json.decodeFromString<LugarDto>(respuesta.bodyAsText())
                val codigo = fila.estadoCodigo.orEmpty()
                if (codigo.isBlank()) {
                    ResultadoCp.NoExiste
                } else {
                    ResultadoCp.Encontrado(
                        LugarDelCp(
                            cp = fila.cp ?: limpio,
                            ciudad = fila.ciudad.orEmpty(),
                            estado = fila.estado.orEmpty(),
                            estadoCodigo = codigo,
                            colonias = fila.colonias.filter { it.isNotBlank() },
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Sin red, función caída, u origen no permitido por CORS —que desde el
            // navegador llega aquí como una excepción y no como un código de estado—.
            ResultadoCp.NoDisponible
        }

        // Un código inexistente seguirá sin existir, así que se recuerda. Un fallo de
        // conexión no: puede ser pasajero, y recordarlo dejaría el formulario sin
        // consulta el resto de la sesión.
        if (resultado !is ResultadoCp.NoDisponible) cache[limpio] = resultado
        return resultado
    }
}
