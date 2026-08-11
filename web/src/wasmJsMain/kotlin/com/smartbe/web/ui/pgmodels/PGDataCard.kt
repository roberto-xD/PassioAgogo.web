package ui.pgmodels

data class PGDataCard(
    val productTittle: String = "",
    /** Resumen corto: lo que muestra la tarjeta. */
    val productDescription: String = "",
    /** Descripción larga: solo se muestra en la ficha ampliada. */
    val productDetail: String = "",
    val productBrand: String = "",
    val productStore: String = "",
    val productRealPrice: String = "",
    val productDiscountPrice: String = "",
    val productCode: String = "",
    val hasOffert: Boolean = false,
    /** Se consigue por encargo aunque no esté disponible en tienda. */
    val sobrePedido: Boolean = false,
    /** Primera imagen; la usa la tarjeta del catálogo. */
    val urlImage: String = "",
    /** Todas las imágenes, para el carrusel de la ficha. */
    val images: List<String> = emptyList(),
)
