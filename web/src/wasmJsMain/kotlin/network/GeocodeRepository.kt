package network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Catálogo de códigos postales de Envia. **Público y sin token**, por eso se consulta
 * desde el navegador y no hay Edge Function de por medio: no hay ningún secreto que
 * esconder aquí.
 */
private const val GEOCODES = "https://geocodes.envia.com/zipcode/MX"

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class CodigoDto(
    // La clave empieza por dígito, así que no puede ser un nombre de propiedad.
    @SerialName("2digit") val dosLetras: String? = null,
)

@Serializable
private data class EstadoDto(val name: String? = null, val code: CodigoDto? = null)

@Serializable
private data class CpDto(
    @SerialName("zip_code") val cp: String? = null,
    /**
     * La ciudad. Fuera de la capital coincide con el municipio —Monterrey, Guadalajara—;
     * en la Ciudad de México `locality` dice «Ciudad de México» y la alcaldía va aparte.
     * Es la forma en que una paquetería espera leer el renglón.
     */
    val locality: String? = null,
    val state: EstadoDto? = null,
    /** Las colonias de ese código postal. */
    val suburbs: List<String> = emptyList(),
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
 * Nunca estorba: si el servicio no contesta o el código no existe, devuelve `null` y la
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

        val lugar = runCatching {
            val respuesta = client.get("$GEOCODES/$limpio")
            if (!respuesta.status.isSuccess()) return@runCatching null

            // Un código que no existe contesta 200 con una lista vacía, no con un error.
            val filas = json.decodeFromString<List<CpDto>>(respuesta.bodyAsText())
            val fila = filas.firstOrNull() ?: return@runCatching null

            val codigo = fila.state?.code?.dosLetras.orEmpty()
            // Sin código de estado la respuesta no sirve para lo único que la queremos.
            if (codigo.isBlank()) return@runCatching null

            LugarDelCp(
                cp = fila.cp ?: limpio,
                ciudad = fila.locality.orEmpty(),
                estado = fila.state?.name.orEmpty(),
                estadoCodigo = codigo,
                colonias = fila.suburbs.filter { it.isNotBlank() },
            )
        }.getOrNull()

        // Se recuerda también el fallo: un código que no existe no existirá al siguiente
        // dígito tampoco, y repreguntar por cada tecla sería castigar a quien se equivoca.
        cache[limpio] = lugar
        return lugar
    }
}
