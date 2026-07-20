package ui.screens

import androidx.compose.runtime.Composable
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.SectionTitle

@Composable
fun TermsScreen() {
    ContentScreen(title = "Términos y condiciones") {
        Paragraph(
            "PLACEHOLDER — reemplaza este texto por los términos legales reales revisados " +
                "por tu equipo antes de publicar en producción."
        )
        SectionTitle("1. Aceptación")
        Paragraph(
            "Al usar Passio Agogo aceptas estos términos. Si no estás de acuerdo, por favor no " +
                "utilices el servicio."
        )
        SectionTitle("2. Uso del servicio")
        Paragraph(
            "La información del catálogo es orientativa y puede cambiar sin previo aviso. No " +
                "garantizamos disponibilidad ni precios de las tiendas de terceros."
        )
        SectionTitle("3. Propiedad intelectual")
        Paragraph(
            "Las marcas y contenidos de terceros pertenecen a sus respectivos dueños."
        )
        SectionTitle("4. Limitación de responsabilidad")
        Paragraph(
            "Passio Agogo no se hace responsable por decisiones de compra realizadas en tiendas " +
                "externas."
        )
        SectionTitle("5. Cambios")
        Paragraph("Podemos actualizar estos términos; la versión vigente se publicará aquí.")
    }
}
