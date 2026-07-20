package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
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
                    "id, nombre, descripcion, marca, imagenes, category_id, " +
                        "categories(nombre), " +
                        "product_variants(id, sku, precio_venta, activo)"
                )
            ) {
                filter { eq("activo", true) }
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

        NetworkResult.Success(CatalogBundle(products, promotions, categoryRefs))
    }
}
