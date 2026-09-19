package models

import kotlinx.serialization.Serializable

/**
 * Sector de la ruleta tal y como llega de `wheel_prizes`.
 *
 * No trae `peso`: las probabilidades se quedan en el servidor, que es quien sortea.
 */
@Serializable
data class WheelPrizeDto(
    val id: String? = null,
    val rotulo: String? = null,
    val detalle: String? = null,
    val orden: Int = 0,
)

/** Sector listo para dibujarse. */
data class WheelPrize(
    val id: String,
    val rotulo: String,
    val detalle: String,
)

/** Cláusula de las bases tal y como llega de `wheel_terms` (25_wheel_terms.sql). */
@Serializable
data class WheelTermDto(
    val id: String? = null,
    val titulo: String? = null,
    val cuerpo: String? = null,
    val orden: Int = 0,
)

/**
 * Cláusula lista para pintarse.
 *
 * Las bases viven en la base de datos y no en el código porque cambian —fechas de
 * vigencia, productos excluidos— y recompilar la web para corregir una fecha acabaría
 * con unas bases desactualizadas publicadas.
 */
data class WheelTerm(
    val id: String,
    val titulo: String,
    val cuerpo: String,
)

/**
 * Lo que devolvió el servidor tras girar.
 *
 * @param yaGiro la persona ya había girado dentro de la ventana permitida; esto es lo
 *   que ganó entonces, no un premio nuevo.
 */
data class SpinOutcome(
    val prizeId: String,
    val rotulo: String,
    val detalle: String,
    val codigo: String,
    val otorgaPremio: Boolean,
    val yaGiro: Boolean,
)
