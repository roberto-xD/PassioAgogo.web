package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ui.theme.PassionTheme
import viewmodel.CatalogUiState
import viewmodel.CategoryOption

@Composable
fun CatalogScreen(
    state: CatalogUiState,
    onSelectCategory: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!state.isLoading && state.errorMessage == null) {
            SearchField(query = state.searchQuery, onQueryChange = onSearchChange)
        }
        if (state.categories.isNotEmpty()) {
            CategoryChips(
                categories = state.categories,
                selectedId = state.selectedCategoryId,
                onSelect = onSelectCategory,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.errorMessage != null -> Message(
                    title = "No se pudo cargar el catálogo",
                    detail = state.errorMessage,
                )
                state.products.isEmpty() && state.searchQuery.isNotBlank() -> Message(
                    title = "Sin resultados para \"${state.searchQuery}\"",
                    detail = "Prueba con otras palabras o quita el filtro.",
                )
                state.products.isEmpty() && state.selectedCategoryId != null -> Message(
                    title = "Sin productos en esta categoría",
                    detail = "Prueba con otra categoría.",
                )
                state.products.isEmpty() -> Message(
                    title = "Catálogo próximamente",
                    detail = "Aún no hay productos para mostrar.",
                )
                else -> ProductGrid(state)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Buscar productos…") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Text(
                    text = "✕",
                    color = PassionTheme.semantics.onBackgroundMuted,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable { onQueryChange("") }
                        .padding(
                            horizontal = PassionTheme.spacing.s2,
                            vertical = PassionTheme.spacing.s1,
                        ),
                )
            }
        },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = PassionTheme.spacing.s4,
                vertical = PassionTheme.spacing.s2,
            ),
    )
}

@Composable
private fun CategoryChips(
    categories: List<CategoryOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = PassionTheme.spacing.s4,
                vertical = PassionTheme.spacing.s2,
            ),
        horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(label = "Todas", selected = selectedId == null) { onSelect(null) }
        categories.forEach { category ->
            Chip(label = category.nombre, selected = selectedId == category.id) {
                onSelect(category.id)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary
        else PassionTheme.semantics.onBackgroundMuted,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else PassionTheme.semantics.overlayWeak
            )
            .clickable(onClick = onClick)
            .padding(horizontal = PassionTheme.spacing.s4, vertical = PassionTheme.spacing.s2),
    )
}

@Composable
private fun ProductGrid(state: CatalogUiState) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PassionTheme.spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s4),
        verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s4),
    ) {
        items(state.products) { product ->
            ProductCard(product)
        }
    }
}

@Composable
private fun Message(title: String, detail: String) {
    Column(
        modifier = Modifier.padding(PassionTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail,
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        )
    }
}
