package ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.browser.window
import kotlinx.coroutines.delay

/**
 * Cada cuánto se comprueba la barra de direcciones.
 *
 * Un octavo de segundo: por debajo de lo que se percibe al pulsar atrás, y una comparación
 * de dos cadenas cortas no cuesta nada.
 */
private const val POLL_MS = 120L

/**
 * Destino actual sincronizado con el hash de la URL del navegador.
 *
 * - Al cargar, lee la ruta desde `window.location.hash`.
 * - Cambiar de destino actualiza la URL (`#/ruta` o `#/ruta/id`), por lo que los enlaces
 *   son compartibles.
 * - Los botones atrás y adelante del navegador cambian de pantalla.
 *
 * **Por qué se consulta la URL en lugar de escuchar `hashchange`.** La versión anterior
 * asignaba un lambda a `window.onhashchange`. Medido en el navegador: la propiedad queda
 * valiendo «function», pero al invocarla a mano no ejecuta nada ni lanza error, así que
 * ese código nunca corrió y el botón atrás cambiaba la URL sin mover la pantalla. Un
 * lambda de Kotlin no sirve como manejador de eventos del DOM en este target.
 *
 * Consultar la URL desde la propia composición evita ese puente por completo, y de paso
 * evita el otro problema que tenía la versión de eventos: una escritura hecha dentro de
 * una llamada del navegador queda fuera del ciclo de Compose y no despierta una
 * recomposición. Aquí la escritura ocurre dentro del ciclo, como en cualquier otro efecto.
 */
@Composable
fun rememberRouteState(): MutableState<Route> {
    val state = remember { mutableStateOf(Route.fromPath(currentPath())) }

    // URL -> estado (atrás/adelante del navegador, y enlaces pegados a mano).
    LaunchedEffect(Unit) {
        while (true) {
            delay(POLL_MS)
            val enLaUrl = Route.fromPath(currentPath())
            if (enLaUrl != state.value) {
                state.value = enLaUrl
            }
        }
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
