package ui.pgmodels

data class PGDataCard(
    val productTittle: String = "",
    val productDescription: String = "",
    val productStore: String = "",
    val productRealPrice: String = "",
    val productDiscountPrice: String = "",
    val productCode: String = "",
    val hasOffert: Boolean = false,
    val urlImage: String = "",
)
