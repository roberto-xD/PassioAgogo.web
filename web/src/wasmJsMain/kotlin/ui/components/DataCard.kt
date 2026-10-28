package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.theme.PassionTheme

/**
 * Tarjeta con título y borde fino, el formato que ya usan las tarjetas de «Qué ofrecemos».
 *
 * Vive aquí y no en una pantalla concreta porque el carrito usa el mismo formato: tenerlo
 * en dos sitios garantizaba que uno de los dos se quedara atrás en cuanto cambiara el
 * tema.
 */
@Composable
fun DataCard(
    titulo: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(PassionTheme.spacing.s4),
    ) {
        if (titulo != null) {
            Text(
                text = titulo,
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        content()
    }
}

/** Un dato de los que se enseñan dentro de una [DataCard]: etiqueta tenue y valor legible. */
@Composable
fun DataLine(etiqueta: String, valor: String) {
    Text(
        text = etiqueta,
        color = PassionTheme.semantics.onBackgroundSubtle,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = PassionTheme.spacing.s3),
    )
    Text(
        text = valor,
        color = PassionTheme.semantics.onBackgroundStrong,
        style = MaterialTheme.typography.bodyMedium,
    )
}
