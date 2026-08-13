package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import models.GuideDto
import models.GuideItem
import models.toItem

/**
 * Guías de uso y cuidados.
 *
 * El RLS (script 22) permite a `anon` leer las guías activas, así que no hace falta
 * sesión para consultarlas.
 */
class GuidesRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
) {
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    fun getGuides(): Flow<NetworkResult<List<GuideItem>>> = toResultFlow {
        if (!SupabaseConfig.isConfigured) {
            return@toResultFlow NetworkResult.Success(emptyList())
        }

        val guides = supabase.from(SupabaseConfig.GUIDES_TABLE)
            .select(
                columns = Columns.raw(
                    "id, titulo, resumen, emoji, uso, limpieza, cuidados, advertencias"
                )
            ) {
                filter { eq("activo", true) }
                order("orden", Order.ASCENDING)
                order("titulo", Order.ASCENDING)
            }
            .decodeList<GuideDto>()
            .mapNotNull(GuideDto::toItem)

        NetworkResult.Success(guides)
    }
}
