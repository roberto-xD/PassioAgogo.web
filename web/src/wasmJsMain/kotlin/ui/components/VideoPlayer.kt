package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.w3c.dom.HTMLElement

/**
 * Reproductor de video reutilizable.
 *
 * Se apoya en [HtmlElementView]: crea un `<video>` nativo del navegador y lo superpone al
 * canvas de Compose siguiendo el hueco que le asigne el [modifier]. Al ser nativo, trae
 * gratis los controles, el streaming por rangos, los subtítulos y la pantalla completa.
 *
 * Colócalo donde quieras dándole el tamaño con el modifier, por ejemplo:
 * ```
 * VideoPlayer(
 *     url = MediaConfig.presentacionUrl(),
 *     modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
 * )
 * ```
 *
 * @param url         URL del archivo (Supabase Storage o cualquier URL absoluta).
 * @param posterUrl   imagen mostrada antes de reproducir.
 * @param autoPlay    los navegadores solo permiten autoplay si el video está silenciado,
 *                    por eso [muted] se activa con él por defecto.
 * @param controls    controles nativos del navegador.
 */
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    posterUrl: String? = null,
    autoPlay: Boolean = false,
    muted: Boolean = autoPlay,
    loop: Boolean = false,
    controls: Boolean = true,
) {
    HtmlElementView(
        factory = { createHtmlElement("video") },
        modifier = modifier,
        onUpdate = { element ->
            // Solo se reasigna si cambió: al escribir src el navegador reinicia la carga.
            if (element.getAttribute("src") != url) {
                element.setAttribute("src", url)
            }
            posterUrl?.let { element.setAttribute("poster", it) }
            element.setAttribute("preload", "metadata")
            // Evita que iOS abra el video a pantalla completa automáticamente.
            element.toggleAttribute("playsinline", true)
            element.toggleAttribute("controls", controls)
            element.toggleAttribute("autoplay", autoPlay)
            element.toggleAttribute("muted", muted)
            element.toggleAttribute("loop", loop)

            element.style.setProperty("object-fit", "contain")
            element.style.setProperty("background-color", "#000")
            element.style.setProperty("border-radius", "12px")
        },
    )
}

/** Atributos booleanos de HTML: valen por presencia, no por valor. */
private fun HTMLElement.toggleAttribute(name: String, enabled: Boolean) {
    if (enabled) setAttribute(name, "") else removeAttribute(name)
}
