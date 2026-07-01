package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import models.PGCatalogItem
import models.toPGDataCard
import network.ApiStatus
import network.CatalogRepository
import ui.pgmodels.PGDataCard

data class CatalogUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val products: List<PGDataCard> = emptyList(),
)

class CatalogViewModel(
    private val repository: CatalogRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    fun loadCatalog(store: String? = null) {
        scope.launch {
            repository.getCatalog(store).collect { result ->
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
                            products = result.data?.items
                                ?.map(PGCatalogItem::toPGDataCard)
                                .orEmpty(),
                        )
                    }
                }
            }
        }
    }
}
