package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import models.EventItem
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.theme.PassionTheme
import viewmodel.EventsUiState

/** Proporción de la portada. Panorámica: es un cartel, no un retrato. */
private const val COVER_RATIO = 16f / 9f

/** Cuánto se atenúa la tarjeta de un evento que ya pasó. */
private const val PAST_ALPHA = 0.65f

/**
 * Listado de eventos.
 *
 * Próximos y pasados en la misma columna, en ese orden. Los pasados no se esconden en
 * otra pestaña: dan idea de lo que hace la tienda a quien llega por primera vez, y basta
 * con atenuarlos y etiquetarlos para que nadie los confunda con lo que está por venir.
 */
@Composable
fun EventsScreen(state: EventsUiState, onOpenEvent: (String) -> Unit) {
    ContentScreen(title = "Eventos") {
        when {
            state.isLoading -> Paragraph("Cargando eventos…")

            state.errorMessage != null -> Paragraph(
                "No pudimos cargar los eventos. Inténtalo de nuevo en un momento."
            )

            state.events.isEmpty() -> Paragraph(
                "Por ahora no tenemos eventos anunciados. Vuelve pronto: aquí " +
                    "publicaremos los próximos."
            )

            else -> Column(
                verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s4),
            ) {
                state.events.forEach { event ->
                    EventCard(event = event, onOpen = { onOpenEvent(event.id) })
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: EventItem, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // La tarjeta entera abre la ficha. El botón sigue ahí para quien lo busque,
            // pero nadie debería tener que apuntar a él.
            .clickable(onClick = onOpen)
            .alpha(if (event.esPasado) PAST_ALPHA else 1f),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = PassionTheme.spacing.s1),
    ) {
        if (event.portada.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(COVER_RATIO)
                    .background(PassionTheme.semantics.imagePlaceholder),
            ) {
                AsyncImage(
                    model = event.portada,
                    contentDescription = event.titulo,
                    // Recortada: todas las portadas ocupan lo mismo aunque las fotos
                    // vengan con proporciones distintas, y la columna no se descuadra.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                EventBadge(
                    esPasado = event.esPasado,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(PassionTheme.spacing.s3),
                )
            }
        }

        Column(Modifier.padding(PassionTheme.spacing.s4)) {
            if (event.portada.isBlank()) {
                EventBadge(
                    esPasado = event.esPasado,
                    modifier = Modifier.padding(bottom = PassionTheme.spacing.s2),
                )
            }

            Text(
                text = event.titulo,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (event.fechaLarga.isNotBlank()) {
                Text(
                    text = event.fechaLarga,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = PassionTheme.spacing.s1),
                )
            }

            if (event.lugar.isNotBlank()) {
                Text(
                    text = event.lugar,
                    color = PassionTheme.semantics.onBackgroundSubtle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Solo el resumen: el texto largo es lo que la ficha aporta al abrirse.
            if (event.resumen.isNotBlank()) {
                Text(
                    text = event.resumen,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = PassionTheme.spacing.s2),
                )
            }

            TextButton(
                onClick = onOpen,
                modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            ) {
                Text("Más información", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EventBadge(esPasado: Boolean, modifier: Modifier = Modifier) {
    val container = if (esPasado) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.primary
    val content = if (esPasado) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onPrimary

    Text(
        text = if (esPasado) "YA OCURRIÓ" else "PRÓXIMO",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(container)
            .padding(
                horizontal = PassionTheme.spacing.s2,
                vertical = PassionTheme.spacing.s1,
            ),
    )
}
