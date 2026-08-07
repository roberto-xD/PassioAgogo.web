package com.smartbe.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.browser.document
import network.CatalogRepository
import network.ContactRepository
import ui.CatalogScreen
import ui.components.Footer
import ui.components.LocalHtmlOverlayClip
import ui.components.NavBar
import ui.navigation.Screen
import ui.navigation.rememberScreenState
import ui.screens.AboutScreen
import ui.screens.ContactScreen
import ui.screens.HelpScreen
import ui.screens.HomeScreen
import ui.screens.PodcastScreen
import ui.screens.PrivacyScreen
import ui.screens.TermsScreen
import ui.screens.VideoScreen
import viewmodel.CatalogViewModel
import viewmodel.ContactViewModel

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

    // Pantalla actual sincronizada con el hash de la URL (#/inicio, #/catalogo, ...).
    val screenState = rememberScreenState()
    val current = screenState.value
    val navigate: (Screen) -> Unit = { screenState.value = it }

    // Composición manual de dependencias (sin framework de DI) para mantener el
    // módulo simple y estable en el target web.
    val catalogViewModel = remember { CatalogViewModel(CatalogRepository()) }
    LaunchedEffect(Unit) { catalogViewModel.loadCatalog() }
    val catalogState by catalogViewModel.uiState.collectAsState()

    val contactViewModel = remember { ContactViewModel(ContactRepository()) }
    val contactState by contactViewModel.uiState.collectAsState()

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1035), Color(0xFF3A1C71))
                    )
                ),
        ) {
            NavBar(current = current, onNavigate = navigate)

            // Área de contenido: sirve de recorte para los elementos HTML superpuestos
            // (video), para que no se dibujen sobre la barra o el pie al hacer scroll.
            var contentBounds by remember { mutableStateOf<Rect?>(null) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { contentBounds = it.boundsInWindow() }
            ) {
                CompositionLocalProvider(LocalHtmlOverlayClip provides contentBounds) {
                    when (current) {
                        Screen.Home -> HomeScreen(
                            onExploreCatalog = { navigate(Screen.Catalog) },
                        )
                        Screen.Catalog -> CatalogScreen(
                            state = catalogState,
                            onSelectCategory = catalogViewModel::selectCategory,
                            onSearchChange = catalogViewModel::setSearchQuery,
                        )
                        Screen.About -> AboutScreen()
                        Screen.Video -> VideoScreen()
                        Screen.Podcast -> PodcastScreen()
                        Screen.Contact -> ContactScreen(
                            state = contactState,
                            onNombreChange = contactViewModel::updateNombre,
                            onEmailChange = contactViewModel::updateEmail,
                            onMensajeChange = contactViewModel::updateMensaje,
                            onSubmit = contactViewModel::submit,
                            onReset = contactViewModel::reset,
                        )
                        Screen.Terms -> TermsScreen()
                        Screen.Privacy -> PrivacyScreen()
                        Screen.Help -> HelpScreen()
                    }
                }
            }

            Footer(onNavigate = navigate)
        }
    }
}
