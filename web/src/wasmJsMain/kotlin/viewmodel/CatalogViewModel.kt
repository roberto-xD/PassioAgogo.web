package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import models.CatalogBundle
import models.buildCatalogCards
import models.childrenIndex
import models.expandCategoryIds
import network.ApiStatus
import network.CatalogRepository
import ui.pgmodels.PGDataCard

data class CategoryOption(val id: String, val nombre: String)

data class CatalogUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val products: List<PGDataCard> = emptyList(),
    val categories: List<CategoryOption> = emptyList(),
    val selectedCategoryId: String? = null,
)

class CatalogViewModel(
    private val repository: CatalogRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    // Última carga completa; el filtrado por categoría es en cliente sobre este bundle.
    private var bundle: CatalogBundle = CatalogBundle()

    fun loadCatalog() {
        scope.launch {
            repository.getCatalog().collect { result ->
                when (result.status) {
                    ApiStatus.LOADING -> _uiState.update {
                        it.copy(isLoading = true, errorMessage = null)
                    }
                    ApiStatus.ERROR -> _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                    ApiStatus.SUCCESS -> {
                        bundle = result.data ?: CatalogBundle()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = null,
                                products = cardsFor(categoryId = null),
                                categories = categoryOptions(),
                                selectedCategoryId = null,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Filtra por categoría (incluyendo subcategorías); null = todas. */
    fun selectCategory(categoryId: String?) {
        _uiState.update {
            it.copy(selectedCategoryId = categoryId, products = cardsFor(categoryId))
        }
    }

    private fun cardsFor(categoryId: String?): List<PGDataCard> {
        if (categoryId == null) return buildCatalogCards(bundle)
        val ids = expandCategoryIds(categoryId, childrenIndex(bundle.categoryRefs))
        val filtered = bundle.products.filter { product ->
            val cid = product.categoryId
            cid != null && cid in ids
        }
        return buildCatalogCards(bundle.copy(products = filtered))
    }

    /** Chips: categorías raíz cuyo subárbol tiene al menos un producto. */
    private fun categoryOptions(): List<CategoryOption> {
        val index = childrenIndex(bundle.categoryRefs)
        val productCategoryIds = bundle.products.mapNotNull { it.categoryId }.toSet()
        return bundle.categoryRefs
            .filter { it.parentId == null && it.id != null && !it.nombre.isNullOrBlank() }
            .filter { root -> expandCategoryIds(root.id!!, index).any { it in productCategoryIds } }
            .map { CategoryOption(it.id!!, it.nombre!!) }
            .sortedBy { it.nombre }
    }
}
