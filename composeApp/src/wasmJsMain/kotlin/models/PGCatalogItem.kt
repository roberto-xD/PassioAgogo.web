package models

import kotlinx.serialization.Serializable

@Serializable
data class PGCatalog(
    val items: ArrayList<PGCatalogItem>? = null,
    val amount: Int? = 0,
)
@Serializable
data class PGCatalogItem(
    val category: String? = "",
    val code: String? = "",
    val description: String? = "",
    val hasOffer: Boolean? = null,
    val id: String? = "",
    val image: String? = "",
    val isActive: Boolean? = null,
    val price_discount: Double? = null,
    val price_discount_label: String? = "",
    val price_label: String? = "",
    val price_normal: Double? = null,
    val price_normal_label: String? = "",
    val store: String? = "",
    val title: String? = "",
    val url: String? = "",
)