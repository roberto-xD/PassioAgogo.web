package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Una opción de envío cotizada.
 *
 * Viene de la Edge Function `cotizar-envio`, no de Envia directamente: la forma es
 * nuestra, así que cómo nombre la paquetería sus campos no llega hasta aquí.
 */
@Serializable
data class OpcionEnvio(
    val paqueteria: String = "",
    val servicio: String = "",
    val descripcion: String = "",
    val precio: Double = 0.0,
    val moneda: String = "MXN",
    /**
     * Lo que promete la paquetería con sus palabras: «2-4 días», «Día siguiente».
     *
     * Se enseña esta frase y no el número de días que también manda Envia, porque los
     * dos no siempre coinciden —una dice «2-4 días» y el otro calcula 6— y enseñar
     * ambos sería contradecirse en dos renglones. La promesa es de la paquetería.
     */
    val entrega: String = "",
) {
    /** Lo que se lee en la tarjeta: «Estafeta Terrestre», ya sin el nombre repetido. */
    val titulo: String get() = descripcion.ifBlank { "$paqueteria $servicio".trim() }

    /** Identifica la opción entre las demás de la lista. */
    val clave: String get() = "$paqueteria/$servicio"
}

/** Lo que devuelve `cotizar-envio`. */
@Serializable
data class CotizacionDto(
    val opciones: List<OpcionEnvio> = emptyList(),
    /**
     * El bulto no cabía en la caja mayor de la tienda, o pesaba de más: lo cotizado va
     * por debajo de lo que costará la guía. Se dice, no se disimula.
     */
    val recortado: Boolean = false,
    val error: String? = null,
)

/** El envío que la persona eligió, tal como viaja en la solicitud. */
@Serializable
data class EnvioElegidoDto(
    @SerialName("envio_paqueteria") val paqueteria: String,
    @SerialName("envio_servicio") val servicio: String,
    @SerialName("envio_descripcion") val descripcion: String,
    @SerialName("envio_costo") val costo: Double,
    @SerialName("envio_entrega") val entrega: String,
)
