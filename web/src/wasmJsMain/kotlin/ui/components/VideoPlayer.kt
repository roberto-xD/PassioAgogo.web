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
        factory = {
            createHtmlElement("video").apply {
                setAttribute("preload", "metadata")
                // Evita que iOS abra el video a pantalla completa automáticamente.
                setAttribute("playsinline", "")
                style.setProperty("object-fit", "contain")
                style.setProperty("background-color", "#000")
                style.setProperty("border-radius", "12px")
            }
        },
        modifier = modifier,
        onUpdate = { element ->
            // La configuración se aplica ANTES que `src`: al asignar la fuente el
            // navegador empieza a cargarla y evalúa el estado actual del elemento. En
            // particular, sin `muted` presente en ese momento bloquea el autoplay.
            element.toggleAttribute("controls", controls)
            element.toggleAttribute("autoplay", autoPlay)
            element.toggleAttribute("muted", muted)
            element.toggleAttribute("loop", loop)
            posterUrl?.let { element.setAttribute("poster", it) }

            // Solo se reasigna si cambió: al escribir src el navegador reinicia la carga.
            if (element.getAttribute("src") != url) {
                element.setAttribute("src", url)
            }
        },
    )
}

/** Atributos booleanos de HTML: valen por presencia, no por valor. */
private fun HTMLElement.toggleAttribute(name: String, enabled: Boolean) {
    if (enabled) setAttribute(name, "") else removeAttribute(name)
}
