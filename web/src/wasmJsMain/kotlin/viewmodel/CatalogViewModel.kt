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
import models.ProductDto
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
    val searchQuery: String = "",
)

class CatalogViewModel(
    private val repository: CatalogRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    // Última carga completa; filtro y búsqueda son en cliente sobre este bundle.
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
                                products = visibleCards(categoryId = null, query = ""),
                                categories = categoryOptions(),
                                selectedCategoryId = null,
                                searchQuery = "",
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
            it.copy(
                selectedCategoryId = categoryId,
                products = visibleCards(categoryId, it.searchQuery),
            )
        }
    }

    /** Búsqueda por texto sobre nombre, descripción, marca, categoría y SKU. */
    fun setSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                products = visibleCards(it.selectedCategoryId, query),
            )
        }
    }

    private fun visibleCards(categoryId: String?, query: String): List<PGDataCard> {
        var products = bundle.products

        if (categoryId != null) {
            val ids = expandCategoryIds(categoryId, childrenIndex(bundle.categoryRefs))
            products = products.filter { product ->
                val cid = product.categoryId
                cid != null && cid in ids
            }
        }

        val terms = normalize(query).split(' ').filter { it.isNotBlank() }
        if (terms.isNotEmpty()) {
            products = products.filter { product ->
                val haystack = normalize(product.searchableText())
                terms.all { it in haystack }
            }
        }

        return buildCatalogCards(bundle.copy(products = products))
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

private fun ProductDto.searchableText(): String = listOfNotNull(
    nombre,
    descripcion,
    marca,
    categoria?.nombre,
    variantes.mapNotNull { it.sku }.joinToString(" ").ifBlank { null },
).joinToString(" ")

/** Minúsculas y sin acentos, para búsqueda tolerante ("categoria" == "categoría"). */
private fun normalize(text: String): String = text.lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i')
    .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
    .replace('ñ', 'n')
