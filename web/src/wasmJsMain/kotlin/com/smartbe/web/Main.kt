package com.smartbe.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.browser.document
import network.AuthRepository
import network.CatalogRepository
import network.ContactRepository
import network.EventsRepository
import network.GalleryRepository
import network.GuidesRepository
import ui.CatalogScreen
import ui.components.FloatingEventsWidget
import ui.components.Footer
import ui.components.LocalHtmlOverlayClip
import ui.components.NavBar
import ui.navigation.Route
import ui.navigation.Screen
import ui.navigation.rememberRouteState
import ui.theme.PassionAGogoTheme
import ui.theme.PassionTheme
import ui.screens.AboutScreen
import ui.screens.AccountScreen
import ui.screens.CareScreen
import ui.screens.ContactScreen
import ui.screens.EventDetailScreen
import ui.screens.EventsScreen
import ui.screens.HelpScreen
import ui.screens.HomeScreen
import ui.screens.LoginScreen
import ui.screens.PodcastScreen
import ui.screens.PrivacyScreen
import ui.screens.TermsScreen
import ui.screens.VideoScreen
import viewmodel.AuthViewModel
import viewmodel.CatalogViewModel
import viewmodel.ContactViewModel
import viewmodel.EventsViewModel
import viewmodel.GalleryViewModel
import viewmodel.GuidesViewModel

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

    // Destino actual sincronizado con el hash de la URL (#/inicio, #/eventos/<id>, ...).
    val routeState = rememberRouteState()
    val route = routeState.value
    val current = route.screen
    val navigate: (Screen) -> Unit = { routeState.value = Route(it) }
    val openEvent: (String) -> Unit = { routeState.value = Route(Screen.Events, it) }

    // Composición manual de dependencias (sin framework de DI) para mantener el
    // módulo simple y estable en el target web.
    val catalogViewModel = remember { CatalogViewModel(CatalogRepository()) }
    LaunchedEffect(Unit) { catalogViewModel.loadCatalog() }
    val catalogState by catalogViewModel.uiState.collectAsState()

    val contactViewModel = remember { ContactViewModel(ContactRepository()) }
    val contactState by contactViewModel.uiState.collectAsState()

    val eventsViewModel = remember { EventsViewModel(EventsRepository()) }
    LaunchedEffect(Unit) { eventsViewModel.load() }
    val eventsState by eventsViewModel.uiState.collectAsState()

    val galleryViewModel = remember { GalleryViewModel(GalleryRepository()) }
    LaunchedEffect(Unit) { galleryViewModel.load() }
    val galleryState by galleryViewModel.uiState.collectAsState()

    val guidesViewModel = remember { GuidesViewModel(GuidesRepository()) }
    LaunchedEffect(Unit) { guidesViewModel.load() }
    val guidesState by guidesViewModel.uiState.collectAsState()

    val authViewModel = remember { AuthViewModel(AuthRepository()) }
    val authState by authViewModel.uiState.collectAsState()

    // Al iniciar o cerrar sesión, mueve al usuario a la pantalla que corresponde.
    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated && routeState.value.screen == Screen.Login) {
            routeState.value = Route(Screen.Account)
        } else if (!authState.isAuthenticated && routeState.value.screen == Screen.Account) {
            routeState.value = Route(Screen.Login)
        }
    }

    PassionAGogoTheme {
        val semantics = PassionTheme.semantics
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(semantics.gradientTop, semantics.gradientBottom)
                    )
                ),
        ) {
            NavBar(
                current = current,
                isAuthenticated = authState.isAuthenticated,
                onNavigate = navigate,
            )

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
                            galleryState = galleryState,
                            onExploreCatalog = { navigate(Screen.Catalog) },
                        )
                        Screen.Catalog -> CatalogScreen(
                            state = catalogState,
                            onSelectCategory = catalogViewModel::selectCategory,
                            onSelectSubcategory = catalogViewModel::selectSubcategory,
                            onSearchChange = catalogViewModel::setSearchQuery,
                        )
                        Screen.About -> AboutScreen()
                        Screen.Video -> VideoScreen()
                        Screen.Podcast -> PodcastScreen()
                        // Con identificador en la URL se abre la ficha; sin él, el listado.
                        Screen.Events -> if (route.id != null) {
                            EventDetailScreen(
                                state = eventsState,
                                eventId = route.id,
                                onBack = { navigate(Screen.Events) },
                            )
                        } else {
                            EventsScreen(state = eventsState, onOpenEvent = openEvent)
                        }
                        Screen.Care -> CareScreen(state = guidesState)
                        Screen.Contact -> ContactScreen(
                            state = contactState,
                            onNombreChange = contactViewModel::updateNombre,
                            onEmailChange = contactViewModel::updateEmail,
                            onMensajeChange = contactViewModel::updateMensaje,
                            onSubmit = contactViewModel::submit,
                            onReset = contactViewModel::reset,
                        )
                        Screen.Login -> LoginScreen(
                            state = authState,
                            onEmailChange = authViewModel::updateEmail,
                            onPasswordChange = authViewModel::updatePassword,
                            onNombreChange = authViewModel::updateNombre,
                            onSwitchMode = authViewModel::switchMode,
                            onSubmit = authViewModel::submit,
                        )
                        Screen.Account -> AccountScreen(
                            state = authState,
                            onSignOut = authViewModel::signOut,
                            onGoToLogin = { navigate(Screen.Login) },
                        )
                        Screen.Terms -> TermsScreen()
                        Screen.Privacy -> PrivacyScreen()
                        Screen.Help -> HelpScreen()
                    }
                }

                // Fuera del `when`: el anuncio flota sobre cualquier pantalla y conserva
                // su estado al navegar. Dentro de este Box y no de la ventana entera para
                // no taparle la barra de navegacion ni el pie.
                // En la propia pantalla de Eventos el anuncio sobra.
                if (eventsState.showWidget && current != Screen.Events) {
                    FloatingEventsWidget(
                        events = eventsState.proximos,
                        minimized = eventsState.minimized,
                        onVerMas = { navigate(Screen.Events) },
                        onSetMinimized = eventsViewModel::setMinimized,
                    )
                }
            }

            Footer(onNavigate = navigate)
        }
    }
}
