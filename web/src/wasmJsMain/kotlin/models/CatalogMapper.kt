package models

import network.SupabaseConfig
import network.slugify
import ui.pgmodels.PGDataCard
import ui.pgmodels.PGVariant
import kotlin.math.roundToLong

/**
 * Convierte el bundle del repositorio en tarjetas para la UI aplicando promociones.
 *
 * Las promos llegan ya filtradas por vigencia/activo desde el servidor. La BD garantiza
 * (08_business_triggers · fn_check_promo_overlap) que no hay dos promociones activas
 * traslapadas sobre la misma variante, así que a lo más una aplica; aun así se resuelve
 * por especificidad: variante > producto > categoría (con subcategorías, replicando la
 * jerarquía recursiva de fn_promotion_variants).
 */
/** Índice hijo-por-padre de la jerarquía de categorías. */
fun childrenIndex(refs: List<CategoryRefDto>): Map<String, List<String>> =
    refs.mapNotNull { ref -> ref.parentId?.let { parent -> ref.id?.let { parent to it } } }
        .groupBy({ it.first }, { it.second })

/** Una categoría y todas sus subcategorías (recursivo, como fn_promotion_variants). */
fun expandCategoryIds(rootId: String, childrenByParent: Map<String, List<String>>): Set<String> {
    val seen = mutableSetOf<String>()
    fun visit(id: String) {
        if (seen.add(id)) childrenByParent[id]?.forEach(::visit)
    }
    visit(rootId)
    return seen
}

fun buildCatalogCards(bundle: CatalogBundle): List<PGDataCard> {
    val childrenByParent = childrenIndex(bundle.categoryRefs)
    // Se indexa una vez para todo el catálogo, no una por producto.
    val presetsByPair = attributePresetIndex(bundle.attributePresets)

    fun expandCategory(rootId: String): Set<String> = expandCategoryIds(rootId, childrenByParent)

    data class PromoScope(
        val promo: PromotionDto,
        val variantIds: Set<String>,
        val productIds: Set<String>,
        val categoryIds: Set<String>,
    )

    val scopes = bundle.promotions.map { promo ->
        PromoScope(
            promo = promo,
            variantIds = promo.targets.mapNotNull { it.variantId }.toSet(),
            productIds = promo.targets.mapNotNull { it.productId }.toSet(),
            categoryIds = promo.targets.mapNotNull { it.categoryId }
                .flatMap(::expandCategory).toSet(),
        )
    }

    fun promoFor(product: ProductDto, variant: VariantDto): PromotionDto? =
        scopes.firstOrNull { variant.id != null && variant.id in it.variantIds }?.promo
            ?: scopes.firstOrNull { product.id != null && product.id in it.productIds }?.promo
            ?: scopes.firstOrNull { product.categoryId != null && product.categoryId in it.categoryIds }?.promo

    return bundle.products.map { product ->
        // Por variante activa: precio base y final con promo; se muestra la de menor final.
        val priced = product.variantes
            .filter { it.activo != false && it.precioVenta != null }
            .map { variant ->
                val base = variant.precioVenta!!
                Triple(variant, base, applyPromotion(base, promoFor(product, variant)))
            }
        val best = priced.minByOrNull { it.third }
        val hasOffer = best != null && best.third < best.second

        // Las presentaciones, de la más barata a la más cara: la primera es la que el
        // catálogo anuncia, así que es la que conviene traer seleccionada.
        val variants = etiquetarVariantes(
            priced.sortedBy { it.third }.map { (variant, base, final) ->
                val titulo = variant.titulo.orEmpty().trim()
                PGVariant(
                    id = variant.id.orEmpty(),
                    sku = variant.sku.orEmpty(),
                    titulo = titulo,
                    // El título escrito a mano manda; sin él se deduce de los atributos,
                    // que es como funcionaba antes de 29_variant_titulo.
                    etiqueta = titulo
                        .sinPrefijoDe(product.nombre.orEmpty())
                        .ifBlank {
                            buildAttributeChips(variant.attributes, presetsByPair)
                                .joinToString(" · ") { it.label }
                        },
                    precio = final,
                    precioLista = formatPrice(base),
                    precioFinal = formatPrice(final),
                    hasOffert = final < base,
                )
            }
        )

        PGDataCard(
            productTittle = product.nombre.orEmpty(),
            slug = slugify(product.nombre.orEmpty()).ifBlank { product.id.orEmpty() },
            // La tarjeta muestra el resumen; `descripcion` queda para la ficha ampliada.
            productDescription = product.resumen.orEmpty(),
            productDetail = product.descripcion.orEmpty(),
            productBrand = product.marca.orEmpty(),
            productCategory = product.categoria?.nombre.orEmpty(),
            productStore = product.marca ?: product.categoria?.nombre.orEmpty(),
            productRealPrice = best?.second?.let(::formatPrice).orEmpty(),
            productDiscountPrice = if (hasOffer) formatPrice(best.third) else "",
            productCode = best?.first?.sku.orEmpty(),
            variantId = best?.first?.id.orEmpty(),
            precioEstimado = best?.third ?: 0.0,
            hasOffert = hasOffer,
            sobrePedido = product.sobrePedido,
            urlImage = product.imagenes.firstOrNull()
                ?.let(SupabaseConfig::publicImageUrl).orEmpty(),
            images = product.imagenes.map(SupabaseConfig::publicImageUrl),
            attributes = buildAttributeChips(product.attributes, presetsByPair),
            variants = variants,
        )
    }
}

/**
 * Quita del título el nombre del producto cuando lo lleva delante.
 *
 * El título se siembra como «Aceite para masajes · Menta · 25 mL» para que sirva por sí
 * solo —en el carrito, en administración—, pero dentro de la ficha ese nombre ya está
 * escrito encima en grande: repetirlo en cada chip solo alarga la fila y entierra lo
 * único que distingue a una presentación de otra.
 */
private fun String.sinPrefijoDe(producto: String): String {
    val nombre = producto.trim()
    if (nombre.isEmpty() || !startsWith(nombre, ignoreCase = true)) return this
    return removeRange(0, nombre.length).trimStart(' ', '·', '-', ',').ifBlank { this }
}

/**
 * Asegura que no haya dos presentaciones con el mismo rótulo **y el mismo precio**.
 *
 * Mientras se distingan por el precio, dos rótulos iguales son legibles. Cuando ni eso,
 * la persona elegiría a ciegas, así que se numeran. No se recurre al SKU: es un código
 * de inventario y no significa nada para quien compra.
 */
private fun etiquetarVariantes(variantes: List<PGVariant>): List<PGVariant> {
    val repetidas = variantes
        .groupBy { it.etiqueta.lowercase() to it.precio }
        .filterValues { it.size > 1 }
        .keys

    var sinRotulo = 0
    val vistas = mutableMapOf<Pair<String, Double>, Int>()

    return variantes.map { v ->
        val clave = v.etiqueta.lowercase() to v.precio
        when {
            v.etiqueta.isBlank() -> v.copy(etiqueta = "Opción ${++sinRotulo}")
            clave in repetidas -> {
                val n = (vistas[clave] ?: 0) + 1
                vistas[clave] = n
                v.copy(etiqueta = "${v.etiqueta} ($n)")
            }
            else -> v
        }
    }
}

private fun applyPromotion(base: Double, promo: PromotionDto?): Double {
    val valor = promo?.valor ?: return base
    val result = when (promo.tipo) {
        "porcentaje" -> base * (1 - valor / 100)
        "monto_fijo" -> base - valor
        "precio_especial" -> valor
        else -> base
    }
    return maxOf(result, 0.0)
}

/** "$1234.50" — String.format no existe en wasm; formateo manual a 2 decimales. */
fun formatPrice(value: Double): String {
    val cents = (value * 100).roundToLong()
    val entero = cents / 100
    val dec = (cents % 100).toString().padStart(2, '0')
    return "$$entero.$dec"
}
