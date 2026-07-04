package com.smartbe.web

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.browser.document
import network.CatalogRepository
import ui.CatalogScreen
import viewmodel.CatalogViewModel

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // ComposeViewport añade su canvas al <body> sin borrar el contenido existente.
    // Quitamos el placeholder de carga para que no ocupe el viewport (height:100vh)
    // y empuje el canvas de Compose fuera de la vista.
    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!) {
        App()
    }
}

@Composable
fun App() {
    // En wasmJs el fetcher de red de Coil no se registra automáticamente: se
    // configura un ImageLoader que carga imágenes remotas vía Ktor.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }

    // Composición manual de dependencias (sin framework de DI) para mantener el
    // módulo simple y estable en el target web.
    val viewModel = remember {
        CatalogViewModel(CatalogRepository())
    }

    LaunchedEffect(Unit) {
        viewModel.loadCatalog()
    }

    val state by viewModel.uiState.collectAsState()

    MaterialTheme {
        CatalogScreen(state)
    }
}
