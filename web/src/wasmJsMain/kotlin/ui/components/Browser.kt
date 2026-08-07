package ui.components

/**
 * Abre una URL en una pestaña nueva.
 *
 * `noopener` evita que la página destino pueda manipular la nuestra vía `window.opener`.
 */
fun openInNewTab(url: String): Unit = js("window.open(url, '_blank', 'noopener')")
