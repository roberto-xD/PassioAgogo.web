package ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    // URL -> estado (back/forward del navegador). onhashchange acepta un lambda directo.
    DisposableEffect(Unit) {
        window.onhashchange = { state.value = Route.fromPath(currentPath()) }
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
