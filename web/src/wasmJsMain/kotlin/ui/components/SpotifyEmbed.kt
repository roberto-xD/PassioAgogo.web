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
        factory = {
            // El navegador fija los permisos que delega al iframe en el momento en que
            // este NAVEGA, leyendo el atributo `allow` que exista entonces. Por eso se
            // configura aquí, antes de adjuntarlo al DOM y antes de asignar `src`: si se
            // pusiera después, la navegación ya habría ocurrido sin permisos y el
            // navegador bloquearía `encrypted-media`
            // ("Permissions policy violation: encrypted-media is not allowed").
            createHtmlElement("iframe").apply {
                setAttribute(
                    "allow",
                    "autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture",
                )
                setAttribute("allowfullscreen", "")
                setAttribute("loading", "lazy")
                setAttribute("frameborder", "0")
                style.setProperty("border", "0")
                style.setProperty("border-radius", "12px")
            }
        },
        modifier = modifier,
        onUpdate = { element ->
            // Único atributo reactivo: cambiarlo provoca una navegación nueva, que ya
            // hereda los permisos configurados arriba.
            val src = "https://open.spotify.com/embed/${content.path}/$id?utm_source=generator"
            if (element.getAttribute("src") != src) {
                element.setAttribute("src", src)
            }
        },
    )
}
