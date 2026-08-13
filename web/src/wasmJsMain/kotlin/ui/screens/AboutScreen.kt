package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.SectionTitle
import ui.theme.PassionTheme

/** Debajo de este ancho las tarjetas de «Qué ofrecemos» quedan en una sola columna. */
private val TWO_COLUMN_MIN_WIDTH = 560.dp

/** Las cuatro cosas que ofrece la tienda, tal y como se aprobaron en el copy. */
private val OFERTA = listOf(
    "Catálogo curado" to
        "Juguetes, lubricantes y aceites, lencería, juegos y suplementos elegidos uno por uno.",
    "Fichas claras" to
        "Materiales, funciones, batería y cuidados. Sin rodeos y sin tecnicismos.",
    "Precios justos" to
        "Precio a la vista en pesos, ofertas reales y ninguna sorpresa al final.",
    "Compra sencilla" to
        "Todo desde el navegador, en pocos pasos, a la hora que tú quieras.",
)

private val CONTACTO = listOf(
    "contacto@passionagogo.com",
    "5555 1387 8451",
    "Ciudad de México",
)

@Composable
fun AboutScreen() {
    ContentScreen(title = "Nosotros") {
        Paragraph(
            "Passion à gogo es una tienda de bienestar sexual, no un sexshop. Elegimos " +
                "cada producto de nuestro catálogo, te explicamos con calma qué hace y " +
                "para qué sirve, y te lo llevamos a casa sin que nadie tenga que enterarse."
        )
        Paragraph(
            "Queremos que entrar aquí se sienta como entrar a cualquier tienda que te " +
                "gusta: cómodo, luminoso y sin pena."
        )

        SectionTitle("Nuestra misión")
        Paragraph(
            "Hablar del placer con la misma naturalidad con la que hablas de dormir bien " +
                "o comer rico. Curamos un catálogo en el que puedes confiar y lo " +
                "acompañamos de información honesta, para que elijas porque entiendes lo " +
                "que compras, no porque te dio menos vergüenza."
        )

        SectionTitle("Qué ofrecemos")
        OfferGrid()

        SectionTitle("Educación y asesoría")
        Paragraph(
            "Si es tu primera vez o no sabes qué se ajusta a lo que buscas, escríbenos: " +
                "te orientamos con información verificada, sin sermones y sin venderte lo " +
                "más caro. También publicamos guías de uso, limpieza y cuidado de cada " +
                "tipo de producto."
        )

        SectionTitle("Privacidad y discreción")
        Paragraph(
            "Tus datos son tuyos: no los compartimos ni los vendemos. En tu estado de " +
                "cuenta aparece un cargo neutro y en la caja no verás nada que delate qué " +
                "compraste."
        )

        SectionTitle("Envíos")
        Paragraph(
            "Enviamos a todo México desde la CDMX, en empaque discreto y sellado: caja " +
                "neutra, sin logos ni descripciones del contenido. Te damos guía de " +
                "rastreo en cuanto tu pedido sale."
        )

        SectionTitle("Contacto")
        Paragraph(
            "¿Dudas, sugerencias o no sabes por dónde empezar? Escríbenos y te " +
                "respondemos sin juicios."
        )
        ContactRow()
    }
}

/**
 * Las cuatro tarjetas de «Qué ofrecemos».
 *
 * Dos columnas cuando hay sitio y una cuando no. Se reparten a mano en filas en lugar de
 * dejarlas fluir: así las dos tarjetas de una fila comparten alto y el bloque no queda
 * escalonado cuando un texto es más largo que el otro.
 */
@Composable
private fun OfferGrid() {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnas = if (maxWidth >= TWO_COLUMN_MIN_WIDTH) 2 else 1

        Column(verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s3)) {
            OFERTA.chunked(columnas).forEach { fila ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s3),
                ) {
                    fila.forEach { (titulo, texto) ->
                        OfferCard(titulo = titulo, texto = texto)
                    }
                    // Con un número impar de tarjetas la última no debe ocupar toda la
                    // fila: se rellena el hueco para que conserve el ancho de columna.
                    repeat(columnas - fila.size) { Spacer() }
                }
            }
        }
    }
}

@Composable
private fun RowScope.OfferCard(titulo: String, texto: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.medium,
            )
            .padding(PassionTheme.spacing.s4),
    ) {
        Text(
            text = titulo,
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = texto,
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = PassionTheme.spacing.s1),
        )
    }
}

@Composable
private fun RowScope.Spacer() {
    Column(modifier = Modifier.weight(1f)) {}
}

@Composable
private fun ContactRow() {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s2),
    ) {
        val enLinea = maxWidth >= TWO_COLUMN_MIN_WIDTH

        if (enLinea) {
            Row(horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2)) {
                CONTACTO.forEach { dato -> ContactPill(dato) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2)) {
                CONTACTO.forEach { dato -> ContactPill(dato) }
            }
        }
    }
}

@Composable
private fun ContactPill(texto: String) {
    Text(
        text = texto,
        color = PassionTheme.semantics.onBackgroundMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(PassionTheme.semantics.overlayWeak)
            .padding(
                horizontal = PassionTheme.spacing.s3,
                vertical = PassionTheme.spacing.s2,
            ),
    )
}
