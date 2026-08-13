package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import models.EventItem
import ui.theme.PassionTheme

/** Por debajo de este ancho la tarjeta taparía media pantalla: se encoge a una burbuja. */
private val COMPACT_MAX_WIDTH = 640.dp

/** Ancho de la tarjeta desplegada. */
private val CARD_WIDTH = 300.dp

/** Cuántos eventos caben en el anuncio sin convertirlo en una lista larga. */
private const val MAX_VISIBLE = 3

/**
 * Anuncio flotante de próximos eventos, anclado arriba a la derecha del área de
 * contenido.
 *
 * Vive fuera del `when` de pantallas, así que **permanece al cambiar de sección** y
 * conserva su estado: si alguien lo pliega en el catálogo, sigue plegado al volver a
 * inicio. Se dibuja dentro del área de contenido y no sobre toda la ventana para no
 * taparle la barra de navegación ni el pie.
 *
 * En pantallas estrechas arranca plegado en una burbuja: en un celular una tarjeta fija
 * competiría con el catálogo, que es justo lo que hay que evitar.
 *
 * @param onVerMas abre la pantalla de Eventos.
 * @param onDismiss cierre definitivo; el ViewModel lo recuerda en el navegador.
 */
@Composable
fun BoxScope.FloatingEventsWidget(
    events: List<EventItem>,
    onVerMas: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (events.isEmpty()) return

    BoxWithConstraints(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(PassionTheme.spacing.s4),
    ) {
        val compact = maxWidth < COMPACT_MAX_WIDTH
        // El estado del plegado sobrevive a los cambios de pantalla porque este
        // composable nunca sale de la composición. `compact` es la clave para que al
        // girar el teléfono o cambiar el tamaño de la ventana se reevalúe el arranque.
        var expanded by remember(compact) { mutableStateOf(!compact) }

        if (expanded) {
            EventsCard(
                events = events,
                compact = compact,
                onVerMas = onVerMas,
                onCollapse = { expanded = false },
                onDismiss = onDismiss,
            )
        } else {
            EventsBubble(count = events.size, onClick = { expanded = true })
        }
    }
}

@Composable
private fun EventsCard(
    events: List<EventItem>,
    compact: Boolean,
    onVerMas: () -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = PassionTheme.spacing.s2,
        modifier = Modifier.widthIn(max = CARD_WIDTH).width(CARD_WIDTH),
    ) {
        Column(modifier = Modifier.padding(PassionTheme.spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Nuestros próximos eventos",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // En estrecho, la primera acción es plegar —recuperable— y no cerrar.
                if (compact) {
                    IconAction(symbol = "–", description = "Plegar", onClick = onCollapse)
                    Spacer(Modifier.width(PassionTheme.spacing.s1))
                }
                IconAction(symbol = "✕", description = "Cerrar", onClick = onDismiss)
            }

            events.take(MAX_VISIBLE).forEach { event ->
                EventRow(event = event, onVerMas = onVerMas)
            }

            if (events.size > MAX_VISIBLE) {
                Text(
                    text = "y ${events.size - MAX_VISIBLE} más",
                    color = PassionTheme.semantics.onBackgroundSubtle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = PassionTheme.spacing.s2)
                        .clickable(onClick = onVerMas),
                )
            }
        }
    }
}

@Composable
private fun EventRow(event: EventItem, onVerMas: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.titulo,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (event.fechaCorta.isNotBlank()) {
                Text(
                    text = event.fechaCorta,
                    color = PassionTheme.semantics.onBackgroundSubtle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = "ver más",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(onClick = onVerMas)
                .padding(
                    horizontal = PassionTheme.spacing.s2,
                    vertical = PassionTheme.spacing.s1,
                ),
        )
    }
}

/** Forma plegada: ocupa lo mínimo y sigue anunciando que hay algo. */
@Composable
private fun EventsBubble(count: Int, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = PassionTheme.spacing.s2,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = if (count > 1) "Eventos · $count" else "Eventos",
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                horizontal = PassionTheme.spacing.s3,
                vertical = PassionTheme.spacing.s2,
            ),
        )
    }
}

@Composable
private fun IconAction(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = description }
            .clip(MaterialTheme.shapes.extraLarge)
            .background(PassionTheme.semantics.overlayWeak)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
