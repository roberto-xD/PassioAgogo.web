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
    val total: Double,
    val fecha: String,
    val unidades: Int,
    val motivoRechazo: String,
    /** Número de rastreo. Vacío mientras el pedido no se haya convertido en paquete. */
    val guiaNumero: String = "",
    val guiaRastreoUrl: String = "",
    val guiaPaqueteria: String = "",
) {
    val tieneGuia: Boolean get() = guiaNumero.isNotBlank()

    /** Cómo se le cuenta el estado a quien la envió, no cómo se llama en la base. */
    val estadoLegible: String get() = when (estado) {
        "enviada" -> "En revisión"
        "atendida" -> "Atendida"
        "rechazada" -> "No pudimos atenderla"
        else -> estado
    }
}
