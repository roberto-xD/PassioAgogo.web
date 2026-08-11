package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import models.GallerySlide
import ui.theme.PassionTheme

/**
 * Diálogo con la imagen ampliada de la galería y su información extendida.
 *
 * La disposición (imagen al lado del texto, apilada en ventanas estrechas, botón de
 * cierre) la resuelve [DetailDialogShell], que comparte con la ficha de producto.
 */
@Composable
fun GalleryDetailDialog(slide: GallerySlide, onDismiss: () -> Unit) {
    DetailDialogShell(
        onDismiss = onDismiss,
        imagePane = { modifier ->
            // Fondo oscuro tras la imagen: al no recortarla quedan franjas a los lados, y
            // así se integran con la fotografía en lugar de destacar.
            Box(
                modifier = modifier.background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = slide.imageUrl,
                    contentDescription = slide.titulo.ifBlank { "Imagen ampliada" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        infoPane = { SlideInfo(slide, onDismiss) },
    )
}

@Composable
private fun ColumnScope.SlideInfo(slide: GallerySlide, onDismiss: () -> Unit) {
    if (slide.categoria.isNotBlank()) {
        Text(
            text = slide.categoria.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
    Text(
        text = slide.titulo,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = PassionTheme.spacing.s1),
    )
    if (slide.descripcion.isNotBlank()) {
        Text(
            text = slide.descripcion,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s3),
        )
    }
    if (slide.detalles.isNotBlank()) {
        Text(
            text = slide.detalles,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s4),
        )
    }
    if (slide.ctaUrl.isNotBlank()) {
        Spacer(Modifier.height(PassionTheme.spacing.s6))
        Button(
            onClick = {
                if (isExternalUrl(slide.ctaUrl)) {
                    // Destino externo: se abre aparte y el diálogo sigue disponible al
                    // volver a la pestaña.
                    openInNewTab(slide.ctaUrl)
                } else {
                    // Ruta interna: hay que cerrar antes de navegar, o la pantalla
                    // destino quedaría oculta tras el diálogo.
                    onDismiss()
                    navigateToInternal(slide.ctaUrl)
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(slide.ctaLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}
