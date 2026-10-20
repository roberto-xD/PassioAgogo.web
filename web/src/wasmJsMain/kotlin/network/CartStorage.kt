package network

import kotlinx.browser.window
import kotlinx.serialization.json.Json
import models.Cart

/** Clave del carrito en el almacenamiento del navegador. */
private const val CLAVE = "passio_carrito_v1"

private val json = Json { ignoreUnknownKeys = true }

/**
 * El carrito guardado en el navegador.
 *
 * Vive aquí y no en la base hasta que se envía porque `order_requests` no tiene estado
 * de borrador: una fila creada al añadir el primer artículo ya aparecería como solicitud
 * pendiente en administración, y cada carrito abandonado sería una solicitud fantasma.
 *
 * El almacenamiento puede fallar —modo privado, permisos del sitio— y eso no debe tumbar
 * la página: si no se puede leer se empieza con el carrito vacío, y si no se puede
 * escribir el carrito simplemente no sobrevive a la recarga.
 */
object CartStorage {

    fun load(): Cart {
        val crudo = leer(CLAVE) ?: return Cart()
        return runCatching { json.decodeFromString(Cart.serializer(), crudo) }
            .getOrDefault(Cart())
    }

    fun save(cart: Cart) {
        runCatching { escribir(CLAVE, json.encodeToString(Cart.serializer(), cart)) }
    }

    fun clear() {
        runCatching { borrar(CLAVE) }
    }
}

private fun leer(clave: String): String? = js(
    """(function (k) {
        try { return window.localStorage.getItem(k); } catch (e) { return null; }
    })(clave)"""
)

private fun escribir(clave: String, valor: String) {
    js(
        """(function (k, v) {
            try { window.localStorage.setItem(k, v); } catch (e) {}
        })(clave, valor)"""
    )
}

private fun borrar(clave: String) {
    js(
        """(function (k) {
            try { window.localStorage.removeItem(k); } catch (e) {}
        })(clave)"""
    )
}
