package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import models.AttributePresetDto
import models.CatalogBundle
import models.CategoryRefDto
import models.ProductDto
import models.PromotionDto

class CatalogRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
) {
    // Se construye solo cuando hay configuración real y se hace una consulta. Con los
    // placeholders el cliente de Supabase nunca se instancia.
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    /**
     * Catálogo completo: productos activos (con categoría y variantes), promociones
     * vigentes (con targets) y la jerarquía de categorías para resolver promos por
     * categoría/subcategoría.
     *
     * Se piden columnas explícitas — nunca `*` — para no exponer campos sensibles
     * como `product_variants.costo` en el tráfico del cliente. El RLS (10_rls.sql)
     * ya limita a `anon` a registros activos.
     */
    fun getCatalog(): Flow<NetworkResult<CatalogBundle>> = toResultFlow {
        if (!SupabaseConfig.isConfigured) {
            return@toResultFlow NetworkResult.Success(CatalogBundle())
        }

        val products = supabase.from(SupabaseConfig.PRODUCTS_TABLE)
            .select(
                columns = Columns.raw(
                    "id, nombre, resumen, descripcion, marca, imagenes, attributes, " +
                        "category_id, activo, sobre_pedido, " +
                        "categories(nombre), " +
                        "product_variants(id, sku, precio_venta, activo)"
                )
            ) {
                // Sin filtro de `activo` aquí: la visibilidad se decide en el ViewModel
                // (activo o sobre pedido), para que el resultado sea el mismo tanto para
                // un visitante anónimo como para un miembro del staff con sesión, cuyas
                // políticas RLS son distintas.
                order("nombre", Order.ASCENDING)
            }
            .decodeList<ProductDto>()

        // Promociones vigentes hoy (el RLS ya limita a activas para anon).
        val now = nowIso()
        val promotions = supabase.from(SupabaseConfig.PROMOTIONS_TABLE)
            .select(
                columns = Columns.raw(
                    "id, tipo, valor, " +
                        "promotion_targets(category_id, product_id, variant_id)"
                )
            ) {
                filter {
                    eq("activo", true)
                    lte("fecha_inicio", now)
                    gte("fecha_fin", now)
                }
            }
            .decodeList<PromotionDto>()

        // Categorías: jerarquía para expandir promos y nombres para el filtro de la UI.
        val categoryRefs = supabase.from(SupabaseConfig.CATEGORIES_TABLE)
            .select(columns = Columns.raw("id, nombre, parent_id"))
            .decodeList<CategoryRefDto>()

        // Diccionario de los `attributes`: convierte {"material":"silicona"} en la chip
        // "🧴 Silicona". El RLS (script 20) deja leer los activos sin sesión.
        val attributePresets = supabase.from(SupabaseConfig.ATTRIBUTE_PRESETS_TABLE)
            .select(columns = Columns.raw("clave, valor, identificador, emoji, orden")) {
                filter { eq("activo", true) }
                order("orden", Order.ASCENDING)
            }
            .decodeList<AttributePresetDto>()

        NetworkResult.Success(
            CatalogBundle(products, promotions, categoryRefs, attributePresets)
        )
    }
}
