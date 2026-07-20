package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import models.ProductDto

class CatalogRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
) {
    // Se construye solo cuando hay configuración real y se hace una consulta. Con los
    // placeholders el cliente de Supabase nunca se instancia.
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    /**
     * Productos activos con su categoría y variantes (embedding de PostgREST).
     *
     * Se piden columnas explícitas — nunca `*` — para no exponer campos sensibles
     * como `product_variants.costo` en el tráfico del cliente. El RLS (10_rls.sql)
     * ya limita a `anon` a registros activos.
     */
    fun getCatalog(): Flow<NetworkResult<List<ProductDto>>> = toResultFlow {
        if (!SupabaseConfig.isConfigured) {
            return@toResultFlow NetworkResult.Success(emptyList())
        }

        val products = supabase.from(SupabaseConfig.PRODUCTS_TABLE)
            .select(
                columns = Columns.raw(
                    "id, nombre, descripcion, marca, imagenes, " +
                        "categories(nombre), " +
                        "product_variants(id, sku, precio_venta, activo)"
                )
            ) {
                filter { eq("activo", true) }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<ProductDto>()

        NetworkResult.Success(products)
    }
}
