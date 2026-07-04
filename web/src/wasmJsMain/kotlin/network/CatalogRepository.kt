package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import models.PGCatalog
import models.PGCatalogItem

class CatalogRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
) {
    // Se construye solo cuando hay configuración real y se hace una consulta. Con los
    // placeholders el cliente de Supabase nunca se instancia.
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    fun getCatalog(store: String? = null): Flow<NetworkResult<PGCatalog>> = toResultFlow {
        if (!SupabaseConfig.isConfigured) {
            return@toResultFlow NetworkResult.Success(PGCatalog(items = emptyList()))
        }

        val items = supabase.from(SupabaseConfig.PRODUCTS_TABLE)
            .select {
                filter {
                    eq("is_active", true)
                    if (store != null) eq("store", store)
                }
            }
            .decodeList<PGCatalogItem>()

        NetworkResult.Success(PGCatalog(items = items))
    }
}
