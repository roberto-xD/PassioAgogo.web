package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import models.EventItem
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.isExternalUrl
import ui.components.navigateToInternal
import ui.components.openInNewTab
import ui.theme.PassionTheme
import viewmodel.EventsUiState

/**
 * Próximos eventos.
 *
 * Versión inicial: lista lo que hay para que el «ver más» del widget flotante lleve a
 * algún sitio útil desde el primer día. El diseño definitivo está pendiente.
 */
@Composable
fun EventsScreen(state: EventsUiState) {
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

            else -> state.events.forEach { event -> EventCard(event) }
        }
    }
}

@Composable
private fun EventCard(event: EventItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s4),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = PassionTheme.spacing.s1),
    ) {
        Column(Modifier.padding(PassionTheme.spacing.s4)) {
            if (event.imagen.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 11f)
                        .clip(MaterialTheme.shapes.small)
                        .background(PassionTheme.semantics.imagePlaceholder),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = event.imagen,
                        contentDescription = event.titulo,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 11f),
                    )
                }
            }

            Text(
                text = event.titulo,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = PassionTheme.spacing.s3),
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

            // El texto largo si lo hay; si no, al menos el resumen del widget.
            val cuerpo = event.detalles.ifBlank { event.resumen }
            if (cuerpo.isNotBlank()) {
                Text(
                    text = cuerpo,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = PassionTheme.spacing.s3),
                )
            }

            if (event.enlace.isNotBlank()) {
                Button(
                    onClick = {
                        if (isExternalUrl(event.enlace)) openInNewTab(event.enlace)
                        else navigateToInternal(event.enlace)
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.padding(top = PassionTheme.spacing.s4),
                ) {
                    Text("Más información", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
