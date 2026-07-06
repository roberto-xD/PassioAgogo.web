package ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.browser.window

/**
 * Estado de la pantalla actual sincronizado con el hash de la URL del navegador.
 *
 * - Al cargar, lee la ruta desde `window.location.hash`.
 * - Cambios de pantalla actualizan la URL (`#/ruta`), por lo que los enlaces son
 *   compartibles y el botón atrás/adelante del navegador cambia de pantalla.
 */
@Composable
fun rememberScreenState(): MutableState<Screen> {
    val state = remember { mutableStateOf(Screen.fromRoute(currentRoute())) }

    // URL -> estado (back/forward del navegador). onhashchange acepta un lambda directo.
    DisposableEffect(Unit) {
        window.onhashchange = { state.value = Screen.fromRoute(currentRoute()) }
        onDispose { window.onhashchange = null }
    }

    // estado -> URL
    LaunchedEffect(state.value) {
        val target = "#/${state.value.route}"
        if (window.location.hash != target) {
            window.location.hash = target
        }
    }

    return state
}

private fun currentRoute(): String =
    window.location.hash.removePrefix("#").removePrefix("/")
