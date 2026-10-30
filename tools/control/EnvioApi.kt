package com.passioagogo.control.envio

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Guías de envío y recolecciones, desde la app de control.
 *
 * Para pegar en la app de control, al lado de `AdminKeyTool`. No trae nada de Envia
 * dentro: **el token de Envia no puede vivir en un APK**, que se descompila, y ese token
 * genera guías reales y cobra. Lo que hay aquí son dos llamadas a Edge Functions que
 * sí lo tienen.
 *
 * El reparto de trabajo, para que quede claro al leerlo:
 *
 *  1. La app abre el sobre de la dirección con la clave privada (`AdminKeyTool`).
 *  2. Se la pasa a [generarGuia] **en claro**, en memoria, sin guardarla en ningún lado.
 *  3. La función pide la guía a Envia y anota el resultado en la solicitud.
 *
 * La dirección viaja por TLS a una función que no la escribe en ninguna tabla. Lo que
 * está cifrado en la base sigue cifrado: esto no lo cambia.
 *
 * Las dos funciones comprueban que quien llama es administración, así que hace falta
 * una sesión abierta en el [SupabaseClient] que se les pase. Si la sesión caduca,
 * devuelven 401 y aquí se ve como [ResultadoGuia.Fallo].
 */
class EnvioApi(private val supabase: SupabaseClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Pide la guía de una solicitud.
     *
     * [direccion] es la dirección del cliente **ya descifrada**. [carrier] y [servicio]
     * son opcionales: si no se indican, se usa lo que el cliente eligió al cotizar.
     *
     * Si la solicitud ya tenía guía, no se genera otra ni se cobra: devuelve la que hay
     * con [Guia.yaTenia] en cierto.
     */
    suspend fun generarGuia(
        solicitudId: String,
        direccion: DireccionEnClaro,
        carrier: String? = null,
        servicio: String? = null,
        /**
         * Marca el pedido para que pasen a recogerlo.
         *
         * **No pide la recolección**, solo la anota. Envia agrupa varias guías en una
         * sola si son de la misma paquetería y salen del mismo sitio, y pedir una por
         * pedido sería un camión por paquete. Lo marcado se recoge después con
         * [solicitarRecoleccion], y mientras tanto sale en [pendientesDeRecoleccion].
         */
        recoleccion: Boolean = false,
        /**
         * Permite enviar por una paquetería distinta de la que eligió el cliente.
         *
         * Sin esto, pasar un [carrier] que no sea el suyo se rechaza. Quien elige
         * Estafeta muchas veces no está eligiendo un precio: está eligiendo en quién
         * confía. Cambiarlo a veces hace falta —no cubre la ruta, rechaza la guía—,
         * pero tiene que ser una decisión, no un descuido, y conviene avisarle.
         */
        permitirCambio: Boolean = false,
    ): ResultadoGuia = llamar("generar-guia") {
        json.encodeToString(
            PeticionGuia.serializer(),
            PeticionGuia(solicitudId, direccion, carrier, servicio, recoleccion, permitirCambio),
        )
    }.fold(
        alOk = { cuerpo ->
            val r = json.decodeFromString<RespuestaGuia>(cuerpo)
            val guia = r.guia
            if (guia == null) {
                ResultadoGuia.Fallo(r.error ?: "La paquetería no devolvió la guía.")
            } else {
                ResultadoGuia.Ok(guia.copy(yaTenia = r.yaTenia), r.aviso)
            }
        },
        alFallo = { ResultadoGuia.Fallo(it) },
    )

    /**
     * Lo que está marcado para recolección y todavía no se ha pedido.
     *
     * Lee la vista `v_recolecciones_pendientes`, que ya trae solo lo que tiene guía,
     * está marcado y sigue esperando. Es la lista del final del día: se agrupa por
     * paquetería y se pide una recolección por cada grupo.
     */
    suspend fun pendientesDeRecoleccion(): List<GuiaPendiente> = runCatching {
        supabase.from("v_recolecciones_pendientes")
            .select()
            .decodeList<GuiaPendiente>()
    }.getOrDefault(emptyList())

    /**
     * Pide que pasen a recoger.
     *
     * Dos formas: con [solicitudIds] para unas concretas, o con [paqueteria] para todas
     * las suyas que estén esperando —que es lo normal al final del día—.
     *
     * Todas tienen que ser **de la misma paquetería**: Envia agrupa por paquetería y
     * dirección de origen. Si se mezclan, la función lo dice antes de llamar a Envia.
     *
     * [fecha] va como `AAAA-MM-DD`. La franja horaria tiene valores por defecto en el
     * servidor; se indican aquí solo para acotarla.
     */
    suspend fun solicitarRecoleccion(
        fecha: String,
        solicitudIds: List<String> = emptyList(),
        paqueteria: String? = null,
        desde: String? = null,
        hasta: String? = null,
    ): ResultadoRecoleccion = llamar("solicitar-recoleccion") {
        json.encodeToString(
            PeticionRecoleccion.serializer(),
            PeticionRecoleccion(solicitudIds, paqueteria, fecha, desde, hasta),
        )
    }.fold(
        alOk = { cuerpo ->
            val r = json.decodeFromString<RespuestaRecoleccion>(cuerpo)
            val rec = r.recoleccion
            if (rec == null) {
                ResultadoRecoleccion.Fallo(r.error ?: "No se agendó la recolección.")
            } else {
                ResultadoRecoleccion.Ok(rec, r.aviso)
            }
        },
        alFallo = { ResultadoRecoleccion.Fallo(it) },
    )

    /**
     * La llamada, con el mensaje de error ya legible.
     *
     * Las funciones contestan con `error` y a veces con `motivo`, que es lo que dijo la
     * paquetería. Los dos importan: «no se pudo generar la guía» no ayuda, y
     * «esta paquetería no recoge en tu zona» decide lo que haces después.
     */
    private suspend fun llamar(nombre: String, cuerpo: () -> String): Respuesta {
        if (supabase.auth.currentSessionOrNull() == null) {
            return Respuesta.Fallo("No hay sesión abierta. Vuelve a entrar.")
        }

        return try {
            val respuesta = supabase.functions(nombre) {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(cuerpo())
            }
            val texto = respuesta.bodyAsText()

            if (respuesta.status.value in 200..299) {
                Respuesta.Ok(texto)
            } else {
                Respuesta.Fallo(mensajeDeError(texto, respuesta.status.value))
            }
        } catch (e: Exception) {
            Respuesta.Fallo("No se pudo conectar: ${e.message ?: "error desconocido"}")
        }
    }

    private fun mensajeDeError(cuerpo: String, codigo: Int): String {
        val fallo = runCatching { json.decodeFromString<RespuestaError>(cuerpo) }.getOrNull()
        val base = fallo?.error ?: when (codigo) {
            401 -> "Tu sesión caducó. Vuelve a entrar."
            403 -> "Tu cuenta no tiene permiso para esto."
            else -> "No se pudo completar la operación ($codigo)."
        }
        // El motivo de la paquetería va detrás, no en lugar del nuestro: uno dice qué
        // pasó en la tienda y el otro qué dijo la paquetería.
        return listOfNotNull(base, fallo?.motivo).distinct().joinToString(" ")
    }

    private sealed interface Respuesta {
        data class Ok(val cuerpo: String) : Respuesta
        data class Fallo(val mensaje: String) : Respuesta

        fun <T> fold(alOk: (String) -> T, alFallo: (String) -> T): T = when (this) {
            is Ok -> runCatching { alOk(cuerpo) }
                .getOrElse { alFallo("Respuesta inesperada del servidor.") }
            is Fallo -> alFallo(mensaje)
        }
    }
}

// ============================================================
// Lo que va y lo que vuelve
// ============================================================

/** La dirección del cliente, ya abierta con la clave privada. */
@Serializable
data class DireccionEnClaro(
    val calle: String = "",
    val interior: String = "",
    val colonia: String = "",
    val cp: String = "",
    val ciudad: String = "",
    val estado: String = "",
    /** El código de dos letras. Si falta, el servidor lo saca del código postal. */
    val estadoCodigo: String = "",
    val referencias: String = "",
)

@Serializable
private data class PeticionGuia(
    val solicitudId: String,
    val direccion: DireccionEnClaro,
    val carrier: String? = null,
    val service: String? = null,
    val recoleccion: Boolean = false,
    val permitirCambio: Boolean = false,
)

@Serializable
private data class PeticionRecoleccion(
    val solicitudes: List<String>,
    val paqueteria: String? = null,
    val fecha: String,
    val desde: String? = null,
    val hasta: String? = null,
)

/** Una guía esperando recolección, de `v_recolecciones_pendientes`. */
@Serializable
data class GuiaPendiente(
    val id: String = "",
    val folio: Long = 0,
    @SerialName("guia_paqueteria") val paqueteria: String = "",
    @SerialName("guia_numero") val numero: String = "",
    @SerialName("guia_generada_en") val generadaEn: String = "",
    @SerialName("cliente_nombre") val cliente: String = "",
)

@Serializable
data class Guia(
    val numero: String = "",
    /** PDF de la etiqueta, para imprimir. */
    val url: String = "",
    val rastreo: String = "",
    val paqueteria: String = "",
    val servicio: String = "",
    val costo: Double = 0.0,
    @SerialName("enviaId") val enviaId: String = "",
    /** La solicitud ya tenía guía: esta es la de antes, no se generó ni se cobró otra. */
    val yaTenia: Boolean = false,
)

@Serializable
data class Recoleccion(
    val confirmacion: String = "",
    val paqueteria: String = "",
    val fecha: String = "",
    val desde: String = "",
    val hasta: String = "",
    val estado: String = "",
    val guias: Int = 0,
)

@Serializable
private data class RespuestaGuia(
    val guia: Guia? = null,
    val yaTenia: Boolean = false,
    val aviso: String? = null,
    val error: String? = null,
)

@Serializable
private data class RespuestaRecoleccion(
    val recoleccion: Recoleccion? = null,
    val aviso: String? = null,
    val error: String? = null,
)

@Serializable
private data class RespuestaError(val error: String? = null, val motivo: String? = null)

/**
 * El resultado, con el aviso aparte.
 *
 * [aviso] no es un error: la guía existe y está pagada, pero algo de después salió mal
 * —no se pudo anotar en la solicitud, por ejemplo—. Hay que enseñarlo, porque implica
 * apuntar el número a mano.
 */
sealed interface ResultadoGuia {
    data class Ok(val guia: Guia, val aviso: String? = null) : ResultadoGuia
    data class Fallo(val mensaje: String) : ResultadoGuia
}

sealed interface ResultadoRecoleccion {
    data class Ok(val recoleccion: Recoleccion, val aviso: String? = null) : ResultadoRecoleccion
    data class Fallo(val mensaje: String) : ResultadoRecoleccion
}
