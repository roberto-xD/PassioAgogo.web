package ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage

/** Tope de acercamiento. Más allá, una foto de catálogo solo muestra sus propios píxeles. */
private const val MAX_ZOOM = 4f

/** A cuánto salta el doble toque. Suficiente para leer una etiqueta sin perder el contexto. */
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Imagen que se puede acercar con dos dedos o con doble toque, y arrastrar mientras está
 * acercada.
 *
 * Pensada sobre todo para el móvil, donde la imagen se ve pequeña y hay etiquetas y
 * textos de producto que no se leen a tamaño natural.
 *
 * **Convive con un carrusel.** El gesto solo se captura cuando hay dos dedos —o cuando la
 * imagen ya está acercada—; con un dedo y a tamaño natural los eventos siguen su camino
 * hacia quien contenga a esta imagen. Sin esa cautela, `detectTransformGestures` se
 * quedaría con todos los arrastres y pasar de foto dejaría de funcionar.
 *
 * @param onZoomChange avisa al pasar de tamaño natural a acercada y al revés. Quien la usa
 *   lo necesita para soltarle el gesto horizontal al carrusel y para parar cualquier
 *   automatismo que pudiera llevarse la imagen mientras se mira un detalle.
 */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onZoomChange: (Boolean) -> Unit = {},
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Los gestos capturan su lambda una sola vez; sin esto la referencia se quedaría
    // anclada a la primera composición.
    val avisar by rememberUpdatedState(onZoomChange)

    // Cambiar de foto la devuelve a su tamaño: heredar el acercamiento de la anterior
    // dejaría la nueva recortada por un encuadre que no le corresponde.
    LaunchedEffect(model) {
        scale = 1f
        offset = Offset.Zero
        avisar(false)
    }

    /**
     * Ajusta escala y desplazamiento a lo que tiene sentido: no se aleja más allá del
     * tamaño natural, no se acerca más allá del tope, y el desplazamiento nunca despega
     * la imagen de los bordes dejando huecos.
     */
    fun aplicar(nuevaEscala: Float, nuevoOffset: Offset) {
        val estabaAcercada = scale > 1f
        val s = nuevaEscala.coerceIn(1f, MAX_ZOOM)
        if (s <= 1f) {
            scale = 1f
            offset = Offset.Zero
        } else {
            val maxX = size.width * (s - 1f) / 2f
            val maxY = size.height * (s - 1f) / 2f
            scale = s
            offset = Offset(
                nuevoOffset.x.coerceIn(-maxX, maxX),
                nuevoOffset.y.coerceIn(-maxY, maxY),
            )
        }
        if ((scale > 1f) != estabaAcercada) avisar(scale > 1f)
    }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            // Dos `pointerInput` separados: cada detector consume por su cuenta, así que
            // compartir uno dejaría al otro sin enterarse.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val evento = awaitPointerEvent()
                        val dedos = evento.changes.count { it.pressed }
                        // La condición que hace que esto conviva con el carrusel: con un
                        // solo dedo y sin acercar, no se toca nada y el evento sigue su
                        // camino.
                        if (dedos > 1 || scale > 1f) {
                            val zoom = evento.calculateZoom()
                            val arrastre = evento.calculatePan()
                            val centroide = evento.calculateCentroid(useCurrent = false)
                            if (zoom != 1f || arrastre != Offset.Zero) {
                                val nuevaEscala = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                val centro = Offset(size.width / 2f, size.height / 2f)
                                // Se acerca hacia el punto entre los dedos: así lo que se
                                // está mirando permanece debajo de ellos en vez de
                                // escaparse hacia el centro.
                                val factor = nuevaEscala / scale
                                val nuevoOffset = (offset + centro - centroide) * factor -
                                    centro + centroide + arrastre
                                aplicar(nuevaEscala, nuevoOffset)
                                evento.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (evento.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { punto ->
                        if (scale > 1f) {
                            aplicar(1f, Offset.Zero)
                        } else {
                            // Lleva al centro el punto tocado, que es lo que se quiere ver.
                            val centro = Offset(size.width / 2f, size.height / 2f)
                            aplicar(DOUBLE_TAP_ZOOM, (centro - punto) * DOUBLE_TAP_ZOOM)
                        }
                    },
                )
            },
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
