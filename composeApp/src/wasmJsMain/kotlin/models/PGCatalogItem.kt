package models

import kotlinx.serialization.Serializable

@Serializable
data class PGCatalog(
    val productList: ArrayList<PGCatalogItem>,
)
@Serializable
data class PGCatalogItem(
    val category: String,
    val code: String,
    val description: String,
    val hasOffer: Boolean,
    val id: String,
    val image: String,
    val isActive: Boolean,
    val price_discount: Double,
    val price_discount_label: String,
    val price_label: String,
    val price_normal: Double,
    val price_normal_label: String,
    val store: String,
    val title: String,
    val url: String
)