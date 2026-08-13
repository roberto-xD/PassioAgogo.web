package network

/**
 * Acceso al almacenamiento local del navegador.
 *
 * Todo va envuelto en `runCatching` porque `localStorage` lanza cuando el navegador lo
 * tiene bloqueado —modo privado estricto, cookies denegadas, iframes con restricciones—.
 * Es una preferencia de presentación: si no se puede guardar, la web sigue funcionando
 * como si la persona no hubiera cerrado nada.
 */
object BrowserStorage {
    fun read(key: String): String? =
        runCatching { getItem(key).takeIf { it.isNotBlank() } }.getOrNull()

    fun write(key: String, value: String) {
        runCatching { setItem(key, value) }
    }
}

private fun getItem(key: String): String = js("window.localStorage.getItem(key) || ''")

private fun setItem(key: String, value: String): Unit = js("window.localStorage.setItem(key, value)")
