package ui.pgmodels

/**
 * Rasgo del producto listo para pintarse: material, color, talla…
 *
 * El emoji es opcional; una chip sin él se pinta solo con el rótulo.
 */
data class PGAttributeChip(
    val label: String,
    val emoji: String = "",
)

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
    /** Rasgos informativos; solo aparecen en la ficha, no en la tarjeta. */
    val attributes: List<PGAttributeChip> = emptyList(),
)
