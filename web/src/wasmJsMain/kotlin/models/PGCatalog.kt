package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import network.SupabaseConfig
import ui.pgmodels.PGDataCard
import kotlin.math.roundToLong

/**
 * DTOs alineados al esquema real de Supabase (01_catalog.sql).
 *
 * La consulta usa embedding de PostgREST: `categories(...)` llega como objeto
 * (FK many-to-one) y `product_variants(...)` como arreglo (one-to-many).
 * Todos los campos tienen default para tolerar columnas ausentes.
 */
@Serializable
data class ProductDto(
    val id: String? = null,
    val nombre: String? = null,
    val descripcion: String? = null,
    val marca: String? = null,
    val imagenes: List<String> = emptyList(),
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

fun ProductDto.toPGDataCard(): PGDataCard {
    // Precio a mostrar: el menor entre las variantes activas.
    val variantesActivas = variantes.filter { it.activo != false }
    val precio = variantesActivas.mapNotNull { it.precioVenta }.minOrNull()

    return PGDataCard(
        productTittle = nombre.orEmpty(),
        productDescription = descripcion.orEmpty(),
        productStore = marca ?: categoria?.nombre.orEmpty(),
        productRealPrice = precio?.let(::formatPrice).orEmpty(),
        productDiscountPrice = "", // promociones: se integrarán en una iteración posterior
        productCode = variantesActivas.firstOrNull()?.sku.orEmpty(),
        hasOffert = false,
        urlImage = imagenes.firstOrNull()?.let(SupabaseConfig::publicImageUrl).orEmpty(),
    )
}

/** Formatea 1234.5 como "$1,234.50" no está disponible en wasm; usamos "$1234.50". */
private fun formatPrice(value: Double): String {
    val cents = (value * 100).roundToLong()
    val entero = cents / 100
    val dec = (cents % 100).toString().padStart(2, '0')
    return "$$entero.$dec"
}
