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

/**
 * Categoría raíz con pantalla propia (26_category_sections).
 *
 * @param slug lo que viaja en la URL: `#/seccion/<slug>`.
 */
data class CatalogSection(val id: String, val slug: String, val nombre: String)

data class CatalogUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val products: List<PGDataCard> = emptyList(),
    /** Categorías raíz con productos. */
    val categories: List<CategoryOption> = emptyList(),
    /** Hijas directas de [selectedCategoryId] que tienen productos. */
    val subcategories: List<CategoryOption> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedSubcategoryId: String? = null,
    val searchQuery: String = "",
    /** Secciones con pantalla propia, para la barra de navegación. */
    val sections: List<CatalogSection> = emptyList(),
    /** Sección que se está mostrando, o `null` si es el catálogo principal. */
    val currentSection: CatalogSection? = null,
    /**
     * Se pidió una sección que no existe —un enlace compartido antes de renombrar el
     * slug—. Sin esto la pantalla mostraría el catálogo entero como si nada.
     */
    val sectionMissing: Boolean = false,
)

class CatalogViewModel(
    private val repository: CatalogRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    // Última carga completa; filtro y búsqueda son en cliente sobre este bundle.
    private var bundle: CatalogBundle = CatalogBundle()

    /**
     * Sección que se está mostrando en exclusiva, o `null` para el catálogo principal.
     * Lo fija la ruta, no un filtro del usuario: por eso no vive en los chips.
     *
     * Se guarda el slug y no la categoría ya resuelta porque la ruta puede llegar antes
     * que el catálogo —quien abre `#/seccion/lenceria` directamente— y entonces no hay
     * todavía ninguna categoría con la que emparejarlo.
     */
    private var sectionSlug: String? = null

    /** La sección pedida, si el catálogo ya cargó y existe una con ese slug. */
    private fun currentSection(): CatalogSection? =
        sectionSlug?.let { buscado -> sectionOptions().firstOrNull { it.slug == buscado } }

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
                                products = visibleCards(null, null, ""),
                                categories = rootCategoryOptions(),
                                subcategories = emptyList(),
                                selectedCategoryId = null,
                                selectedSubcategoryId = null,
                                searchQuery = "",
                                sections = sectionOptions(),
                                currentSection = currentSection(),
                                sectionMissing = sectionSlug != null && currentSection() == null,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Cambia entre el catálogo principal (`null`) y la pantalla de una sección.
     *
     * Se llama con el slug de la URL en cada composición, así que solo hace algo cuando
     * el destino cambia de verdad; de otro modo cada recomposición borraría el filtro
     * que la persona acaba de elegir.
     *
     * Al cambiar de ámbito sí se limpian filtro y búsqueda: pertenecían al anterior y
     * arrastrar una subcategoría de Lencería al catálogo general no dejaría nada a la
     * vista.
     */
    fun setSection(slug: String?) {
        if (slug == sectionSlug) return

        sectionSlug = slug
        _uiState.update {
            it.copy(
                currentSection = currentSection(),
                // Con el catálogo aún cargando no se puede saber si el slug existe; se
                // decide cuando llegan las categorías, no aquí.
                sectionMissing = !it.isLoading && slug != null && currentSection() == null,
                categories = rootCategoryOptions(),
                subcategories = emptyList(),
                selectedCategoryId = null,
                selectedSubcategoryId = null,
                searchQuery = "",
                products = visibleCards(null, null, ""),
            )
        }
    }

    /**
     * Filtra por categoría raíz (incluyendo toda su descendencia); `null` = todas.
     *
     * Cambiar de raíz descarta la subcategoría elegida: pertenecía a la rama anterior.
     */
    fun selectCategory(categoryId: String?) {
        _uiState.update {
            it.copy(
                selectedCategoryId = categoryId,
                selectedSubcategoryId = null,
                subcategories = subcategoryOptions(categoryId),
                products = visibleCards(categoryId, null, it.searchQuery),
            )
        }
    }

    /** Segundo nivel: acota a una hija de la raíz elegida; `null` = toda la raíz. */
    fun selectSubcategory(subcategoryId: String?) {
        _uiState.update {
            it.copy(
                selectedSubcategoryId = subcategoryId,
                products = visibleCards(it.selectedCategoryId, subcategoryId, it.searchQuery),
            )
        }
    }

    /** Búsqueda por texto sobre nombre, descripción, marca, categoría y SKU. */
    fun setSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                products = visibleCards(it.selectedCategoryId, it.selectedSubcategoryId, query),
            )
        }
    }

    private fun visibleCards(
        categoryId: String?,
        subcategoryId: String?,
        query: String,
    ): List<PGDataCard> {
        // Visible en el catálogo público: disponible, o conseguible por encargo. La
        // consulta no lo filtra para que el resultado no dependa de si hay sesión
        // iniciada, ya que el RLS es más permisivo con el staff.
        var products = bundle.products.filter { it.activo || it.sobrePedido }

        // Ámbito: dentro de una sección solo existe su subárbol; en el catálogo
        // principal existe todo **menos** los subárboles de las secciones. Se aplica
        // antes que la búsqueda, así que buscar en el catálogo tampoco saca lencería.
        val seccion = currentSection()
        products = if (seccion != null) {
            val dentro = expandCategoryIds(seccion.id, childrenIndex(bundle.categoryRefs))
            products.filter { it.categoryId != null && it.categoryId in dentro }
        } else {
            val apartados = sectionCategoryIds()
            products.filter { it.categoryId == null || it.categoryId !in apartados }
        }

        // La subcategoría, si la hay, manda: siempre es descendiente de la raíz elegida.
        val effectiveCategoryId = subcategoryId ?: categoryId
        if (effectiveCategoryId != null) {
            val ids = expandCategoryIds(effectiveCategoryId, childrenIndex(bundle.categoryRefs))
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

    /** Las categorías raíz con pantalla propia, en el orden en que se leen. */
    private fun sectionOptions(): List<CatalogSection> = bundle.categoryRefs
        .filter { it.parentId == null }
        .mapNotNull { ref ->
            val id = ref.id ?: return@mapNotNull null
            val slug = ref.seccion?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val nombre = ref.nombre?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CatalogSection(id = id, slug = slug.trim(), nombre = nombre.trim())
        }
        .sortedBy { it.nombre }

    /** Todo lo que cuelga de una sección, que es justo lo que el catálogo no muestra. */
    private fun sectionCategoryIds(): Set<String> {
        val index = childrenIndex(bundle.categoryRefs)
        return sectionOptions().flatMap { expandCategoryIds(it.id, index) }.toSet()
    }

    /**
     * Chips del primer nivel.
     *
     * En el catálogo principal son las categorías raíz con productos, sin las que
     * tienen pantalla propia. Dentro de una sección son las hijas directas de esa
     * sección: ella misma ya es el ámbito y repetirla como chip no acotaría nada.
     */
    private fun rootCategoryOptions(): List<CategoryOption> {
        val seccion = currentSection()
        if (seccion != null) return subcategoryOptions(seccion.id)

        val index = childrenIndex(bundle.categoryRefs)
        val apartados = sectionCategoryIds()
        val productCategoryIds = bundle.products
            .filter { it.activo || it.sobrePedido }
            .mapNotNull { it.categoryId }
            .toSet()
        return bundle.categoryRefs
            .filter { it.parentId == null && it.id != null && !it.nombre.isNullOrBlank() }
            .filter { it.id !in apartados }
            .filter { root -> expandCategoryIds(root.id!!, index).any { it in productCategoryIds } }
            .map { CategoryOption(it.id!!, it.nombre!!) }
            .sortedBy { it.nombre }
    }

    /**
     * Chips del segundo nivel: hijas **directas** de [parentId] cuyo subárbol tiene
     * productos. Se limita a un nivel para que la fila siga siendo legible; los nietos
     * quedan incluidos en el filtro de su madre.
     */
    private fun subcategoryOptions(parentId: String?): List<CategoryOption> {
        if (parentId == null) return emptyList()
        val index = childrenIndex(bundle.categoryRefs)
        val productCategoryIds = bundle.products
            .filter { it.activo || it.sobrePedido }
            .mapNotNull { it.categoryId }
            .toSet()
        return bundle.categoryRefs
            .filter { it.parentId == parentId && it.id != null && !it.nombre.isNullOrBlank() }
            .filter { child -> expandCategoryIds(child.id!!, index).any { it in productCategoryIds } }
            .map { CategoryOption(it.id!!, it.nombre!!) }
            .sortedBy { it.nombre }
    }
}

private fun ProductDto.searchableText(): String = listOfNotNull(
    nombre,
    resumen,
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
