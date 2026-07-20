package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val descripcion: String? = null,
    val marca: String? = null,
    val imagenes: List<String> = emptyList(),
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

/** Fila mínima de categories para reconstruir la jerarquía (promos por categoría). */
@Serializable
data class CategoryRefDto(
    val id: String? = null,
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

/** Resultado completo de una carga de catálogo. */
data class CatalogBundle(
    val products: List<ProductDto> = emptyList(),
    val promotions: List<PromotionDto> = emptyList(),
    val categoryRefs: List<CategoryRefDto> = emptyList(),
)
