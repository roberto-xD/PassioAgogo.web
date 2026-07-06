package ui.screens

import androidx.compose.runtime.Composable
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.SectionTitle

@Composable
fun HelpScreen() {
    ContentScreen(title = "Ayuda") {
        Paragraph("Preguntas frecuentes sobre el uso de Passio Agogo.")
        SectionTitle("¿Cómo busco productos?")
        Paragraph(
            "Entra a la sección Catálogo para ver los productos disponibles. Próximamente podrás " +
                "filtrar por tienda y categoría."
        )
        SectionTitle("¿Los precios están actualizados?")
        Paragraph(
            "Mostramos la información más reciente disponible, pero los precios finales pueden " +
                "variar en la tienda de origen."
        )
        SectionTitle("¿Necesito una cuenta?")
        Paragraph(
            "Por ahora no. El inicio de sesión llegará en una próxima versión para funciones " +
                "personalizadas."
        )
        SectionTitle("¿Cómo reporto un problema?")
        Paragraph("Escríbenos a soporte@passioagogo.com y te ayudamos.")
    }
}
