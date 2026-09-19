package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import models.SpinOutcome
import models.WheelPrize
import models.WheelTerm
import network.ApiStatus
import network.SpinResult
import network.WheelRepository

data class WheelUiState(
    val isLoading: Boolean = true,
    val visible: Boolean = false,
    val prizes: List<WheelPrize> = emptyList(),
    /** Bases de la promoción, en el orden en que se leen. Vacío si no hay ninguna. */
    val terms: List<WheelTerm> = emptyList(),
    /** Hay una petición de giro en el aire o la ruleta sigue girando. */
    val spinning: Boolean = false,
    /**
     * Lo que decidió el servidor.
     *
     * Llega **antes** de que la ruleta termine de girar: la animación solo se encarga de
     * frenar donde toca. Por eso el resultado no se enseña hasta [revealed].
     */
    val outcome: SpinOutcome? = null,
    /** La ruleta ya frenó y se puede enseñar el premio. */
    val revealed: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Dónde tiene que frenar la ruleta, o `null` mientras no se sabe. */
    val landingIndex: Int?
        get() = outcome?.let { premio ->
            prizes.indexOfFirst { it.id == premio.prizeId }.takeIf { it >= 0 }
        }

    val canSpin: Boolean get() = !spinning && !revealed && prizes.size > 1
}

class WheelViewModel(
    private val repository: WheelRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(WheelUiState())
    val uiState: StateFlow<WheelUiState> = _uiState.asStateFlow()

    fun load() {
        scope.launch {
            repository.getWheel().collect { result ->
                when (result.status) {
                    ApiStatus.LOADING -> _uiState.update {
                        it.copy(isLoading = true, errorMessage = null)
                    }
                    ApiStatus.ERROR -> _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                    ApiStatus.SUCCESS -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            visible = result.data?.visible == true,
                            prizes = result.data?.prizes.orEmpty(),
                            terms = result.data?.terms.orEmpty(),
                        )
                    }
                }
            }
        }
    }

    /**
     * Pide un giro.
     *
     * La ruleta arranca de inmediato aunque la respuesta tarde: girar en el vacío durante
     * un instante se ve natural, y esperar con la ruleta quieta a que conteste el
     * servidor se vería como que el botón no funciona.
     */
    fun spin() {
        if (!_uiState.value.canSpin) return
        _uiState.update { it.copy(spinning = true, errorMessage = null) }

        scope.launch {
            when (val result = repository.spin()) {
                is SpinResult.Ganado -> _uiState.update { it.copy(outcome = result.outcome) }
                is SpinResult.Fallo -> _uiState.update {
                    // Se detiene la ruleta: seguir girando sin destino sería mentir.
                    it.copy(spinning = false, errorMessage = result.mensaje)
                }
            }
        }
    }

    /** La avisa la ruleta cuando termina de frenar. */
    fun onLanded() {
        _uiState.update { it.copy(spinning = false, revealed = true) }
    }
}
