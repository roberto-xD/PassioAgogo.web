package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DTOs alineados al esquema real de Supabase (01_catalog.sql / 05_promotions.sql).
 *
 * Las consultas usan embedding de PostgREST: `categories(...)` llega como objeto
 * (FK many-to-one) y `product_variants(...)` / `promotion_targets(...)` como arreglo
 * (one-to-many). Todos los campos tienen default para tolerar columnas ausentes.
 */
@Serializable
data class ProductDto(
    val id: String? = null,
    val nombre: String? = null,
    /** Texto corto para la tarjeta del catálogo. */
    val resumen: String? = null,
    /** Texto largo: no se muestra en la tarjeta, pero sí alimenta la búsqueda. */
    val descripcion: String? = null,
    val marca: String? = null,
    val activo: Boolean = true,
    @SerialName("sobre_pedido") val sobrePedido: Boolean = false,
    val imagenes: List<String> = emptyList(),
    /**
     * Pares clave-valor libres del producto: `{"material": "silicona"}`. Los rótulos y
     * emojis con los que se pintan no están aquí, sino en [AttributePresetDto].
     */
    val attributes: JsonObject? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("categories") val categoria: CategoryDto? = null,
    @SerialName("product_variants") val variantes: List<VariantDto> = emptyList(),
)

@Serializable
data class CategoryDto(
    val nombre: String? = null,
)

@Serializable
data class VariantDto(
    val id: String? = null,
    val sku: String? = null,
    @SerialName("precio_venta") val precioVenta: Double? = null,
    val activo: Boolean? = null,
)

/** Fila mínima de categories: jerarquía (promos/filtro) + nombre para los chips. */
@Serializable
data class CategoryRefDto(
    val id: String? = null,
    val nombre: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
)

@Serializable
data class PromotionDto(
    val id: String? = null,
    val tipo: String? = null, // porcentaje | monto_fijo | precio_especial
    val valor: Double? = null,
    @SerialName("promotion_targets") val targets: List<PromotionTargetDto> = emptyList(),
)

@Serializable
data class PromotionTargetDto(
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("variant_id") val variantId: String? = null,
)

/**
 * Diccionario que convierte un par clave-valor de `attributes` en una chip legible
 * (19_attribute_presets). Sin él solo tendríamos el texto crudo del jsonb.
 *
 * Los campos no son nulos en la base; se les da valor por defecto para tolerar una fila
 * incompleta sin tumbar la deserialización del resto del catálogo.
 */
@Serializable
data class AttributePresetDto(
    val clave: String = "",
    val valor: String = "",
    /** Rótulo de la chip: "Silicona". */
    val identificador: String = "",
    val emoji: String? = null,
    val orden: Int = 0,
)

/** Resultado completo de una carga de catálogo. */
data class CatalogBundle(
    val products: List<ProductDto> = emptyList(),
    val promotions: List<PromotionDto> = emptyList(),
    val categoryRefs: List<CategoryRefDto> = emptyList(),
    val attributePresets: List<AttributePresetDto> = emptyList(),
)
