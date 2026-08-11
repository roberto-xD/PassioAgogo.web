package network

/**
 * Canal de contacto por WhatsApp para la ficha de producto.
 *
 * El enlace `wa.me` abre una conversación con el número de la tienda y el mensaje ya
 * escrito, listo para que la persona solo pulse enviar. No envía nada por sí solo: es el
 * usuario quien decide.
 */
object WhatsAppConfig {
    /**
     * Número en **formato internacional, solo dígitos**: sin `+`, espacios ni guiones.
     * Por ejemplo, para México: `52` + los diez dígitos.
     */
    const val NUMERO: String = "" // TODO: número de la tienda

    /** Mensaje base; al final se añade el nombre del artículo. */
    const val MENSAJE_BASE: String = "me interesó este artículo del catálogo"

    val isConfigured: Boolean
        get() = NUMERO.isNotBlank() && NUMERO.all { it.isDigit() }

    /**
     * Enlace a la conversación con el mensaje ya redactado.
     *
     * Se incluye el nombre del artículo para que quien atienda sepa de cuál se trata sin
     * tener que preguntarlo.
     */
    fun enlaceParaProducto(nombreProducto: String): String {
        val mensaje = if (nombreProducto.isBlank()) {
            MENSAJE_BASE
        } else {
            "$MENSAJE_BASE: $nombreProducto"
        }
        return "https://wa.me/$NUMERO?text=${encodeUriComponent(mensaje)}"
    }
}

/** Codifica el mensaje para que acentos y espacios viajen bien en la URL. */
private fun encodeUriComponent(value: String): String = js("encodeURIComponent(value)")
