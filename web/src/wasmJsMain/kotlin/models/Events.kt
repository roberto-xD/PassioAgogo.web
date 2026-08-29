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
    val imagenes: List<String> = emptyList(),
    @SerialName("fecha_inicio") val fechaInicio: String? = null,
    @SerialName("fecha_fin") val fechaFin: String? = null,
    val enlace: String? = null,
    @SerialName("enlace_texto") val enlaceTexto: String? = null,
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
    /** Fragmento de URL derivado del título: `#/eventos/ah-con`. */
    val slug: String,
    val titulo: String,
    val resumen: String,
    val detalles: String,
    val lugar: String,
    /** Todas las imágenes; la primera hace de portada en la tarjeta del listado. */
    val imagenes: List<String>,
    /** "12 sep" o "12–14 sep": lo que cabe en una línea del widget. */
    val fechaCorta: String,
    /** "12 de septiembre de 2026, 20:00": una línea con todo, para el listado. */
    val fechaLarga: String,
    /** "12 de septiembre de 2026": la fecha sola, para la ficha. */
    val fechaSolo: String,
    /** "20:00": la hora sola, para la ficha. */
    val horaSolo: String,
    /** La fecha de cierre, si el evento dura varios días. */
    val fechaFinSolo: String,
    val enlace: String,
    /** Rótulo del botón elegido desde la base; vacío = el que ponga la web. */
    val enlaceTexto: String,
    /** Ya terminó. El listado los muestra igual, después de los próximos. */
    val esPasado: Boolean,
    /** Fecha de inicio sin formatear. Solo para ordenar: las bonitas no se comparan bien. */
    val ordenIso: String,
) {
    val portada: String get() = imagenes.firstOrNull().orEmpty()
}
