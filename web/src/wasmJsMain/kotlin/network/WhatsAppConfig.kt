@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

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
    const val NUMERO: String = "5513878451" // TODO: número de la tienda

    /** Mensaje base; al final se añade el nombre del artículo. */
    const val MENSAJE_BASE: String = "me interesó este artículo del catálogo"

    /** Mensaje de quien escribe desde el teléfono de contacto, sin producto de por medio. */
    const val MENSAJE_DUDA: String = "Hola, he visto la página pero tengo una duda"

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
        return enlace(mensaje)
    }

    /** Conversación abierta desde los datos de contacto, con la duda ya planteada. */
    fun enlaceParaDuda(): String = enlace(MENSAJE_DUDA)

    /**
     * Duda sobre un pedido concreto, desde el acuse o el historial.
     *
     * Lleva el código porque es lo que permite atender sin preguntar primero «¿cuál de
     * tus pedidos?». Va el código y no el folio ni el identificador interno: es lo que
     * la persona tiene en su pantalla, y con lo que administración puede buscarlo
     * (`fn_envios_admin(p_codigo := ...)`).
     */
    fun enlaceParaPedido(codigo: String): String =
        enlace("Hola, tengo una duda sobre mi pedido $codigo")

    /**
     * Cancelación de un pedido ya pagado o ya enviado.
     *
     * Va redactado como lo que es —una petición, no una queja— y con el código dentro,
     * porque quien atienda necesita poder abrir el pedido antes de contestar. Que la
     * persona no tenga que explicar desde cero lo que la pantalla ya sabe es la mitad
     * del trabajo de una conversación así.
     */
    fun enlaceParaCancelacion(codigo: String): String =
        enlace("Hola, quiero cancelar mi pedido $codigo")

    /**
     * Seguimiento de una devolución ya pedida.
     *
     * Distinto de [enlaceParaCancelacion] porque la conversación es otra: quien ya
     * canceló no viene a cancelar, viene a preguntar por su dinero. Abrir WhatsApp y
     * encontrar escrito lo que uno no quería decir acaba en que lo borra y escribe
     * «hola», y ahí se pierde el código.
     */
    fun enlaceParaDevolucion(codigo: String): String =
        enlace("Hola, cancelé mi pedido $codigo y quiero saber de mi devolución")

    /**
     * Reclamación de un premio de la ruleta.
     *
     * El código va en el mensaje porque es lo que se verifica contra `wheel_spins` al
     * entregarlo: sin él, la captura de pantalla de una ruleta no prueba nada.
     */
    fun enlaceParaPremio(rotulo: String, codigo: String): String =
        enlace("Gane $rotulo en la ruleta. Mi codigo es $codigo")

    private fun enlace(mensaje: String): String =
        "https://wa.me/$NUMERO?text=${encodeUriComponent(mensaje)}"
}

/** Codifica el mensaje para que acentos y espacios viajen bien en la URL. */
private fun encodeUriComponent(value: String): String = js("encodeURIComponent(value)")
