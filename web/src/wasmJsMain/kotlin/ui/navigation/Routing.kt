package ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.browser.window
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    // URL -> estado (atrás/adelante del navegador).
    DisposableEffect(Unit) {
        window.onhashchange = {
            // El cambio se lanza en el ámbito de la composición en lugar de escribir el
            // estado aquí mismo. El evento llega del navegador, fuera del ciclo de
            // Compose, y una escritura hecha ahí no despierta una recomposición: se
            // comprobó en el navegador que el manejador sí se ejecutaba y la URL sí
            // cambiaba, pero la pantalla se quedaba en la anterior. Al lanzarlo, la
            // escritura ocurre dentro del ciclo y la pantalla acompaña al historial.
            scope.launch { state.value = Route.fromPath(currentPath()) }
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
