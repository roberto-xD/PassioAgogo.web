package com.smartbe.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import coil3.svg.SvgDecoder
import kotlinx.browser.document
import kotlinx.coroutines.delay
import network.AuthRepository
import network.CatalogRepository
import network.AddressRepository
import network.ContactRepository
import network.OrdersRepository
import network.EventsRepository
import network.GalleryRepository
import network.GuidesRepository
import network.WheelRepository
import ui.CatalogScreen
import ui.components.FloatingEventsWidget
import ui.components.Footer
import ui.components.clearQuery
import ui.components.currentQuery
import ui.components.goToExternal
import ui.components.queryParam
import ui.components.LocalHtmlOverlayClip
import ui.components.LocalSnackbarHostState
import ui.components.NavBar
import ui.navigation.Route
import ui.navigation.Screen
import ui.navigation.rememberRouteState
import ui.theme.PassionAGogoTheme
import ui.theme.PassionTheme
import ui.screens.AboutScreen
import ui.screens.AccountScreen
import ui.screens.SECCION_HISTORIAL
import ui.screens.CareScreen
import ui.screens.ContactScreen
import ui.screens.EventDetailScreen
import ui.screens.EventsScreen
import ui.screens.HelpScreen
import ui.screens.HomeScreen
import ui.screens.LoginScreen
import ui.screens.PodcastScreen
import ui.screens.CartScreen
import ui.screens.PrivacyScreen
import ui.screens.SectionProductScreen
import ui.screens.TermsScreen
import ui.screens.ThankYouScreen
import ui.screens.VideoScreen
import ui.screens.WheelScreen
import viewmodel.AuthViewModel
import viewmodel.AddressViewModel
import viewmodel.CartViewModel
import viewmodel.CatalogSection
import viewmodel.CatalogViewModel
import viewmodel.ContactViewModel
import viewmodel.EventsViewModel
import viewmodel.GalleryViewModel
import viewmodel.GuidesViewModel
import viewmodel.ThankYouViewModel
import viewmodel.WheelViewModel

/** Cada cuánto se comprueba que la sesión de la pantalla y la del cliente coincidan. */
private const val SESION_POLL_MS = 500L

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
            .components {
                add(KtorNetworkFetcherFactory())
                // Los logos de las paqueterías vienen en SVG, y Coil no los decodifica
                // sin esto: se cargarían sin error visible y no se pintaría nada.
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    // Destino actual sincronizado con el hash de la URL (#/inicio, #/eventos/<id>, ...).
    val routeState = rememberRouteState()
    val route = routeState.value
    val current = route.screen
    val navigate: (Screen) -> Unit = { routeState.value = Route(it) }
    val openEvent: (String) -> Unit = { routeState.value = Route(Screen.Events, it) }
    val openSection: (CatalogSection) -> Unit = {
        routeState.value = Route(Screen.Section, it.slug)
    }
    val openSectionProduct: (ui.pgmodels.PGDataCard) -> Unit = { producto ->
        routeState.value = Route(Screen.Section, routeState.value.id, producto.slug)
    }

    // Composición manual de dependencias (sin framework de DI) para mantener el
    // módulo simple y estable en el target web.
    val catalogViewModel = remember { CatalogViewModel(CatalogRepository()) }
    LaunchedEffect(Unit) { catalogViewModel.loadCatalog() }
    val catalogState by catalogViewModel.uiState.collectAsState()

    // El ámbito del catálogo lo manda la URL: `#/catalogo` es el general y
    // `#/seccion/<slug>` una sección. Cualquier otra pantalla no lo toca, para que al
    // ir y volver el filtro siga donde estaba.
    val sectionSlug = if (current == Screen.Section) route.id else null
    LaunchedEffect(current, sectionSlug) {
        if (current == Screen.Section || current == Screen.Catalog) {
            catalogViewModel.setSection(sectionSlug)
        }
    }

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

    val wheelViewModel = remember { WheelViewModel(WheelRepository()) }
    LaunchedEffect(Unit) { wheelViewModel.load() }
    val wheelState by wheelViewModel.uiState.collectAsState()

    val authViewModel = remember { AuthViewModel(AuthRepository()) }
    val authState by authViewModel.uiState.collectAsState()

    // La sesión se comprueba desde la propia composición, como la ruta. El flujo del
    // SDK debería bastar, pero en este target ya nos pasó que un cambio llegado desde
    // una llamada del navegador no despertara a Compose: la barra se quedaba diciendo
    // «Acceder» con la sesión ya abierta hasta recargar a mano. Comparar dos booleanos
    // dos veces por segundo no cuesta nada y cierra esa puerta.
    LaunchedEffect(Unit) {
        while (true) {
            delay(SESION_POLL_MS)
            authViewModel.syncSession()
        }
    }

    val cartViewModel = remember { CartViewModel(OrdersRepository()) }
    val cartState by cartViewModel.uiState.collectAsState()

    // El acuse de «gracias». Va aparte del carrito porque tiene su propio ciclo: carga
    // por id de la URL y sigue preguntando hasta que el webhook del pago conteste.
    val thankYouViewModel = remember { ThankYouViewModel() }
    val thankYouState by thankYouViewModel.uiState.collectAsState()

    val addressViewModel = remember { AddressViewModel(AddressRepository()) }
    val addressState by addressViewModel.uiState.collectAsState()

    // La dirección se carga cuando ya se sabe la sal, que llega con el perfil. Sin sal
    // no se puede derivar nada, así que antes no habría con qué abrir el sobre.
    LaunchedEffect(authState.isAuthenticated, authState.kdfSalt) {
        if (authState.isAuthenticated && authState.kdfSalt.isNotBlank()) {
            addressViewModel.cargar(authState.kdfSalt)
        } else if (!authState.isAuthenticated) {
            addressViewModel.limpiar()
        }
    }

    // El sobre se copia tal cual a la solicitud: enviar no necesita la clave.
    LaunchedEffect(addressState.sobre) {
        cartViewModel.setDireccion(addressState.sobre)
    }

    // El carrito exige dirección para enviar a domicilio, pero solo a quien tiene sesión:
    // sin ella no hay dónde guardarla.
    LaunchedEffect(authState.isAuthenticated) {
        cartViewModel.setAutenticado(authState.isAuthenticated)
    }

    // Enviada la solicitud, el teléfono que se escribió al pedir se queda en el perfil
    // para no volver a pedirlo. Se hace al enviar y no al teclear: hasta ese momento es
    // un campo a medias, no el contacto de nadie.
    LaunchedEffect(cartState.folioEnviado) {
        if (cartState.folioEnviado != null) {
            authViewModel.recordarTelefono(cartState.telefono)
        }
    }

    // El acuse carga con el id que trae la ruta, y deja de sondear al salir de la
    // pantalla: un sondeo que sigue en segundo plano pregunta por un pedido que ya nadie
    // está mirando.
    LaunchedEffect(routeState.value.screen, routeState.value.id) {
        val ruta = routeState.value
        if (ruta.screen == Screen.ThankYou) {
            thankYouViewModel.cargar(ruta.id.orEmpty())
        } else {
            thankYouViewModel.limpiar()
        }
    }

    // Hay URL de Mercado Pago: se lleva esta pestaña allí.
    //
    // La navegación se hace aquí y no en el ViewModel porque irse de la página es cosa
    // de la pantalla. Y en la misma pestaña: la URL llega después de preguntar a la Edge
    // Function, y una pestaña nueva a esas alturas la bloquea el navegador.
    LaunchedEffect(cartState.urlPago) {
        cartState.urlPago?.let { url ->
            cartViewModel.pagoAbierto()
            goToExternal(url)
        }
    }

    // Se vuelve de Mercado Pago: trae sus parámetros pegados a la URL.
    //
    // **No se le cree a la URL.** Es texto que cualquiera puede escribir a mano, y lo
    // que diga no cambia nada: lo único que se hace es ir al historial y volver a leer
    // el estado de la base, que lo escribe el webhook tras comprobar la firma. Si el
    // aviso aún no ha llegado se verá «en proceso», que es la verdad en ese instante.
    LaunchedEffect(Unit) {
        val consulta = currentQuery()
        val vieneDeUnPago = listOf("payment_id", "collection_id", "preference_id", "external_reference")
            .any { it in consulta }
        if (vieneDeUnPago) {
            // `external_reference` es el id de la solicitud, que es lo que pusimos al
            // crear la orden. **No se cree, se usa para buscar**: el RLS limita
            // `order_requests` a las propias, así que uno ajeno no devuelve nada.
            val solicitud = queryParam("external_reference")
            // Se limpian en cuanto se leen: dejarlos puestos haría que recargar
            // pareciera otro regreso, y que la URL compartida llevara dentro el id de
            // un pago de alguien.
            clearQuery()
            cartViewModel.volviDelPago()
            routeState.value = if (solicitud.isNotBlank()) {
                Route(Screen.ThankYou, id = solicitud)
            } else {
                // Sin referencia no hay acuse que enseñar, pero sí hay historial.
                Route(Screen.Account, id = SECCION_HISTORIAL)
            }
        }
    }

    // Avisos de «agregado». La clave es el contador y no el carrito: dos añadidos
    // seguidos del mismo artículo tienen que volver a avisar.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(cartState.agregados) {
        if (cartState.agregados > 0) {
            // Reemplaza el aviso anterior en lugar de encolarlo: quien añade tres cosas
            // seguidas quiere seguir comprando, no ver tres avisos uno tras otro.
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = "Agregado correctamente",
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
        }
    }

    // Al abrir sesion se rellenan los datos de contacto con lo que ya sabemos, sin pisar
    // lo que la persona haya escrito: puede querer el pedido a nombre de otra.
    LaunchedEffect(authState.isAuthenticated, authState.profile) {
        if (authState.isAuthenticated) {
            cartViewModel.prefill(
                nombre = authState.profile?.nombre.orEmpty(),
                email = authState.accountEmail,
                telefono = authState.profile?.telefono.orEmpty(),
            )
        }
    }

    // Quien llega al acceso desde el carrito vuelve al carrito, no a su cuenta: estaba
    // a medio enviar una solicitud y dejarlo en otra pantalla le hace repetir el camino.
    var volverAlCarrito by remember { mutableStateOf(false) }

    /**
     * La sección de «Mi cuenta» a la que se iba antes de que pidieran acceder.
     *
     * Quien vuelve de Mercado Pago con la sesión caducada rebota a la pantalla de
     * acceso, y sin esto entraría a su perfil: justo la persona con más prisa por saber
     * si su pago entró es la que acabaría buscando la pestaña a mano.
     */
    var seccionTrasAcceder by remember { mutableStateOf<String?>(null) }

    // Al iniciar o cerrar sesión, mueve al usuario a la pantalla que corresponde.
    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated && routeState.value.screen == Screen.Login) {
            routeState.value = if (volverAlCarrito) {
                Route(Screen.Cart)
            } else {
                Route(Screen.Account, id = seccionTrasAcceder)
            }
            volverAlCarrito = false
            seccionTrasAcceder = null
        } else if (!authState.isAuthenticated && routeState.value.screen == Screen.Account) {
            // Se apunta a dónde iba antes de mandarlo a acceder, para devolverlo ahí.
            seccionTrasAcceder = routeState.value.id
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
                sections = catalogState.sections,
                currentSectionSlug = sectionSlug,
                onNavigateSection = openSection,
                cartCount = cartState.unidades,
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
                CompositionLocalProvider(
                    LocalHtmlOverlayClip provides contentBounds,
                    LocalSnackbarHostState provides snackbarHostState,
                ) {
                    when (current) {
                        Screen.Home -> HomeScreen(
                            galleryState = galleryState,
                            onExploreCatalog = { navigate(Screen.Catalog) },
                        )
                        // Misma pantalla para el catálogo general y para una sección: lo
                        // único que cambia es el ámbito, que ya resolvió el ViewModel.
                        // Con un tercer segmento en la URL se abre la ficha del producto.
                        Screen.Catalog, Screen.Section -> if (
                            current == Screen.Section && route.subId != null
                        ) {
                            // Se resuelve aquí y no en la pantalla porque reconstruye las
                            // tarjetas de toda la sección: con `remember` eso ocurre al
                            // cambiar el catálogo o el slug, no en cada recomposición.
                            val producto = remember(catalogState.products, route.subId) {
                                catalogViewModel.findInSection(route.subId)
                            }
                            SectionProductScreen(
                                state = catalogState,
                                product = producto,
                                onBack = {
                                    routeState.value = Route(Screen.Section, route.id)
                                },
                                onAddToCart = { producto, variante -> cartViewModel.add(producto, variante) },
                            )
                        } else {
                            CatalogScreen(
                                state = catalogState,
                                onSelectCategory = catalogViewModel::selectCategory,
                                onSelectSubcategory = catalogViewModel::selectSubcategory,
                                onSearchChange = catalogViewModel::setSearchQuery,
                                // Solo dentro de una sección: el catálogo general
                                // conserva su diálogo.
                                onOpenProduct = if (current == Screen.Section) {
                                    openSectionProduct
                                } else {
                                    null
                                },
                                onAddToCart = { producto, variante -> cartViewModel.add(producto, variante) },
                            )
                        }
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
                        Screen.Wheel -> WheelScreen(
                            state = wheelState,
                            onSpin = wheelViewModel::spin,
                            onLanded = wheelViewModel::onLanded,
                        )
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
                            authState = authState,
                            cartState = cartState,
                            onSignOut = authViewModel::signOut,
                            onGoToLogin = { navigate(Screen.Login) },
                            onNombreChange = authViewModel::updatePerfilNombre,
                            onTelefonoChange = authViewModel::updatePerfilTelefono,
                            onSavePerfil = authViewModel::savePerfil,
                            onLoad = cartViewModel::loadSolicitudes,
                            onGoToCatalog = { navigate(Screen.Catalog) },
                            onGoToCart = { navigate(Screen.Cart) },
                            onPagar = cartViewModel::pagar,
                            seccionInicial = routeState.value.id,
                            direccion = addressState,
                            onEditarDireccion = addressViewModel::editar,
                            onGuardarDireccion = addressViewModel::guardar,
                            onBorrarDireccion = addressViewModel::borrar,
                        )
                        Screen.Cart -> CartScreen(
                            state = cartState,
                            onSetCantidad = cartViewModel::setCantidad,
                            onRemove = cartViewModel::remove,
                            onNombreChange = cartViewModel::updateNombre,
                            onTelefonoChange = cartViewModel::updateTelefono,
                            onEmailChange = cartViewModel::updateEmail,
                            onNotasChange = cartViewModel::updateNotas,
                            onEntregaChange = cartViewModel::setEntrega,
                            onConfirmadoChange = cartViewModel::setConfirmado,
                            onCotizarEnvio = cartViewModel::cotizarEnvio,
                            onElegirEnvio = cartViewModel::elegirEnvio,
                            onSubmit = cartViewModel::submit,
                            onPagar = cartViewModel::pagar,
                            onReset = cartViewModel::reset,
                            onGoToLogin = {
                                volverAlCarrito = true
                                navigate(Screen.Login)
                            },
                            onGoToCatalog = { navigate(Screen.Catalog) },
                            onGoToRequests = {
                                routeState.value = Route(Screen.Account, id = SECCION_HISTORIAL)
                            },
                            direccion = addressState,
                            onEditarDireccion = addressViewModel::editar,
                            onGuardarDireccion = addressViewModel::guardar,
                        )
                        Screen.ThankYou -> ThankYouScreen(
                            state = thankYouState,
                            onAlternarDetalle = thankYouViewModel::alternarDetalle,
                            onGoToCatalog = { navigate(Screen.Catalog) },
                            onGoToRequests = {
                                routeState.value = Route(Screen.Account, id = SECCION_HISTORIAL)
                            },
                            onGoToLogin = { navigate(Screen.Login) },
                        )
                        Screen.Terms -> TermsScreen()
                        Screen.Privacy -> PrivacyScreen()
                        Screen.Help -> HelpScreen()
                    }
                }

                // El aviso va dentro del área de contenido y por encima de ella, para
                // que se vea también con un diálogo de producto abierto, que es desde
                // donde más se añade.
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(PassionTheme.spacing.s4),
                )

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
