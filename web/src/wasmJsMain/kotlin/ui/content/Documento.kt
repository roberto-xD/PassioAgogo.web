package ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.ContentScreen
import ui.components.DataCard
import ui.components.Paragraph
import ui.components.SectionTitle
import ui.theme.PassionTheme

/**
 * Un trozo de documento.
 *
 * El texto no se escribe aquí: sale de los Markdown de `contenido/` a través
 * de `tools/contenido/build.py`, que genera [Textos] y, con el mismo origen,
 * las páginas HTML de `sitio/`. Así la política que lee el cliente en la app y
 * la que lee Google son la misma, sin copiarla a mano en dos sitios.
 */
sealed interface Bloque {
    /** Encabezado de sección: el `##` del Markdown. */
    data class Titulo(val texto: String) : Bloque

    data class Parrafo(val texto: String) : Bloque

    data class Vineta(val texto: String) : Bloque

    data class Numero(val indice: Int, val texto: String) : Bloque

    /** Las tarjetas de «Qué ofrecemos»: el `###` y el párrafo que le sigue. */
    data class Tarjeta(val titulo: String, val cuerpo: String) : Bloque
}

data class Documento(
    val titulo: String,
    val actualizado: String,
    val bloques: List<Bloque>,
)

/** Debajo de este ancho las tarjetas quedan en una sola columna. */
private val TWO_COLUMN_MIN_WIDTH = 560.dp

/**
 * Dibuja un [Documento] completo.
 *
 * @param pie contenido extra al final. Lo usa «Nosotros» para sus datos de
 *   contacto, que en la app son botones —el correo se copia, el teléfono abre
 *   WhatsApp— y en la página web son texto. Por eso esa lista va marcada como
 *   `{solo-web}` en el Markdown: aquí la pone este pie.
 */
@Composable
fun DocumentoScreen(documento: Documento, pie: @Composable ColumnScope.() -> Unit = {}) {
    ContentScreen(title = documento.titulo) {
        Paragraph("Última actualización: ${documento.actualizado}.")

        // Las tarjetas consecutivas van juntas en una rejilla, así que no se
        // pueden dibujar de una en una: se recorren por tramos.
        var i = 0
        while (i < documento.bloques.size) {
            val bloque = documento.bloques[i]
            if (bloque is Bloque.Tarjeta) {
                val tramo = documento.bloques.drop(i)
                    .takeWhile { it is Bloque.Tarjeta }
                    .filterIsInstance<Bloque.Tarjeta>()
                Tarjetas(tramo)
                i += tramo.size
            } else {
                Simple(bloque)
                i++
            }
        }

        pie()
    }
}

@Composable
private fun Simple(bloque: Bloque) {
    when (bloque) {
        is Bloque.Titulo -> SectionTitle(bloque.texto)
        is Bloque.Parrafo -> Paragraph(bloque.texto)
        is Bloque.Vineta -> Paragraph("• ${bloque.texto}")
        is Bloque.Numero -> Paragraph("${bloque.indice}. ${bloque.texto}")
        is Bloque.Tarjeta -> Unit // lo resuelve [DocumentoScreen] por tramos
    }
}

/**
 * La rejilla de tarjetas.
 *
 * Las filas se reparten a mano en lugar de dejarlas fluir: así las dos tarjetas
 * de una fila comparten alto y el bloque no queda escalonado cuando un texto es
 * más largo que el otro.
 */
@Composable
private fun Tarjetas(tarjetas: List<Bloque.Tarjeta>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnas = if (maxWidth >= TWO_COLUMN_MIN_WIDTH) 2 else 1

        Column(verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s3)) {
            tarjetas.chunked(columnas).forEach { fila ->
                Row(
                    // Alto intrínseco: la fila mide lo que la tarjeta más alta y
                    // las demás lo rellenan.
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s3),
                ) {
                    fila.forEach { Tarjeta(it) }
                    // Con un número impar de tarjetas, la última no debe ocupar
                    // toda la fila: se rellena el hueco.
                    repeat(columnas - fila.size) { Hueco() }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Tarjeta(tarjeta: Bloque.Tarjeta) {
    DataCard(
        titulo = tarjeta.titulo,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
    ) {
        Text(
            text = tarjeta.cuerpo,
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = PassionTheme.spacing.s1),
        )
    }
}

@Composable
private fun RowScope.Hueco() {
    Column(modifier = Modifier.weight(1f)) {}
}
