@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

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

/**
 * Lleva **esta** pestaña a otra dirección.
 *
 * Para el pago, y no [openInNewTab], por dos razones. La primera es que no funcionaría:
 * la URL de Mercado Pago llega después de preguntarle a la Edge Function, y una pestaña
 * que se abre cuando ya pasó un rato desde el clic la bloquea el navegador por no venir
 * de un gesto. La segunda es que un cobro quiere una sola pestaña: Mercado Pago devuelve
 * a la persona aquí al terminar, y con dos pestañas abiertas acaba mirando la vieja, que
 * sigue diciendo que no ha pagado.
 */
fun goToExternal(url: String): Unit = js("window.location.assign(url)")

/**
 * Los parámetros de consulta de la URL actual, sin el `?`.
 *
 * Mercado Pago le pega los suyos al devolver el navegador. La app enruta por hash, así
 * que esto es lo único que hay que mirar para saber que venimos de un pago.
 */
fun currentQuery(): String = window.location.search.removePrefix("?")

/**
 * Un parámetro de la URL actual, o cadena vacía.
 *
 * Lo que saca de aquí es **texto de quien escribió la URL**, no un dato de confianza.
 * Sirve para saber *qué* buscar, nunca para dar algo por cierto: el `external_reference`
 * del regreso de Mercado Pago dice de qué pedido hablar, y el RLS se encarga de que
 * pedir uno ajeno no devuelva nada.
 */
fun queryParam(nombre: String): String {
    val crudo = currentQuery()
    if (crudo.isBlank()) return ""
    for (par in crudo.split("&")) {
        val i = par.indexOf('=')
        if (i <= 0) continue
        if (decodeUri(par.substring(0, i)) == nombre) return decodeUri(par.substring(i + 1))
    }
    return ""
}

private fun decodeUri(valor: String): String =
    js("decodeURIComponent(valor.replace(/\\+/g, ' '))")

/**
 * Borra los parámetros de consulta sin recargar ni añadir otra entrada al historial.
 *
 * Se limpian en cuanto se leen: dejarlos puestos hace que el botón de recargar vuelva a
 * parecer un regreso del pago, y que la URL que la persona comparta lleve dentro el id
 * de un pago suyo.
 */
fun clearQuery() {
    val limpia = window.location.pathname + window.location.hash
    window.history.replaceState(null, "", limpia)
}

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
