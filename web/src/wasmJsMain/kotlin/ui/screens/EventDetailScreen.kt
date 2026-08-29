package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import models.EventItem
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.ZoomableImageCarousel
import ui.components.isExternalUrl
import ui.components.navigateToInternal
import ui.components.openInNewTab
import ui.theme.PassionTheme
import viewmodel.EventsUiState

/** Proporción del carrusel. Algo más alto que la portada: aquí la imagen es protagonista. */
private const val GALLERY_RATIO = 4f / 3f

/** Rótulo del botón cuando la base no trae uno propio. */
private const val ENLACE_TEXTO_POR_DEFECTO = "Más información"

/**
 * Ficha de un evento, con ruta propia (`#/eventos/<id>`).
 *
 * Al tener URL, la ficha se puede compartir y el botón de atrás del navegador devuelve al
 * listado. Reutiliza el carrusel de la ficha de producto, con su pase automático, sus
 * flechas y su acercamiento por doble toque.
 */
@Composable
fun EventDetailScreen(
    state: EventsUiState,
    eventId: String?,
    onBack: () -> Unit,
) {
    val event = state.find(eventId)

    ContentScreen(title = event?.titulo ?: "Evento") {
        Text(
            text = "‹ Volver a eventos",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(onClick = onBack)
                .padding(
                    horizontal = PassionTheme.spacing.s2,
                    vertical = PassionTheme.spacing.s1,
                ),
        )

        when {
            state.isLoading -> Paragraph("Cargando evento…")

            event == null -> Paragraph(
                "No encontramos este evento. Puede que ya no esté disponible."
            )

            else -> EventBody(event)
        }
    }
}

@Composable
private fun EventBody(event: EventItem) {
    if (event.imagenes.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PassionTheme.spacing.s3)
                .aspectRatio(GALLERY_RATIO)
                .clip(MaterialTheme.shapes.medium),
        ) {
            ZoomableImageCarousel(
                images = event.imagenes,
                contentDescription = event.titulo,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (event.esPasado) {
        Text(
            text = "Este evento ya ocurrió.",
            color = PassionTheme.semantics.onBackgroundSubtle,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = PassionTheme.spacing.s4),
        )
    }

    Column(modifier = Modifier.padding(top = PassionTheme.spacing.s4)) {
        // La pregunta cambia de tiempo verbal según el evento haya pasado o no, y la
        // hora solo se anuncia cuando aún se puede llegar: en algo ya celebrado, a qué
        // hora empezó no le sirve a nadie.
        Text(
            text = if (event.esPasado) "¿Cuándo fue?" else "¿Cuándo será?",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = event.fechaSolo,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = PassionTheme.spacing.s1),
        )
        if (!event.esPasado && event.horaSolo.isNotBlank()) {
            Text(
                text = "A partir de las ${event.horaSolo}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (event.fechaFinSolo.isNotBlank()) {
            Text(
                text = "Hasta el ${event.fechaFinSolo}",
                color = PassionTheme.semantics.onBackgroundSubtle,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = PassionTheme.spacing.s1),
            )
        }

        DatoDelEvento(
            rotulo = "Dónde",
            valor = event.lugar,
            modifier = Modifier.padding(top = PassionTheme.spacing.s4),
        )
    }

    // Lo largo si lo hay; si no, al menos el resumen, que es lo que el listado enseñaba.
    val cuerpo = event.detalles.ifBlank { event.resumen }
    if (cuerpo.isNotBlank()) {
        Text(
            text = cuerpo,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s4),
        )
    }

    if (event.enlace.isNotBlank()) {
        Button(
            onClick = {
                if (isExternalUrl(event.enlace)) openInNewTab(event.enlace)
                else navigateToInternal(event.enlace)
            },
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.padding(top = PassionTheme.spacing.s5),
        ) {
            Text(
                // El rótulo lo eliges en la base; si está vacío, uno genérico.
                text = event.enlaceTexto.ifBlank { ENLACE_TEXTO_POR_DEFECTO },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Una línea «rótulo: valor». No se pinta si el valor está vacío. */
@Composable
private fun DatoDelEvento(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    if (valor.isBlank()) return

    Column(modifier = modifier) {
        Text(
            text = rotulo.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = valor,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
