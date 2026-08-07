package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Tipos de contenido que admite el reproductor incrustado de Spotify. */
enum class SpotifyContent(val path: String) {
    Track("track"),
    Album("album"),
    Playlist("playlist"),
    Artist("artist"),
    Show("show"),
    Episode("episode"),
}

/**
 * Reproductor incrustado de Spotify.
 *
 * Reutiliza [HtmlElementView]: el reproductor oficial de Spotify es un `<iframe>`, así que
 * el mismo puente que superpone el video sobre el canvas sirve aquí sin cambios (posición,
 * recorte al hacer scroll y limpieza incluidos).
 *
 * El alto lo fija el [modifier]; Spotify usa dos tamaños de referencia:
 * **152.dp** (compacto, una fila) y **352.dp** (tarjeta con portada y lista).
 *
 * Reproducción: para los visitantes anónimos o con cuenta gratuita el reproductor ofrece
 * **previews de ~30 segundos**. La reproducción completa requiere Premium con sesión
 * iniciada y, aun así, Spotify la restringe en iframes de dominios externos.
 *
 * @param id identificador del contenido: el código que aparece en el enlace de
 *   *Compartir → Copiar enlace* (`https://open.spotify.com/show/<id>`).
 */
@Composable
fun SpotifyEmbed(
    content: SpotifyContent,
    id: String,
    modifier: Modifier = Modifier,
) {
    HtmlElementView(
        factory = { createHtmlElement("iframe") },
        modifier = modifier,
        onUpdate = { element ->
            val src = "https://open.spotify.com/embed/${content.path}/$id?utm_source=generator"
            if (element.getAttribute("src") != src) {
                element.setAttribute("src", src)
            }
            // encrypted-media es obligatorio: sin él, el reproductor no sale del modo
            // preview ni para usuarios Premium.
            element.setAttribute(
                "allow",
                "autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture",
            )
            element.setAttribute("loading", "lazy")
            element.setAttribute("frameborder", "0")

            element.style.setProperty("border", "0")
            element.style.setProperty("border-radius", "12px")
        },
    )
}
