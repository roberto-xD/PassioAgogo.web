package ui.components

import kotlinx.browser.window

/**
 * Abre una URL en una pestaña nueva.
 *
 * `noopener` evita que la página destino pueda manipular la nuestra vía `window.opener`.
 */
fun openInNewTab(url: String): Unit = js("window.open(url, '_blank', 'noopener')")

/**
 * Copia un texto al portapapeles. Devuelve `false` si el navegador no lo permitió.
 *
 * Se intenta primero la API moderna, que solo existe en contextos seguros (https o
 * localhost). Donde no está —una prueba servida por http, por ejemplo— se recurre al
 * `execCommand` de siempre: está obsoleto, pero es lo único que funciona ahí y sigue
 * soportado en todos los navegadores actuales.
 *
 * El resultado se devuelve para poder avisar de verdad: decir «copiado» cuando no se
 * copió es peor que no decir nada, porque la persona se va creyendo que lo tiene.
 */
fun copyToClipboard(text: String): Boolean = clipboardWrite(text)

private fun clipboardWrite(text: String): Boolean = js(
    """(function (t) {
        try {
            if (navigator.clipboard && window.isSecureContext) {
                navigator.clipboard.writeText(t);
                return true;
            }
            var a = document.createElement('textarea');
            a.value = t;
            a.style.position = 'fixed';
            a.style.top = '-1000px';
            a.style.opacity = '0';
            document.body.appendChild(a);
            a.focus();
            a.select();
            var ok = document.execCommand('copy');
            document.body.removeChild(a);
            return ok;
        } catch (e) {
            return false;
        }
    })(text)"""
)

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
