package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Fila de `events` (21_events_widget.sql). */
@Serializable
data class EventDto(
    val id: String? = null,
    val titulo: String? = null,
    val resumen: String? = null,
    val detalles: String? = null,
    val lugar: String? = null,
    val imagen: String? = null,
    @SerialName("fecha_inicio") val fechaInicio: String? = null,
    @SerialName("fecha_fin") val fechaFin: String? = null,
    val enlace: String? = null,
)

/** Fila de `site_settings`: interruptores del sitio en pares clave-valor. */
@Serializable
data class SiteSettingDto(
    val clave: String = "",
    val valor: String = "",
)

/**
 * Evento ya listo para pintarse, con las fechas convertidas a la zona horaria de quien
 * mira. La base las guarda en UTC; mostrarlas en crudo anunciaría la hora equivocada.
 */
data class EventItem(
    val id: String,
    val titulo: String,
    val resumen: String,
    val detalles: String,
    val lugar: String,
    val imagen: String,
    /** "12 sep" o "12–14 sep": lo que cabe en una línea del widget. */
    val fechaCorta: String,
    /** "12 de septiembre de 2026, 20:00": para la pantalla de Eventos. */
    val fechaLarga: String,
    val enlace: String,
)
