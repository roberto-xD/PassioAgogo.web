package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.pgmodels.PGVariant
import ui.theme.PassionTheme

/** Contorno de la píldora. Más marcado que el de las chips informativas: esto se pulsa. */
private val BORDE = 1.dp

/**
 * Selector de presentación.
 *
 * A diferencia de [AttributeChips], que solo describe, estas píldoras **son un control**:
 * la elegida decide qué se añade al carrito y a qué precio. Por eso llevan estado de
 * selección y el mismo color de acento que los filtros del catálogo.
 *
 * Con una sola presentación no se dibuja nada: un selector de una opción no es una
 * elección, solo ruido.
 *
 * El precio va dentro de cada píldora cuando las variantes no cuestan lo mismo. Es el
 * dato que decide la compra, y obligar a seleccionar una para verlo convierte el
 * selector en una adivinanza.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VariantPicker(
    variants: List<PGVariant>,
    selectedId: String,
    onSelect: (PGVariant) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (variants.size < 2) return

    val preciosDistintos = variants.map { it.precio }.distinct().size > 1

    Column(modifier = modifier) {
        Text(
            text = "Presentación",
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = PassionTheme.spacing.s2),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
            verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
        ) {
            variants.forEach { variante ->
                Opcion(
                    variante = variante,
                    seleccionada = variante.id == selectedId,
                    conPrecio = preciosDistintos,
                    onClick = { onSelect(variante) },
                )
            }
        }
    }
}

@Composable
private fun Opcion(
    variante: PGVariant,
    seleccionada: Boolean,
    conPrecio: Boolean,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    val fondo = if (seleccionada) MaterialTheme.colorScheme.primary
    else PassionTheme.semantics.overlayWeak
    val contenido = if (seleccionada) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .clip(shape)
            .background(fondo)
            .border(
                width = BORDE,
                color = if (seleccionada) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = PassionTheme.spacing.s4,
                vertical = PassionTheme.spacing.s2,
            ),
    ) {
        Text(
            text = variante.etiqueta,
            color = contenido,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (seleccionada) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (conPrecio) {
            Text(
                text = variante.precioFinal,
                color = contenido,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
