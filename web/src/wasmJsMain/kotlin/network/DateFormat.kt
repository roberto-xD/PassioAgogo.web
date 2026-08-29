package network

/**
 * Fechas legibles a partir de los timestamps ISO que devuelve PostgREST.
 *
 * Se delega en `Intl` del navegador en lugar de formatear a mano: la base guarda en UTC
 * y `toLocaleDateString` convierte a la zona horaria de quien mira, que es la única hora
 * que le sirve para saber cuándo presentarse a un evento.
 *
 * Todas las funciones `js(...)` van en forma de expresión única, que es la que compila de
 * forma fiable en Kotlin/Wasm.
 */

/** "12 sep" — lo que cabe en una línea del widget. */
fun formatShortDate(iso: String): String =
    if (iso.isBlank()) "" else shortDate(iso)

/** "12 de septiembre de 2026, 20:00" — para la pantalla de Eventos. */
fun formatLongDate(iso: String): String =
    if (iso.isBlank()) "" else longDate(iso)

/**
 * Rango de fechas compacto. Si empieza y termina el mismo día, no repite la fecha; si el
 * evento no tiene fin, muestra solo el inicio.
 */
fun formatDateRange(inicioIso: String, finIso: String?): String {
    val inicio = formatShortDate(inicioIso)
    if (finIso.isNullOrBlank()) return inicio
    val fin = formatShortDate(finIso)
    return if (fin.isBlank() || fin == inicio) inicio else "$inicio – $fin"
}

/** "17 de agosto de 2026" — la fecha sola, sin hora. */
fun formatDateOnly(iso: String): String = if (iso.isBlank()) "" else dateOnly(iso)

/** "20:00" — la hora sola. */
fun formatTimeOnly(iso: String): String = if (iso.isBlank()) "" else timeOnly(iso)

/** `true` si el instante ya pasó, según el reloj de quien mira. */
fun isPast(iso: String): Boolean =
    if (iso.isBlank()) false else pastCheck(iso)

private fun pastCheck(iso: String): Boolean = js("new Date(iso).getTime() < Date.now()")

private fun dateOnly(iso: String): String =
    js("new Date(iso).toLocaleDateString('es-MX', { day: 'numeric', month: 'long', year: 'numeric' })")

private fun timeOnly(iso: String): String =
    js("new Date(iso).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })")

private fun shortDate(iso: String): String =
    js("new Date(iso).toLocaleDateString('es-MX', { day: 'numeric', month: 'short' })")

private fun longDate(iso: String): String =
    js("new Date(iso).toLocaleString('es-MX', { day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' })")
