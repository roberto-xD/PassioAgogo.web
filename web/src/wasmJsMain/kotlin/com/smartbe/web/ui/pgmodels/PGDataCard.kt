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

/**
 * Una presentación concreta del producto: la que se pide y la que se cobra.
 *
 * [etiqueta] es lo que distingue a esta variante de sus hermanas —«Chocolate · 30 ml»—,
 * sacada de sus propios `attributes`. Cuando no hay con qué distinguirlas se cae en el
 * SKU, que al menos es único.
 */
data class PGVariant(
    val id: String,
    val sku: String = "",
    val etiqueta: String = "",
    /** Precio final, ya con la promoción aplicada. Es el que va al carrito. */
    val precio: Double = 0.0,
    /** Precio de lista formateado; solo se enseña tachado cuando hay oferta. */
    val precioLista: String = "",
    val precioFinal: String = "",
    val hasOffert: Boolean = false,
)

data class PGDataCard(
    val productTittle: String = "",
    /**
     * Fragmento de URL derivado del nombre: `#/seccion/lenceria/conjunto-de-encaje`.
     * Solo lo usa la ficha de sección, que es la que tiene ruta propia.
     */
    val slug: String = "",
    /** Resumen corto: lo que muestra la tarjeta. */
    val productDescription: String = "",
    /** Descripción larga: solo se muestra en la ficha ampliada. */
    val productDetail: String = "",
    val productBrand: String = "",
    /** Nombre de la categoría. La ficha de sección lo muestra sobre el título. */
    val productCategory: String = "",
    val productStore: String = "",
    val productRealPrice: String = "",
    val productDiscountPrice: String = "",
    val productCode: String = "",
    /**
     * Variante concreta que se pediría al añadir al carrito: la más barata de las
     * activas, que es justo la del precio que muestra la tarjeta. Vacía si el producto
     * no tiene ninguna, en cuyo caso no se puede pedir.
     */
    val variantId: String = "",
    /** El mismo precio que [productDiscountPrice] o [productRealPrice], sin formatear. */
    val precioEstimado: Double = 0.0,
    val hasOffert: Boolean = false,
    /** Se consigue por encargo aunque no esté disponible en tienda. */
    val sobrePedido: Boolean = false,
    /** Primera imagen; la usa la tarjeta del catálogo. */
    val urlImage: String = "",
    /** Todas las imágenes, para el carrusel de la ficha. */
    val images: List<String> = emptyList(),
    /** Rasgos informativos; solo aparecen en la ficha, no en la tarjeta. */
    val attributes: List<PGAttributeChip> = emptyList(),
    /**
     * Presentaciones disponibles, de la más barata a la más cara. Con más de una, la
     * ficha enseña un selector; con una sola no hay nada que elegir.
     */
    val variants: List<PGVariant> = emptyList(),
)
