package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lo que necesita la pantalla de «gracias» para contar un pedido entero.
 *
 * Va aparte de [Solicitud] —la del historial— porque pide cosas que allí no hacen falta:
 * los artículos con su nombre, el sobre de la dirección, el método de entrega. Meterlo
 * todo en [Solicitud] cargaría cada tarjeta del historial con datos que no enseña.
 */
data class Acuse(
    val id: String,
    val folio: Long,
    val fecha: String,
    val estado: String,
    val entrega: MetodoEntrega,
    /** Artículos, sin envío. Es lo que calcula el trigger de la base. */
    val totalArticulos: Double,
    val envioCosto: Double,
    val envioDescripcion: String,
    /** La promesa de la paquetería con sus palabras: «2-4 días». */
    val envioEntrega: String,
    val pagoEstado: String,
    val pagoTotal: Double,
    val pagoMetodo: String,
    val pagoReferencia: String,
    /** Los cuatro últimos de la tarjeta. Vacío si no pagó con una. */
    val pagoUltimos4: String,
    /** La marca: `master`, `visa`, `oxxo`… Más reconocible que la clase. */
    val pagoMarca: String = "",
    val articulos: List<ArticuloAcuse>,
    /** El sobre cifrado tal cual se copió al enviar. Nulo si va a recoger en persona. */
    val sobre: network.SobreDireccion? = null,
) {
    val pago: EstadoPago get() = EstadoPago.desde(pagoEstado)

    val unidades: Int get() = articulos.sumOf { it.cantidad }

    val tieneEnvio: Boolean get() = envioCosto > 0.0

    /** Lo que se va a pagar. Tras pagar, lo que Mercado Pago dice que se cobró. */
    val total: Double
        get() = if (pagoTotal > 0.0) pagoTotal else totalArticulos + envioCosto

    /**
     * ¿Seguimos esperando a saber?
     *
     * Volver de Mercado Pago no significa que ya sepamos el resultado: quien lo escribe
     * es el webhook, que llega por su cuenta y tarda segundos. Mientras el pago esté
     * pendiente y no haya referencia, lo honesto es decir que se está confirmando — la
     * referencia solo la pone el webhook, así que su ausencia es justo «todavía no nos
     * han contado nada».
     */
    val confirmando: Boolean
        get() = pago == EstadoPago.Pendiente && pagoReferencia.isBlank()
}

/**
 * Un producto que se sugiere al final del acuse.
 *
 * Se eligen a mano, por SKU, en `site_settings.acuse_recomendados`. Son pocos y cambian
 * poco: un motor de recomendaciones para esto sería construir una máquina para mover
 * una silla.
 */
data class Recomendado(
    val nombre: String,
    val categoria: String,
    val precio: Double,
    val imagen: String,
    /** Para abrir su ficha: `#/seccion/<seccion>/<slug>`. */
    val slug: String,
    val seccion: String,
)

/**
 * Un renglón del pedido.
 *
 * El nombre se trae pero la pantalla lo enseña plegado, detrás de un «Ver detalle». Es
 * la misma razón por la que la guía declara «Artículos de higiene personal» y el cargo
 * sale como `PAG`: esta pantalla se abre sola al volver de pagar, y puede haber alguien
 * al lado. Que lo enseñe quien quiera enseñarlo.
 */
data class ArticuloAcuse(
    val nombre: String,
    val cantidad: Int,
    val precio: Double,
) {
    val subtotal: Double get() = precio * cantidad
}

/** La fila de `order_requests` con todo lo que pide el acuse. */
@Serializable
data class AcuseDto(
    val id: String? = null,
    val folio: Long? = null,
    val estado: String? = null,
    @SerialName("total_estimado") val totalEstimado: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("metodo_entrega") val metodoEntrega: String? = null,
    @SerialName("envio_costo") val envioCosto: Double? = null,
    @SerialName("envio_descripcion") val envioDescripcion: String? = null,
    @SerialName("envio_entrega") val envioEntrega: String? = null,
    @SerialName("pago_estado") val pagoEstado: String? = null,
    @SerialName("pago_total") val pagoTotal: Double? = null,
    @SerialName("pago_metodo") val pagoMetodo: String? = null,
    @SerialName("pago_referencia") val pagoReferencia: String? = null,
    @SerialName("pago_ultimos4") val pagoUltimos4: String? = null,
    @SerialName("pago_marca") val pagoMarca: String? = null,
    // El sobre de la dirección, copiado al enviar. Se descifra en el navegador con la
    // clave de la persona; la base nunca lo ve en claro.
    @SerialName("dir_version") val dirVersion: Int? = null,
    @SerialName("dir_contenido") val dirContenido: String? = null,
    @SerialName("dir_iv") val dirIv: String? = null,
    @SerialName("dir_dk_usuario") val dirDkUsuario: String? = null,
    @SerialName("dir_iv_usuario") val dirIvUsuario: String? = null,
    @SerialName("dir_salt_usuario") val dirSaltUsuario: String? = null,
    @SerialName("dir_kdf_iteraciones") val dirKdfIteraciones: Int? = null,
    @SerialName("dir_dk_admin") val dirDkAdmin: String? = null,
    @SerialName("dir_admin_key_id") val dirAdminKeyId: String? = null,
    @SerialName("order_request_items") val items: List<ItemAcuseDto> = emptyList(),
)

/**
 * Un renglón con su variante.
 *
 * El precio que se enseña es `precio_estimado`, el que la persona vio al pedir — no el
 * del catálogo de hoy. Para enseñar un recibo es el correcto: es lo que aceptó. (Para
 * **cobrar** no sirve, y por eso `crear-pago` lo ignora y suma desde `product_variants`.)
 */
@Serializable
data class ItemAcuseDto(
    val cantidad: Int = 0,
    @SerialName("precio_estimado") val precioEstimado: Double = 0.0,
    @SerialName("product_variants") val variante: VarianteAcuseDto? = null,
)

@Serializable
data class VarianteAcuseDto(
    val titulo: String? = null,
    val sku: String? = null,
    val products: ProductoAcuseDto? = null,
)

@Serializable
data class ProductoAcuseDto(val nombre: String? = null)

/** Un producto del catálogo, para la sección de sugerencias. */
@Serializable
data class ProductoRecomendadoDto(
    val nombre: String? = null,
    val imagenes: List<String> = emptyList(),
    val activo: Boolean? = null,
    @SerialName("categories") val categoria: ProductoAcuseDto? = null,
    @SerialName("product_variants") val variantes: List<VarianteRecomendadaDto> = emptyList(),
)

@Serializable
data class VarianteRecomendadaDto(
    val sku: String? = null,
    @SerialName("precio_venta") val precioVenta: Double? = null,
    val activo: Boolean? = null,
)
