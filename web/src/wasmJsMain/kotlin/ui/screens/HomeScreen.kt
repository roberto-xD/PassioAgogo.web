package ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import network.MediaConfig
import ui.components.ImageCarousel
import ui.components.PodcastBlock
import ui.theme.PassionTheme
import viewmodel.GalleryUiState

/**
 * La portada.
 *
 * Recoge dos cosas que antes tenían entrada propia en la barra: el podcast, que ahora
 * cuelga del final de la página, y la ruleta, que se abre desde su botón flotante. Las
 * dos siguen teniendo ruta (`#/podcast` lleva aquí; `#/ruleta` sigue siendo su
 * pantalla), pero ya no gastan sitio en una barra donde las últimas entradas no las
 * veía nadie.
 *
 * @param ruletaVisible la misma bandera `ruleta_visible` que decide si la promoción
 *   existe. Antes ocultaba la entrada de la barra; ahora oculta el botón. Apagarla
 *   sigue siendo lo único que hay que hacer para que la ruleta desaparezca del sitio.
 */
@Composable
fun HomeScreen(
    galleryState: GalleryUiState,
    onExploreCatalog: () -> Unit,
    ruletaVisible: Boolean = false,
    onOpenWheel: () -> Unit = {},
) {
    val semantics = PassionTheme.semantics

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PassionTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(PassionTheme.spacing.s12))

        if (MediaConfig.isLogoConfigured) {
            AsyncImage(
                model = MediaConfig.logoUrl,
                contentDescription = "Passion à gogo · tienda para adultos",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
            )
        } else {
            // Respaldo si el logotipo no está disponible: la tipografía script de la marca.
            Text(
                text = "Passion à gogo",
                color = MaterialTheme.colorScheme.primary,
                style = PassionTheme.type.scriptAccent,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(PassionTheme.spacing.s6))
        Text(
            text = "Tu catálogo, en un solo lugar",
            color = semantics.onBackgroundStrong,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PassionTheme.spacing.s6))
        Text(
            text = "Explora nuestro catálogo con calma y descubre las promociones vigentes, " +
                "reunidas en una sola experiencia.",
            color = semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(PassionTheme.spacing.s8))
        Button(onClick = onExploreCatalog, shape = MaterialTheme.shapes.extraLarge) {
            Text("Ver catálogo", style = MaterialTheme.typography.labelLarge)
        }

        if (galleryState.slides.isNotEmpty()) {
            Spacer(Modifier.height(PassionTheme.spacing.s12))
            Text(
                text = "Eventos, promociones y novedades",
                color = semantics.onBackgroundStrong,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PassionTheme.spacing.s4))
            ImageCarousel(slides = galleryState.slides)
        }

        // El podcast cierra la página: quien ha bajado hasta aquí ya vio el catálogo y
        // el carrusel, y es el momento en que apetece quedarse a escuchar algo.
        Spacer(Modifier.height(PassionTheme.spacing.s12))
        PodcastBlock(modifier = Modifier.fillMaxWidth())

        // Hueco para que el botón flotante no tape el final del contenido.
        Spacer(Modifier.height(PassionTheme.spacing.s12))
    }

        if (ruletaVisible) {
            ExtendedFloatingActionButton(
                onClick = onOpenWheel,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(PassionTheme.spacing.s5),
            ) {
                Text("Gira y gana", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
