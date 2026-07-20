package models

import network.SupabaseConfig
import ui.pgmodels.PGDataCard
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

        PGDataCard(
            productTittle = product.nombre.orEmpty(),
            productDescription = product.descripcion.orEmpty(),
            productStore = product.marca ?: product.categoria?.nombre.orEmpty(),
            productRealPrice = best?.second?.let(::formatPrice).orEmpty(),
            productDiscountPrice = if (hasOffer) formatPrice(best.third) else "",
            productCode = best?.first?.sku.orEmpty(),
            hasOffert = hasOffer,
            urlImage = product.imagenes.firstOrNull()
                ?.let(SupabaseConfig::publicImageUrl).orEmpty(),
        )
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
private fun formatPrice(value: Double): String {
    val cents = (value * 100).roundToLong()
    val entero = cents / 100
    val dec = (cents % 100).toString().padStart(2, '0')
    return "$$entero.$dec"
}
