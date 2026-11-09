package models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Un renglón del carrito.
 *
 * Guarda el nombre y el precio además del identificador de variante porque el carrito
 * vive en el navegador y tiene que poder pintarse antes de que llegue el catálogo. El
 * precio es **informativo**: al convertir la solicitud en pedido, la base recotiza con
 * la promoción vigente de ese día.
 */
@Serializable
data class CartItem(
    val variantId: String,
    val nombre: String,
    val sku: String = "",
    val precio: Double = 0.0,
    val cantidad: Int = 1,
    /** Para volver a la ficha desde el carrito. */
    val slug: String = "",
    val imagen: String = "",
) {
    val subtotal: Double get() = precio * cantidad
}

/** El carrito entero, tal y como se guarda en el navegador. */
@Serializable
data class Cart(val items: List<CartItem> = emptyList()) {
    val total: Double get() = items.sumOf { it.subtotal }
    val unidades: Int get() = items.sumOf { it.cantidad }
}

/**
 * Cómo quiere recibir el pedido quien lo solicita.
 *
 * El valor que viaja a la base es [clave], no el nombre de la constante: así renombrar
 * algo aquí no cambia lo que ya está guardado en `order_requests`.
 */
enum class MetodoEntrega(val clave: String, val etiqueta: String) {
    Domicilio("domicilio", "A domicilio"),
    Personal("personal", "Personalmente"),
}

/**
 * Fila que se inserta en `order_requests` al enviar (23_order_requests.sql).
 *
 * `total_estimado` no se manda: lo calcula un trigger a partir de los artículos, y
 * mandarlo solo daría pie a que el total del cliente y el de la base discrepen.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NuevaSolicitudDto(
    @SerialName("cliente_nombre") val clienteNombre: String,
    @SerialName("cliente_telefono") val clienteTelefono: String? = null,
    @SerialName("cliente_email") val clienteEmail: String? = null,
    val notas: String? = null,
    @SerialName("created_by") val createdBy: String,
    // A nulo no se serializa, y por eso el campo se declara así: en una base donde
    // 32_metodo_entrega.sql todavía no se ha corrido, mandar «metodo_entrega: null»
    // sería tan rechazado como mandar un valor. El repositorio reintenta con nulo y la
    // elección se va anotada en las notas.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("metodo_entrega") val metodoEntrega: String? = null,
    // El envío elegido (35_order_requests_envio.sql). Mismo trato que el método: a nulo
    // no se serializa, así que una base sin esas columnas no rechaza la solicitud.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("envio_paqueteria") val envioPaqueteria: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("envio_servicio") val envioServicio: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("envio_descripcion") val envioDescripcion: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("envio_costo") val envioCosto: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("envio_entrega") val envioEntrega: String? = null,
    // El código de la ruleta (43_codigo_descuento). Se manda el código y nada más: el
    // porcentaje y el importe los pone `crear-pago` tras volver a validarlo.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("codigo_descuento") val codigoDescuento: String? = null,
    // La dirección va copiada, no referenciada: si el cliente se muda después, el
    // pedido viejo tiene que conservar adónde se envió de verdad. Se copia el sobre
    // tal cual, así que enviar no necesita la clave.
    @SerialName("dir_version") val dirVersion: Int? = null,
    @SerialName("dir_contenido") val dirContenido: String? = null,
    @SerialName("dir_iv") val dirIv: String? = null,
    @SerialName("dir_dk_usuario") val dirDkUsuario: String? = null,
    @SerialName("dir_iv_usuario") val dirIvUsuario: String? = null,
    @SerialName("dir_salt_usuario") val dirSaltUsuario: String? = null,
    @SerialName("dir_kdf_iteraciones") val dirKdfIteraciones: Int? = null,
    @SerialName("dir_dk_admin") val dirDkAdmin: String? = null,
    @SerialName("dir_admin_key_id") val dirAdminKeyId: String? = null,
)

@Serializable
data class NuevoItemDto(
    @SerialName("request_id") val requestId: String,
    @SerialName("variant_id") val variantId: String,
    val cantidad: Int,
    @SerialName("precio_estimado") val precioEstimado: Double,
)

/** Lo que devuelve la base tras insertar, y lo que lista el historial. */
@Serializable
data class SolicitudDto(
    val id: String? = null,
    val folio: Long? = null,
    val estado: String? = null,
    @SerialName("total_estimado") val totalEstimado: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("motivo_rechazo") val motivoRechazo: String? = null,
    // La guía, cuando ya existe (36_order_requests_guia). El cliente ve su número y su
    // rastreo; el PDF de la etiqueta no, que es para imprimir en la tienda.
    @SerialName("guia_numero") val guiaNumero: String? = null,
    @SerialName("guia_rastreo_url") val guiaRastreoUrl: String? = null,
    @SerialName("guia_paqueteria") val guiaPaqueteria: String? = null,
    // El envío que eligió el cliente (35_order_requests_envio). Su costo no está en
    // `total_estimado`: ese lo calcula un trigger a partir de los artículos.
    @SerialName("envio_costo") val envioCosto: Double? = null,
    @SerialName("envio_descripcion") val envioDescripcion: String? = null,
    // El envío promocional (48_codigos.sql).
    @SerialName("envio_gratis") val envioGratis: Boolean = false,
    // El pago (39_pagos.sql). El cliente las lee y no las escribe: las pone el webhook
    // de Mercado Pago con service_role, después de comprobar la firma y de volver a
    // preguntarle a Mercado Pago cuánto se pagó.
    @SerialName("pago_estado") val pagoEstado: String? = null,
    @SerialName("pago_total") val pagoTotal: Double? = null,
    @SerialName("pago_metodo") val pagoMetodo: String? = null,
    @SerialName("pago_marca") val pagoMarca: String? = null,
    @SerialName("pago_ultimos4") val pagoUltimos4: String? = null,
    @SerialName("pago_referencia") val pagoReferencia: String? = null,
    @SerialName("order_request_items") val items: List<ItemSolicitudDto> = emptyList(),
)

@Serializable
data class ItemSolicitudDto(
    val cantidad: Int = 0,
    @SerialName("precio_estimado") val precioEstimado: Double = 0.0,
)

/** Solicitud lista para pintarse en el historial. */
data class Solicitud(
    val id: String,
    val folio: Long,
    val estado: String,
    /** Solo los artículos: es lo que calcula el trigger de la base. */
    val totalArticulos: Double,
    val fecha: String,
    val unidades: Int,
    val motivoRechazo: String,
    val envioCosto: Double = 0.0,
    val envioDescripcion: String = "",
    /** El envío lo cubrió un código: no se cobró y la paquetería la escogió la tienda. */
    val envioGratis: Boolean = false,
    /** Número de rastreo. Vacío mientras el pedido no se haya convertido en paquete. */
    val guiaNumero: String = "",
    val guiaRastreoUrl: String = "",
    val guiaPaqueteria: String = "",
    /** Vacío mientras no se haya abierto ningún cobro. Ver [EstadoPago]. */
    val pagoEstado: String = "",
    /** Lo que Mercado Pago dice que se cobró. Puede no coincidir con [total]. */
    val pagoTotal: Double = 0.0,
    val pagoMetodo: String = "",
    /** La marca: `master`, `visa`… Más reconocible que la clase. */
    val pagoMarca: String = "",
    /** Los cuatro últimos de la tarjeta. Vacío si no pagó con una. */
    val pagoUltimos4: String = "",
    val pagoReferencia: String = "",
) {
    val tieneGuia: Boolean get() = guiaNumero.isNotBlank()
    val tieneEnvio: Boolean get() = envioCosto > 0.0

    val pago: EstadoPago get() = EstadoPago.desde(pagoEstado)

    /**
     * ¿Tiene sentido ofrecerle pagar?
     *
     * No cuando ya está pagada, ni cuando la tienda no pudo atenderla: cobrar algo que
     * se rechazó obliga a devolverlo. Un cobro pendiente **sí** se puede reintentar —es
     * justo el caso de quien cerró la pestaña a medias—, y un rechazo también, que para
     * eso se reintenta una tarjeta.
     */
    val puedePagar: Boolean
        get() = pago != EstadoPago.Aprobado &&
            pago != EstadoPago.Reembolsado &&
            estado != "rechazada" &&
            total > 0.0

    /**
     * Lo que de verdad va a pagar, artículos más envío.
     *
     * El trigger de la base solo suma artículos, así que enseñar `total_estimado` a
     * secas daba una cifra que no era la que el cliente vio al enviar la solicitud.
     */
    val total: Double get() = totalArticulos + envioCosto

    /** Cómo se le cuenta el estado a quien la envió, no cómo se llama en la base. */
    val estadoLegible: String get() = when (estado) {
        "enviada" -> "En revisión"
        "atendida" -> "Atendida"
        "rechazada" -> "No pudimos atenderla"
        else -> estado
    }
}

/**
 * En qué va el cobro.
 *
 * Las mismas cinco palabras que guarda la base (39_pagos.sql), más [Ninguno] para
 * cuando todavía no se ha abierto ningún cobro. Mercado Pago distingue muchos más
 * estados; esos se guardan sin traducir en `pagos.estado_crudo`, que es donde sirven.
 */
/**
 * Con qué se pagó, en una línea: «Mastercard ···· 4821», «Efectivo», «Tarjeta».
 *
 * Vive aquí y no en las pantallas porque lo enseñan dos —el acuse y el historial— y dos
 * copias de la misma idea acaban diciendo cosas distintas sobre el mismo pedido.
 *
 * La marca va por delante de la clase cuando se conoce: «Mastercard ···· 4821» se
 * reconoce de un vistazo, mientras que «Tarjeta de crédito ···· 4821» obliga a pensar
 * cuál era. Vacío cuando no sabemos nada, para que quien llama decida si callar.
 */
fun etiquetaDePago(marca: String, metodo: String, ultimos4: String): String {
    val nombre = marcaDePago(marca).ifBlank { claseDePago(metodo) }
    if (nombre.isBlank()) return ""
    val cola = ultimos4.takeIf { it.isNotBlank() }?.let { " ···· $it" }.orEmpty()
    return nombre + cola
}

/**
 * La marca con el nombre que se lee en la tarjeta.
 *
 * Vacío cuando no aporta nada por sí sola —`oxxo` ya lo dice todo con la clase
 * «Efectivo»— y entonces se cae a la clase.
 */
private fun marcaDePago(marca: String): String = when (marca.lowercase()) {
    "master", "debmaster", "mastercard" -> "Mastercard"
    "visa", "debvisa" -> "Visa"
    "amex" -> "American Express"
    "carnet" -> "Carnet"
    else -> ""
}

/**
 * La clase de medio de pago, en palabras.
 *
 * Lo que no esté en la lista se enseña tal cual antes que esconderlo: a quien revisa su
 * pedido, un `pec` raro le dice más que un hueco.
 */
private fun claseDePago(clave: String): String = when (clave.lowercase()) {
    "" -> ""
    "credit_card" -> "Tarjeta de crédito"
    "debit_card" -> "Tarjeta de débito"
    "prepaid_card" -> "Tarjeta prepagada"
    "account_money" -> "Saldo en Mercado Pago"
    "ticket", "oxxo", "paycash" -> "Efectivo"
    "bank_transfer", "spei" -> "Transferencia"
    // Las marcas sueltas, por si llegan en el campo de la clase.
    "visa", "master", "amex", "debvisa", "debmaster" -> "Tarjeta"
    else -> clave
}

/**
 * ¿Se pagó con una tarjeta?
 *
 * Se mira la **clase** y no la marca: la clase es justo la pregunta —`credit_card`
 * frente a `ticket`— mientras que con la marca habría que mantener la lista de todas
 * las que existen y acertar siempre. Lo que se lee en el estado de cuenta solo tiene
 * sentido decírselo a quien va a tener un estado de cuenta.
 */
fun esPagoConTarjeta(metodo: String): Boolean =
    metodo.lowercase() in setOf("credit_card", "debit_card", "prepaid_card")

enum class EstadoPago(val clave: String, val etiqueta: String) {
    Ninguno("", "Sin pagar"),
    Pendiente("pendiente", "Pago en proceso"),
    Aprobado("aprobado", "Pagado"),
    Rechazado("rechazado", "Pago rechazado"),
    Reembolsado("reembolsado", "Reembolsado"),
    Cancelado("cancelado", "Pago cancelado");

    companion object {
        fun desde(clave: String?): EstadoPago =
            entries.firstOrNull { it.clave == clave?.trim() } ?: Ninguno
    }
}
