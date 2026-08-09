package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import models.GallerySlide
import models.toSlide
import network.ApiStatus
import network.GalleryRepository

data class GalleryUiState(
    val isLoading: Boolean = true,
    val slides: List<GallerySlide> = emptyList(),
    val errorMessage: String? = null,
)

class GalleryViewModel(
    private val repository: GalleryRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    fun load() {
        scope.launch {
            repository.getItems().collect { result ->
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
                            // Descarta los elementos sin imagen: no aportan nada al carrusel.
                            slides = result.data?.mapNotNull { item -> item.toSlide() }.orEmpty(),
                        )
                    }
                }
            }
        }
    }
}
