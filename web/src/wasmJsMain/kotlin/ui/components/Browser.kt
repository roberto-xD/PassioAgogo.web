package ui.components

import kotlinx.browser.window

/**
 * Abre una URL en una pestaña nueva.
 *
 * `noopener` evita que la página destino pueda manipular la nuestra vía `window.opener`.
 */
fun openInNewTab(url: String): Unit = js("window.open(url, '_blank', 'noopener')")

/** `true` si el destino sale del sitio; el resto son rutas internas (`/catalogo`). */
fun isExternalUrl(url: String): Boolean =
    url.startsWith("http://") || url.startsWith("https://")

/**
 * Navega a una ruta interna del sitio.
 *
 * La app usa hash routing, así que basta con cambiar el hash: `rememberScreenState`
 * escucha `hashchange` y cambia de pantalla sin recargar la página.
 */
fun navigateToInternal(path: String) {
    window.location.hash = if (path.startsWith("#")) path else "#$path"
}
