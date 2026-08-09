package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import models.GalleryItemDto

/**
 * Galería de la portada (eventos, promociones y productos destacados).
 *
 * El RLS (script 13) permite al rol `anon` leer los elementos activos, así que no hace
 * falta sesión para verlos.
 */
class GalleryRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
) {
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    fun getItems(): Flow<NetworkResult<List<GalleryItemDto>>> = toResultFlow {
        if (!SupabaseConfig.isConfigured) {
            return@toResultFlow NetworkResult.Success(emptyList())
        }

        val items = supabase.from(SupabaseConfig.GALLERY_TABLE)
            .select(
                columns = Columns.raw("id, titulo, descripcion, detalles, imagen, categoria, orden")
            ) {
                filter { eq("activo", true) }
                order("orden", Order.ASCENDING)
            }
            .decodeList<GalleryItemDto>()

        NetworkResult.Success(items)
    }
}
