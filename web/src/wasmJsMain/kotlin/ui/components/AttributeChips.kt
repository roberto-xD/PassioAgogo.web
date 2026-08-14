package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ui.pgmodels.PGAttributeChip
import ui.theme.PassionTheme
import ui.theme.emojiSoportado

/** Contorno de la píldora. Fino: la chip informa, no compite con la llamada a la acción. */
private val BORDE = 1.dp

/**
 * Rasgos del producto en píldoras, en varias filas si no caben en una.
 *
 * **No son interactivas**: aquí no filtran ni seleccionan nada, solo describen el
 * artículo. Por eso no llevan estado de selección ni `clickable`, y su contorno es más
 * discreto que el de los chips de categoría del catálogo, que sí son un control.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttributeChips(chips: List<PGAttributeChip>, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
        verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
    ) {
        chips.forEach { chip -> AttributeChip(chip) }
    }
}

@Composable
private fun AttributeChip(chip: PGAttributeChip) {
    val shape = MaterialTheme.shapes.extraLarge
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
        modifier = Modifier
            .clip(shape)
            .background(PassionTheme.semantics.overlayWeak)
            .border(BORDE, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(
                horizontal = PassionTheme.spacing.s4,
                vertical = PassionTheme.spacing.s2,
            ),
    ) {
        // Solo los que la fuente empaquetada sabe dibujar; el resto se omite y queda
        // el rótulo, que es quien lleva la información.
        if (emojiSoportado(chip.emoji)) {
            Text(
                text = chip.emoji,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = PassionTheme.type.emoji,
            )
        }
        Text(
            text = chip.label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
