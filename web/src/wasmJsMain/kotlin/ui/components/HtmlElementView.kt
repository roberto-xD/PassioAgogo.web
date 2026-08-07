package ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import kotlin.math.max

/**
 * Puente entre Compose y el DOM.
 *
 * Compose para web dibuja todo en un `<canvas>`, así que un elemento HTML (video,
 * iframe, mapa…) no puede vivir dentro del árbol de composición: se crea en el DOM y se
 * **superpone** al canvas, copiando la posición y el tamaño que Compose midió para este
 * hueco. Es la misma técnica que usa el interop HTML de Compose Multiplatform.
 *
 * Limitación inherente: el elemento vive fuera del canvas, por lo que no lo recortan los
 * contenedores de Compose. Para que no se dibuje sobre la barra o el pie al hacer scroll,
 * se recorta contra [LocalHtmlOverlayClip].
 *
 * Idea de puente tomada de KMP-ShaPlayer (Apache 2.0); implementación propia.
 */
@Composable
fun HtmlElementView(
    factory: () -> HTMLElement,
    modifier: Modifier = Modifier,
    onUpdate: (HTMLElement) -> Unit = {},
) {
    val density = LocalDensity.current.density
    val clipBounds = LocalHtmlOverlayClip.current
    val element = remember { factory() }

    DisposableEffect(element) {
        element.style.setProperty("position", "absolute")
        element.style.setProperty("margin", "0px")
        // Sobre el canvas de Compose, pero por debajo del reto de Turnstile (1000).
        element.style.setProperty("z-index", "10")
        document.body?.appendChild(element)
        onDispose { element.remove() }
    }

    // Reaplica la configuración (src, controles…) cuando cambian los parámetros.
    SideEffect { onUpdate(element) }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            element.syncWith(coordinates.boundsInWindow(), density, clipBounds)
        }
    )
}

/** Crea el elemento con `js()` para no depender de un cast de tipos externos. */
fun createHtmlElement(tag: String): HTMLElement = js("document.createElement(tag)")

/**
 * Área visible para los elementos HTML superpuestos, en coordenadas de ventana.
 *
 * `null` = sin recorte. La app la fija al área de contenido (entre la barra y el pie),
 * de modo que un video dentro de una pantalla con scroll se recorta en vez de taparlas.
 */
val LocalHtmlOverlayClip = compositionLocalOf<Rect?> { null }

/** Copia el rectángulo medido por Compose al elemento, en píxeles CSS. */
private fun HTMLElement.syncWith(bounds: Rect, density: Float, clip: Rect?) {
    val visible = clip?.let { bounds.overlaps(it) } ?: true
    if (!visible || bounds.width <= 0f || bounds.height <= 0f) {
        style.setProperty("display", "none")
        return
    }
    style.setProperty("display", "block")

    // Compose mide en píxeles físicos; el DOM posiciona en píxeles CSS.
    style.setProperty("left", "${bounds.left / density}px")
    style.setProperty("top", "${bounds.top / density}px")
    style.setProperty("width", "${bounds.width / density}px")
    style.setProperty("height", "${bounds.height / density}px")

    if (clip == null) {
        style.removeProperty("clip-path")
        return
    }
    // inset(arriba derecha abajo izquierda): recorta lo que sobresale del área visible.
    val top = max(0f, clip.top - bounds.top) / density
    val right = max(0f, bounds.right - clip.right) / density
    val bottom = max(0f, bounds.bottom - clip.bottom) / density
    val left = max(0f, clip.left - bounds.left) / density
    style.setProperty("clip-path", "inset(${top}px ${right}px ${bottom}px ${left}px)")
}
