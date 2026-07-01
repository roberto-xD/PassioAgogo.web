package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import models.PGCatalog
import network.ApiStatus
import network.NetworkRepository

data class HomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val catalog: PGCatalog = PGCatalog(),
)

class HomeViewModel(
    private val networkRepository: NetworkRepository
) {
    // Un único scope propio del ViewModel en lugar de crear uno nuevo (y con el
    // dispatcher equivocado) en cada llamada, lo que provocaba fugas de corrutinas.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun getProducts(store: String? = null) {
        scope.launch {
            networkRepository.getProductList(store).collect { response ->
                when (response.status) {
                    ApiStatus.LOADING -> _uiState.update {
                        it.copy(isLoading = true, errorMessage = null)
                    }
                    ApiStatus.ERROR -> _uiState.update {
                        it.copy(isLoading = false, errorMessage = response.message)
                    }
                    ApiStatus.SUCCESS -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            catalog = response.data ?: PGCatalog(),
                        )
                    }
                }
            }
        }
    }
}
