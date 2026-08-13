package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import models.GuideItem
import network.ApiStatus
import network.GuidesRepository

data class GuidesUiState(
    val isLoading: Boolean = true,
    val guides: List<GuideItem> = emptyList(),
    val errorMessage: String? = null,
)

class GuidesViewModel(
    private val repository: GuidesRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(GuidesUiState())
    val uiState: StateFlow<GuidesUiState> = _uiState.asStateFlow()

    fun load() {
        scope.launch {
            repository.getGuides().collect { result ->
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
                            guides = result.data.orEmpty(),
                        )
                    }
                }
            }
        }
    }
}
