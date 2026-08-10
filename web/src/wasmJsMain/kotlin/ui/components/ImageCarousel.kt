package ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import models.GallerySlide
import ui.theme.PassionTheme

/** Aumento de la tarjeta bajo el cursor. */
private const val HOVER_SCALE = 1.12f

/**
 * Carrusel de imágenes con desplazamiento automático continuo.
 *
 * Comportamiento:
 * - **Automático**: avanza a velocidad constante, fotograma a fotograma.
 * - **Manual**: la fila se arrastra con el ratón o el dedo, y las flechas laterales
 *   avanzan una tarjeta.
 * - **Al pasar el cursor**: el avance se detiene, la tarjeta apuntada crece ligeramente y
 *   se dibuja por encima de las demás ([zIndex]), mostrando su descripción. Al retirar el
 *   cursor, el avance se reanuda.
 * - **Al hacer clic**: se abre un diálogo con la imagen ampliada y su información
 *   extendida; el avance queda detenido mientras está abierto.
 *
 * La lista es circular: se recorren [Int.MAX_VALUE] posiciones tomando el elemento por
 * módulo, y se arranca por la mitad para poder arrastrar también hacia atrás.
 *
 * El escalado se hace con [graphicsLayer], que no afecta a la medición: la tarjeta crece
 * *sobre* sus vecinas en vez de empujarlas. Por eso la fila reserva algo más de alto que
 * la tarjeta, para que el aumento no quede recortado.
 */
@Composable
fun ImageCarousel(
    slides: List<GallerySlide>,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 300.dp,
    itemHeight: Dp = 200.dp,
    spacing: Dp = PassionTheme.spacing.s4,
    speedPxPerSecond: Float = 32f,
) {
    if (slides.isEmpty()) return

    val listState = rememberLazyListState(
        // Punto de partida centrado en un múltiplo del tamaño de la lista, para que la
        // primera tarjeta visible sea la primera del catálogo.
        initialFirstVisibleItemIndex = remember(slides.size) {
            val middle = Int.MAX_VALUE / 2
            middle - (middle % slides.size)
        }
    )

    // El hover se detecta en la fila completa y no sumando el de cada tarjeta: las
    // tarjetas entran y salen de composición sin parar, y llevar la cuenta de cuántas
    // están apuntadas se desincroniza con facilidad, dejando el carrusel detenido para
    // siempre. Con una única fuente de verdad eso no puede ocurrir.
    val rowInteraction = remember { MutableInteractionSource() }
    val isRowHovered by rowInteraction.collectIsHoveredAsState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()

    var selected by remember { mutableStateOf<GallerySlide?>(null) }
    val paused = isRowHovered || isDragged || selected != null

    // El bucle de animación no se reinicia con los cambios de pausa: vive mientras viva
    // el carrusel y simplemente omite el avance. Así no depende de que una clave vuelva a
    // su valor para reanudarse.
    val pausedNow by rememberUpdatedState(paused)
    LaunchedEffect(Unit) {
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val elapsedSeconds = (frame - previousFrame) / 1_000_000_000f
            previousFrame = frame
            if (!pausedNow) {
                listState.scrollBy(speedPxPerSecond * elapsedSeconds)
            }
        }
    }

    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { (itemWidth + spacing).toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(rowInteraction),
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                // Alto extra para que la tarjeta ampliada no se recorte.
                .height(itemHeight * HOVER_SCALE + PassionTheme.spacing.s2),
            contentPadding = PaddingValues(horizontal = PassionTheme.spacing.s4),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(count = Int.MAX_VALUE) { index ->
                val slide = slides[index % slides.size]
                CarouselCard(
                    slide = slide,
                    width = itemWidth,
                    height = itemHeight,
                    onClick = { selected = slide },
                )
            }
        }

        CarouselArrow(
            symbol = "‹",
            modifier = Modifier.align(Alignment.CenterStart),
            onClick = { scope.launch { listState.animateScrollBy(-stepPx) } },
        )
        CarouselArrow(
            symbol = "›",
            modifier = Modifier.align(Alignment.CenterEnd),
            onClick = { scope.launch { listState.animateScrollBy(stepPx) } },
        )
    }

    selected?.let { slide ->
        GalleryDetailDialog(slide = slide, onDismiss = { selected = null })
    }
}

@Composable
private fun CarouselCard(
    slide: GallerySlide,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    // Este hover solo controla el aspecto de la propia tarjeta; la pausa del carrusel se
    // decide en la fila, así que un desajuste aquí no puede detenerlo.
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) HOVER_SCALE else 1f,
        animationSpec = tween(durationMillis = 220),
    )

    Box(
        modifier = Modifier
            .zIndex(if (isHovered) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .width(width)
            .height(height)
            .clip(MaterialTheme.shapes.medium)
            .background(PassionTheme.semantics.imagePlaceholder)
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = slide.imageUrl,
            contentDescription = slide.titulo.ifBlank { "Imagen de la galería" },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Velo oscuro para que el texto sea legible sobre cualquier fotografía: va sobre
        // la imagen, así que no depende del tema claro u oscuro.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(height / 2)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(PassionTheme.spacing.s3),
        ) {
            if (slide.categoria.isNotBlank()) {
                Text(
                    text = slide.categoria.uppercase(),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                text = slide.titulo,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Al pasar el cursor se revela el resumen y la invitación a abrir el detalle.
            if (isHovered) {
                if (slide.descripcion.isNotBlank()) {
                    Text(
                        text = slide.descripcion,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = PassionTheme.spacing.s1),
                    )
                }
                if (slide.detalles.isNotBlank() || slide.ctaUrl.isNotBlank()) {
                    Text(
                        text = "Clic para ver más",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = PassionTheme.spacing.s1),
                    )
                }
            }
        }
    }
}

@Composable
private fun CarouselArrow(
    symbol: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(horizontal = PassionTheme.spacing.s1)
            .size(36.dp)
            .zIndex(2f)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
