package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import models.GallerySlide
import ui.theme.PassionTheme

/** Por debajo de este ancho no caben imagen e información una al lado de la otra. */
private val SIDE_BY_SIDE_MIN_WIDTH = 720.dp

/**
 * Diálogo con la imagen ampliada y su información extendida.
 *
 * Ocupa un porcentaje de la ventana en lugar del ancho por defecto de la plataforma
 * (`usePlatformDefaultWidth = false`), y la imagen se muestra con [ContentScale.Fit] para
 * verla completa: aquí el objetivo es apreciar el detalle, no llenar el marco.
 *
 * La información va **al lado** de la imagen, de modo que se lea sin desplazarse. En
 * ventanas estrechas —donde dos columnas quedarían ilegibles— se apila debajo.
 */
@Composable
fun GalleryDetailDialog(slide: GallerySlide, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 1100.dp)
                .fillMaxHeight(0.88f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    if (maxWidth >= SIDE_BY_SIDE_MIN_WIDTH) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            SlideImage(
                                slide = slide,
                                modifier = Modifier
                                    .weight(1.5f)
                                    .fillMaxHeight(),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(PassionTheme.spacing.s6),
                            ) {
                                SlideInfo(slide, onDismiss)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            SlideImage(
                                slide = slide,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp, max = 420.dp),
                            )
                            Column(modifier = Modifier.padding(PassionTheme.spacing.s6)) {
                                SlideInfo(slide, onDismiss)
                            }
                        }
                    }
                }

                // Botón de cierre, siempre visible sobre la esquina superior derecha.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(PassionTheme.spacing.s3)
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✕",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideImage(slide: GallerySlide, modifier: Modifier = Modifier) {
    // Fondo oscuro tras la imagen: al no recortarla quedan franjas a los lados, y así se
    // integran con la fotografía en lugar de destacar.
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
