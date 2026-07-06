package ui.screens

import androidx.compose.runtime.Composable
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.SectionTitle

@Composable
fun PrivacyScreen() {
    ContentScreen(title = "Política de privacidad") {
        Paragraph(
            "PLACEHOLDER — reemplaza este texto por la política de privacidad real, acorde a la " +
                "legislación aplicable (p. ej. GDPR / LFPDPPP) antes de publicar."
        )
        SectionTitle("Datos que recopilamos")
        Paragraph(
            "Describe aquí qué datos recopilas (p. ej. datos de navegación, cuenta de usuario " +
                "cuando exista login) y con qué finalidad."
        )
        SectionTitle("Uso de los datos")
        Paragraph("Explica cómo se usan los datos y si se comparten con terceros.")
        SectionTitle("Cookies y almacenamiento")
        Paragraph("Detalla el uso de cookies o almacenamiento local en el navegador.")
        SectionTitle("Tus derechos")
        Paragraph(
            "Indica cómo el usuario puede acceder, rectificar o eliminar sus datos, y un contacto " +
                "para ejercerlos: privacidad@passioagogo.com."
        )
    }
}
