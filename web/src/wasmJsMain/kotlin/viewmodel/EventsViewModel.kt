package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import models.EventItem
import network.ApiStatus
import network.BrowserStorage
import network.EventsRepository

/** Clave donde se recuerda si la persona dejó el anuncio minimizado. */
private const val MINIMIZED_KEY = "passion.eventos.minimizado"

data class EventsUiState(
    val isLoading: Boolean = true,
    val events: List<EventItem> = emptyList(),
    /** Interruptor `widget_eventos_visible` de la base de datos. */
    val widgetEnabled: Boolean = false,
    /**
     * Si la persona dejó el anuncio minimizado.
     *
     * `null` significa que aún no ha decidido nada, y entonces manda el criterio por
     * defecto de cada tamaño de pantalla. Distinguir «sin decidir» de «desplegado» es lo
     * que permite que en el móvil arranque plegado sin pisar una decisión explícita.
     */
    val minimized: Boolean? = null,
    val errorMessage: String? = null,
) {
    /**
     * Los que aún no han ocurrido.
     *
     * El widget solo anuncia estos: la pantalla lista además los pasados, pero un
     * anuncio flotante de algo que ya se celebró no invita a nada.
     */
    val proximos: List<EventItem> get() = events.filterNot { it.esPasado }

    /**
     * Sin eventos próximos el widget no aparece, aunque el interruptor esté encendido:
     * una tarjeta flotante vacía solo estorbaría.
     */
    val showWidget: Boolean
        get() = widgetEnabled && proximos.isNotEmpty()

    /**
     * Busca por el fragmento de la URL. Acepta también el identificador para que los
     * enlaces con el formato anterior sigan abriendo la ficha.
     */
    fun find(slugOrId: String?): EventItem? {
        if (slugOrId.isNullOrBlank()) return null
        return events.firstOrNull { it.slug == slugOrId } ?: events.firstOrNull { it.id == slugOrId }
    }
}

class EventsViewModel(
    private val repository: EventsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    fun load() {
        scope.launch {
            repository.getUpcoming().collect { result ->
                when (result.status) {
                    ApiStatus.LOADING -> _uiState.update {
                        it.copy(isLoading = true, errorMessage = null)
                    }
                    // Un fallo de red no debe pintar un widget roto: se calla y ya.
                    ApiStatus.ERROR -> _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                    ApiStatus.SUCCESS -> {
                        val bundle = result.data
                        val events = bundle?.events.orEmpty()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = null,
                                events = events,
                                widgetEnabled = bundle?.widgetVisible == true,
                                minimized = leerDecision(events.filterNot { e -> e.esPasado }),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Minimiza o despliega el anuncio, y lo recuerda.
     *
     * Se guarda junto al sello de la cartelera actual: cuando publiques un evento nuevo
     * la decisión caduca y el anuncio vuelve a mostrarse desplegado. Minimizarlo una vez
     * no lo silencia para siempre.
     */
    fun setMinimized(value: Boolean) {
        _uiState.update { estado ->
            BrowserStorage.write(
                MINIMIZED_KEY,
                sello(estado.proximos) + SEPARADOR + if (value) MIN else EXP,
            )
            estado.copy(minimized = value)
        }
    }
}

private const val SEPARADOR = "|"
private const val MIN = "min"
private const val EXP = "exp"

/** Identifica la cartelera: cambia en cuanto entra o sale un evento. */
private fun sello(events: List<EventItem>): String =
    events.map { it.id }.sorted().joinToString(",")

/**
 * Decisión guardada para *esta* cartelera. Si el sello no coincide —hay eventos nuevos—
 * se ignora lo guardado y se vuelve al comportamiento por defecto.
 */
private fun leerDecision(events: List<EventItem>): Boolean? {
    val guardado = BrowserStorage.read(MINIMIZED_KEY) ?: return null
    val corte = guardado.lastIndexOf(SEPARADOR)
    if (corte < 0) return null
    if (guardado.substring(0, corte) != sello(events)) return null
    return when (guardado.substring(corte + 1)) {
        MIN -> true
        EXP -> false
        else -> null
    }
}
