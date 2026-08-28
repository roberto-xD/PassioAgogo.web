package ui.navigation

/** Pantallas de la app y su ruta (usada en el hash de la URL: #/inicio, #/catalogo, ...). */
enum class Screen(val route: String, val title: String) {
    Home("inicio", "Inicio"),
    Catalog("catalogo", "Catálogo"),
    About("nosotros", "Nosotros"),
    Video("video", "Video"),
    Podcast("podcast", "Podcast"),
    Events("eventos", "Eventos"),
    Care("uso-y-cuidados", "Uso y cuidados"),
    Contact("contacto", "Contacto"),
    Login("acceder", "Acceder"),
    Account("cuenta", "Mi cuenta"),
    Terms("terminos", "Términos y condiciones"),
    Privacy("privacidad", "Política de privacidad"),
    Help("ayuda", "Ayuda");

    companion object {
        fun fromRoute(route: String?): Screen =
            entries.firstOrNull { it.route == route } ?: Home
    }
}

/**
 * Destino completo: una pantalla y, si la tiene, la cosa concreta que muestra.
 *
 * El [id] existe para las pantallas de ficha, hoy la de un evento (`#/eventos/<id>`).
 * Al viajar en la URL, la ficha se puede compartir y el botón de atrás del navegador
 * devuelve al listado, que es lo que cualquiera espera.
 */
data class Route(val screen: Screen, val id: String? = null) {
    val hash: String
        get() = if (id.isNullOrBlank()) "#/${screen.route}" else "#/${screen.route}/$id"

    companion object {
        /** Interpreta el hash: primer segmento la pantalla, segundo el identificador. */
        fun fromPath(path: String): Route {
            val partes = path.split("/").filter { it.isNotBlank() }
            val screen = Screen.fromRoute(partes.firstOrNull())
            return Route(screen, partes.getOrNull(1))
        }
    }
}
