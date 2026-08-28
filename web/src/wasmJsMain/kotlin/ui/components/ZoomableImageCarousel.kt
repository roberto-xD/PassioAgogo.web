package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.theme.PassionTheme

/** Pausa entre imágenes del pase automático. */
private const val AUTO_ADVANCE_MS = 4_000L

/**
 * Carrusel de imágenes que pasan de una en una y se pueden acercar.
 *
 * Es el carrusel de la ficha de producto, extraído para que la ficha de evento se
 * comporte igual sin duplicar su lógica, que no es poca: pase automático que se detiene
 * al pasar el cursor, se reinicia al navegar a mano y se para mientras hay una imagen
 * acercada; flechas y puntos que se retiran con el zoom porque taparían el detalle que se
 * está mirando; y la pista de que la imagen se puede acercar.
 */
@Composable
fun ZoomableImageCarousel(
    images: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { images.size })
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val scope = rememberCoroutineScope()

    // Se incrementa cada vez que la persona navega a mano. Al ser clave del efecto, la
    // cuenta atrás vuelve a empezar: si no, tras avanzar con las flechas podía quedar muy
    // poco tiempo del ciclo en curso y la imagen saltaba sola casi de inmediato.
    var manualNavigation by remember { mutableStateOf(0) }

    // Mientras una imagen está acercada, el carrusel no se mueve: ni por gesto ni solo.
    // Arrastrar tiene que desplazar la foto, y el pase automático llevársela de debajo
    // justo cuando se está mirando un detalle sería lo más molesto posible.
    var zoomed by remember { mutableStateOf(false) }

    // El efecto se rearranca al cambiar la pausa o al navegar a mano, nunca durante su
    // propia animación: así no puede cancelarse a sí mismo a media transición.
    LaunchedEffect(images.size, isHovered, manualNavigation, zoomed) {
        if (images.size < 2 || isHovered || zoomed) return@LaunchedEffect
        while (true) {
            delay(AUTO_ADVANCE_MS)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % images.size)
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .hoverable(hoverInteraction),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomableImage(
                model = images[page],
                contentDescription = contentDescription,
                onZoomChange = { zoomed = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Pista de que la imagen se puede acercar. Se retira mientras está acercada: ahí
        // ya no informa de nada y solo taparía parte de lo que se quiere ver.
        if (!zoomed) {
            ZoomHint(modifier = Modifier.align(Alignment.BottomEnd))
        }

        if (images.size > 1 && !zoomed) {
            CarouselArrow(
                symbol = "‹",
                modifier = Modifier.align(Alignment.CenterStart),
                onClick = {
                    manualNavigation++
                    scope.launch {
                        val previous = (pagerState.currentPage - 1 + images.size) % images.size
                        pagerState.animateScrollToPage(previous)
                    }
                },
            )
            CarouselArrow(
                symbol = "›",
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = {
                    manualNavigation++
                    scope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage + 1) % images.size)
                    }
                },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(PassionTheme.spacing.s3),
                horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s1),
            ) {
                repeat(images.size) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (active) 9.dp else 7.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(Color.White.copy(alpha = if (active) 0.95f else 0.45f))
                            .clickable {
                                manualNavigation++
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                    )
                }
            }
        }
    }
}
