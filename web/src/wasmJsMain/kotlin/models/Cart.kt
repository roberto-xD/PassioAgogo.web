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
    Domicilio("domicilio", "Envío a domicilio"),
    Personal("personal", "Recoger personalmente"),
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
    // El pago (39_pagos.sql). El cliente las lee y no las escribe: las pone el webhook
    // de Mercado Pago con service_role, después de comprobar la firma y de volver a
    // preguntarle a Mercado Pago cuánto se pagó.
    @SerialName("pago_estado") val pagoEstado: String? = null,
    @SerialName("pago_total") val pagoTotal: Double? = null,
    @SerialName("pago_metodo") val pagoMetodo: String? = null,
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
    /** Número de rastreo. Vacío mientras el pedido no se haya convertido en paquete. */
    val guiaNumero: String = "",
    val guiaRastreoUrl: String = "",
    val guiaPaqueteria: String = "",
    /** Vacío mientras no se haya abierto ningún cobro. Ver [EstadoPago]. */
    val pagoEstado: String = "",
    /** Lo que Mercado Pago dice que se cobró. Puede no coincidir con [total]. */
    val pagoTotal: Double = 0.0,
    val pagoMetodo: String = "",
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
