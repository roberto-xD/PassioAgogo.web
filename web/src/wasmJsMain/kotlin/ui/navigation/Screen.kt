package ui.navigation

/** Pantallas de la app y su ruta (usada en el hash de la URL: #/inicio, #/catalogo, ...). */
enum class Screen(val route: String, val title: String) {
    Home("inicio", "Inicio"),
    Catalog("catalogo", "Catálogo"),
    About("nosotros", "Nosotros"),
    Terms("terminos", "Términos y condiciones"),
    Privacy("privacidad", "Política de privacidad"),
    Help("ayuda", "Ayuda");

    companion object {
        fun fromRoute(route: String?): Screen =
            entries.firstOrNull { it.route == route } ?: Home
    }
}
