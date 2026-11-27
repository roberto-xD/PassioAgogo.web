package ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import network.MediaConfig
import androidx.compose.runtime.remember
import ui.components.CatalogBanner
import ui.components.ImageCarousel
import ui.components.PodcastBlock
import ui.components.RuletaBanner
import ui.components.prefiereMenosMovimiento
import ui.theme.PassionTheme
import viewmodel.GalleryUiState

/**
 * La portada.
 *
 * El encabezado es [CatalogBanner]: una pieza con marco en lugar de cuatro cosas
 * sueltas flotando sobre el fondo.
 *
 * Recoge además dos cosas que antes tenían entrada propia en la barra: el podcast, que ahora
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PassionTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(PassionTheme.spacing.s8))

        // El encabezado, recogido en una pieza.
        //
        // Antes eran cuatro cosas sueltas flotando sobre el fondo —logotipo, titular,
        // párrafo y botón—; ahora es un banner con su marco, hermano del de la ruleta
        // que va justo debajo. Lleva dentro el mismo texto y el mismo destino.
        CatalogBanner(
            onClick = onExploreCatalog,
            modifier = Modifier.fillMaxWidth(),
        )

        // El banner de la ruleta, debajo del de catálogo: primero la tienda, después
        // la promoción.
        if (ruletaVisible) {
            Spacer(Modifier.height(PassionTheme.spacing.s8))
            RuletaBanner(
                onClick = onOpenWheel,
                // Veinticuatro segundos de giro en bucle es justo lo que la
                // preferencia de menos movimiento existe para evitar.
                spinEnabled = !remember { prefiereMenosMovimiento() },
                hubImageUrl = MediaConfig.ruletaHubUrl,
                modifier = Modifier.fillMaxWidth(),
            )
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

        Spacer(Modifier.height(PassionTheme.spacing.s12))
    }
}
