package ui.screens

import androidx.compose.runtime.Composable
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.SectionTitle

@Composable
fun AboutScreen() {
    ContentScreen(title = "Nosotros") {
        Paragraph(
            "Passio Agogo nace para ayudarte a descubrir y comparar productos de distintas " +
                "tiendas en un solo lugar, de forma rápida y sencilla."
        )
        SectionTitle("Nuestra misión")
        Paragraph(
            "Reunir la mejor oferta disponible y presentarla de manera clara para que tomes " +
                "mejores decisiones de compra."
        )
        SectionTitle("Qué ofrecemos")
        Paragraph("• Catálogo unificado de productos de varias tiendas.")
        Paragraph("• Comparación de precios y ofertas destacadas.")
        Paragraph("• Una experiencia rápida y accesible desde el navegador.")
        SectionTitle("Contacto")
        Paragraph("¿Tienes dudas o sugerencias? Escríbenos a hola@passioagogo.com.")
    }
}
