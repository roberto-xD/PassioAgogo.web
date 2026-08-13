package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import models.EventDto
import models.EventItem
import models.SiteSettingDto

/** Lo que necesita la web para decidir si anuncia eventos y cuáles. */
data class EventsBundle(
    /** Interruptor `widget_eventos_visible` de `site_settings`. */
    val widgetVisible: Boolean = false,
    val events: List<EventItem> = emptyList(),
)

/**
 * Próximos eventos y el interruptor que decide si se anuncian.
 *
 * El RLS (script 21) deja leer sin sesión tanto los eventos activos como los ajustes del
 * sitio, así que el widget funciona para cualquier visitante.
 */
class EventsRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
) {
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    fun getUpcoming(): Flow<NetworkResult<EventsBundle>> = toResultFlow {
        if (!SupabaseConfig.isConfigured) {
            return@toResultFlow NetworkResult.Success(EventsBundle())
        }

        val visible = supabase.from(SupabaseConfig.SITE_SETTINGS_TABLE)
            .select(columns = Columns.raw("clave, valor")) {
                filter { eq("clave", SupabaseConfig.SETTING_EVENTS_WIDGET) }
            }
            .decodeList<SiteSettingDto>()
            .firstOrNull()
            ?.valor
            ?.trim()
            ?.lowercase()
            // Si la fila no existe, el widget no se muestra: es preferible que un ajuste
            // sin configurar deje el sitio como estaba a que aparezca algo inesperado.
            .let { it == "true" }

        // `vigente_hasta` es la columna generada del script 21: coalesce(fecha_fin,
        // fecha_inicio). Comparar contra ella deja fuera lo que ya pasó, tanto los
        // eventos de un día como los de varios que siguen en curso.
        val events = supabase.from(SupabaseConfig.EVENTS_TABLE)
            .select(
                columns = Columns.raw(
                    "id, titulo, resumen, detalles, lugar, imagen, " +
                        "fecha_inicio, fecha_fin, enlace"
                )
            ) {
                filter {
                    eq("activo", true)
                    gte("vigente_hasta", nowIso())
                }
                order("fecha_inicio", Order.ASCENDING)
                order("orden", Order.ASCENDING)
            }
            .decodeList<EventDto>()
            .mapNotNull(::toItem)

        NetworkResult.Success(EventsBundle(widgetVisible = visible, events = events))
    }
}

/** Un evento sin título o sin fecha no es anunciable: se descarta en lugar de pintarse a medias. */
private fun toItem(dto: EventDto): EventItem? {
    val id = dto.id ?: return null
    val titulo = dto.titulo?.takeIf { it.isNotBlank() } ?: return null
    val inicio = dto.fechaInicio?.takeIf { it.isNotBlank() } ?: return null

    return EventItem(
        id = id,
        titulo = titulo,
        resumen = dto.resumen.orEmpty(),
        detalles = dto.detalles.orEmpty(),
        lugar = dto.lugar.orEmpty(),
        imagen = dto.imagen?.takeIf { it.isNotBlank() }
            ?.let(SupabaseConfig::publicImageUrl)
            .orEmpty(),
        fechaCorta = formatDateRange(inicio, dto.fechaFin),
        fechaLarga = formatLongDate(inicio),
        enlace = dto.enlace.orEmpty(),
    )
}
