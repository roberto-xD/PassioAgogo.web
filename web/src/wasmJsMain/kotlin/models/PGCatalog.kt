package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ui.pgmodels.PGDataCard

/** Contenedor simple del catálogo para la UI (se construye desde la lista de Postgrest). */
data class PGCatalog(
    val items: List<PGCatalogItem> = emptyList(),
)

/**
 * Fila de la tabla de productos en Supabase.
 *
 * Los nombres deben coincidir con las columnas de tu tabla. Para columnas snake_case que
 * no calzan con el nombre del campo Kotlin, se usa @SerialName. Ajusta esto a tu esquema
 * final. Todos los campos tienen valor por defecto para tolerar columnas ausentes.
 */
@Serializable
data class PGCatalogItem(
    val id: String? = "",
    val category: String? = "",
    val code: String? = "",
    val title: String? = "",
    val description: String? = "",
    val store: String? = "",
    val image: String? = "",
    val url: String? = "",
    @SerialName("has_offer") val hasOffer: Boolean? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("price_normal") val priceNormal: Double? = null,
    @SerialName("price_normal_label") val priceNormalLabel: String? = "",
    @SerialName("price_discount") val priceDiscount: Double? = null,
    @SerialName("price_discount_label") val priceDiscountLabel: String? = "",
    @SerialName("price_label") val priceLabel: String? = "",
)

fun PGCatalogItem.toPGDataCard(): PGDataCard {
    return PGDataCard(
        productTittle = title.orEmpty(),
        productDescription = description.orEmpty(),
        productStore = store.orEmpty(),
        productRealPrice = priceNormalLabel.orEmpty(),
        productDiscountPrice = priceDiscountLabel.orEmpty(),
        productCode = code.orEmpty(),
        hasOffert = hasOffer ?: false,
        urlImage = image.orEmpty(),
    )
}
