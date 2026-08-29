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
    /**
     * Todos los eventos activos, próximos primero y pasados después.
     *
     * La pantalla los muestra todos en la misma columna; el widget flotante se queda
     * solo con los próximos, porque anunciar algo que ya ocurrió no tiene sentido.
     */
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

        // Se piden todos los activos, no solo los vigentes: la pantalla muestra también
        // los pasados. Quién es próximo y quién no se decide abajo, con el reloj del
        // navegador, que es el mismo criterio con el que se ordenan.
        val events = supabase.from(SupabaseConfig.EVENTS_TABLE)
            .select(
                columns = Columns.raw(
                    "id, titulo, resumen, detalles, lugar, imagenes, " +
                        "fecha_inicio, fecha_fin, enlace, enlace_texto"
                )
            ) {
                filter { eq("activo", true) }
                order("fecha_inicio", Order.ASCENDING)
                order("orden", Order.ASCENDING)
            }
            .decodeList<EventDto>()
            .mapNotNull(::toItem)
            // Primero los próximos, del más cercano al más lejano; después los pasados,
            // del más reciente al más antiguo. Lo que interesa queda arriba en ambos
            // tramos: el evento al que aún se llega a tiempo, y el que se acaba de vivir.
            .sortedWith(
                compareBy<EventItem> { it.esPasado }
                    .thenBy { if (it.esPasado) "" else it.ordenIso }
                    .thenByDescending { if (it.esPasado) it.ordenIso else "" }
            )

        NetworkResult.Success(EventsBundle(widgetVisible = visible, events = events))
    }
}

/** Un evento sin título o sin fecha no es anunciable: se descarta en lugar de pintarse a medias. */
private fun toItem(dto: EventDto): EventItem? {
    val id = dto.id ?: return null
    val titulo = dto.titulo?.takeIf { it.isNotBlank() } ?: return null
    val inicio = dto.fechaInicio?.takeIf { it.isNotBlank() } ?: return null

    // "Ya pasó" se mide contra el final si lo hay: un evento de tres días sigue siendo
    // próximo mientras esté en curso.
    val vigenteHasta = dto.fechaFin?.takeIf { it.isNotBlank() } ?: inicio

    return EventItem(
        id = id,
        // Si el título no deja nada aprovechable —solo signos, por ejemplo— se cae al
        // identificador: es feo, pero una ficha sin dirección no se puede abrir.
        slug = slugify(titulo).ifBlank { id },
        titulo = titulo,
        resumen = dto.resumen.orEmpty(),
        detalles = dto.detalles.orEmpty(),
        lugar = dto.lugar.orEmpty(),
        imagenes = dto.imagenes
            .filter { it.isNotBlank() }
            .map(SupabaseConfig::publicImageUrl),
        fechaCorta = formatDateRange(inicio, dto.fechaFin),
        fechaLarga = formatLongDate(inicio),
        fechaSolo = formatDateOnly(inicio),
        horaSolo = formatTimeOnly(inicio),
        fechaFinSolo = dto.fechaFin
            ?.takeIf { it.isNotBlank() }
            ?.let(::formatDateOnly)
            // Un evento de un solo día trae fecha de fin igual a la de inicio: repetirla
            // debajo no informaría de nada.
            ?.takeIf { it != formatDateOnly(inicio) }
            .orEmpty(),
        enlace = dto.enlace.orEmpty(),
        enlaceTexto = dto.enlaceTexto.orEmpty().trim(),
        esPasado = isPast(vigenteHasta),
        ordenIso = inicio,
    )
}
