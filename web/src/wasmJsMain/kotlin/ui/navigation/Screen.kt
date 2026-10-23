package ui.navigation

/** Pantallas de la app y su ruta (usada en el hash de la URL: #/inicio, #/catalogo, ...). */
enum class Screen(val route: String, val title: String) {
    Home("inicio", "Inicio"),
    Catalog("catalogo", "Catálogo"),
    About("nosotros", "Nosotros"),
    Video("video", "Video"),
    Podcast("podcast", "Podcast"),
    Events("eventos", "Eventos"),
    /** Catálogo acotado a una categoría con pantalla propia: `#/seccion/<slug>`. */
    Section("seccion", "Sección"),
    Care("uso-y-cuidados", "Uso y cuidados"),
    Wheel("ruleta", "Gira y gana"),
    Cart("carrito", "Mi solicitud"),
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
 * Destino completo: una pantalla y, si las tiene, las cosas concretas que muestra.
 *
 * El [id] existe para las pantallas de ficha —la de un evento, `#/eventos/<id>`— y para
 * las secciones del catálogo, donde es el slug de la categoría (`#/seccion/lenceria`).
 * El [subId] añade un nivel más: la ficha de un producto dentro de una sección
 * (`#/seccion/lenceria/conjunto-de-encaje`).
 *
 * Al viajar en la URL, el destino se puede compartir y el botón de atrás del navegador
 * devuelve al listado, que es lo que cualquiera espera.
 */
data class Route(val screen: Screen, val id: String? = null, val subId: String? = null) {
    val hash: String
        get() = buildString {
            append("#/").append(screen.route)
            if (!id.isNullOrBlank()) {
                append('/').append(id)
                // Sin padre no hay hijo: un subId suelto daría una URL que no se puede
                // volver a interpretar.
                if (!subId.isNullOrBlank()) append('/').append(subId)
            }
        }

    companion object {
        /** Interpreta el hash: pantalla / identificador / subidentificador. */
        fun fromPath(path: String): Route {
            val partes = path.split("/").filter { it.isNotBlank() }
            val screen = Screen.fromRoute(partes.firstOrNull())
            return Route(screen, partes.getOrNull(1), partes.getOrNull(2))
        }
    }
}
