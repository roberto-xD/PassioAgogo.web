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

/** Clave donde se recuerda qué anuncio cerró la persona. */
private const val DISMISS_KEY = "passion.eventos.cerrado"

data class EventsUiState(
    val isLoading: Boolean = true,
    val events: List<EventItem> = emptyList(),
    /** Interruptor `widget_eventos_visible` de la base de datos. */
    val widgetEnabled: Boolean = false,
    /** La persona cerró este anuncio con la ✕. */
    val dismissed: Boolean = false,
    val errorMessage: String? = null,
) {
    /**
     * Sin eventos que anunciar el widget no aparece, aunque el interruptor esté
     * encendido: una tarjeta flotante vacía solo estorbaría.
     */
    val showWidget: Boolean
        get() = widgetEnabled && !dismissed && events.isNotEmpty()
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
                                dismissed = BrowserStorage.read(DISMISS_KEY) == sello(events),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Cierra el anuncio y lo recuerda.
     *
     * Se guarda el sello de la cartelera actual, no un simple "cerrado": así, cuando
     * publiques un evento nuevo, el widget vuelve a aparecer. Cerrarlo una vez no lo
     * silencia para siempre.
     */
    fun dismiss() {
        _uiState.update { estado ->
            BrowserStorage.write(DISMISS_KEY, sello(estado.events))
            estado.copy(dismissed = true)
        }
    }
}

/** Identifica la cartelera: cambia en cuanto entra o sale un evento. */
private fun sello(events: List<EventItem>): String =
    events.map { it.id }.sorted().joinToString(",")
