package ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.browser.window

/**
 * Destino actual sincronizado con el hash de la URL del navegador.
 *
 * - Al cargar, lee la ruta desde `window.location.hash`.
 * - Cambiar de destino actualiza la URL (`#/ruta` o `#/ruta/id`), por lo que los enlaces
 *   son compartibles y el botón atrás/adelante del navegador funciona.
 */
@Composable
fun rememberRouteState(): MutableState<Route> {
    val state = remember { mutableStateOf(Route.fromPath(currentPath())) }

    // URL -> estado (atrás/adelante del navegador).
    DisposableEffect(Unit) {
        window.onhashchange = {
            state.value = Route.fromPath(currentPath())
            // Sin esto la pantalla no acompaña al historial. El evento llega del
            // navegador, fuera del ciclo de Compose, y una escritura hecha ahí se queda
            // en el snapshot global sin que nadie avise a la composición: se comprobó en
            // el navegador que el manejador sí corría y la URL sí volvía, y aun así la
            // pantalla se quedaba en la anterior. Esta llamada es la que propaga el
            // cambio. Lanzarlo en el ámbito de la composición no servía: ese despachador
            // está dormido mientras la app no tiene trabajo.
            Snapshot.sendApplyNotifications()
        }
        onDispose { window.onhashchange = null }
    }

    // estado -> URL
    LaunchedEffect(state.value) {
        val target = state.value.hash
        if (window.location.hash != target) {
            window.location.hash = target
        }
    }

    return state
}

private fun currentPath(): String = window.location.hash.removePrefix("#")
