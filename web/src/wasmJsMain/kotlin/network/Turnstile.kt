package network

import kotlinx.coroutines.delay

/**
 * Cloudflare Turnstile en modo invisible.
 *
 * La app se dibuja en un `<canvas>`, así que el widget no puede montarse dentro de la
 * jerarquía de Compose: vive en el DOM (`#turnstile-host`) y se gestiona con el helper
 * `window.paTurnstile` definido en `index.html`. Aquí solo se dispara el reto y se
 * consulta su estado hasta obtener el token.
 *
 * La SITE_KEY es **pública por diseño** (viaja al navegador). La clave secreta vive
 * únicamente en la Edge Function que verifica el token contra Cloudflare.
 */
object TurnstileConfig {
    // TODO: reemplazar por la site key real (Cloudflare → Turnstile → Add widget).
    const val SITE_KEY: String = "0x4AAAAAAEHnqKhFDRDDkTr8"

    /** Sin configurar, el formulario se envía sin token (útil en desarrollo local). */
    val isConfigured: Boolean
        get() = !SITE_KEY.startsWith("YOUR-")
}

class TurnstileException(message: String) : Exception(message)

private fun turnstileExecuteJs(siteKey: String): Unit = js("window.paTurnstile.execute(siteKey)")

private fun turnstileStatusJs(): String = js("window.paTurnstile.status()")

private fun turnstileTokenJs(): String = js("window.paTurnstile.token()")

private fun turnstileErrorJs(): String = js("window.paTurnstile.error()")

/**
 * Lanza el reto y espera el token.
 *
 * El resultado llega por callback en JS, por lo que se consulta el estado en intervalos
 * cortos: es la forma más simple y estable de puentear callbacks JS ↔ corrutinas en
 * wasmJs. En el caso normal (sin interacción) resuelve en unos cientos de milisegundos;
 * si Cloudflare pide interacción, el usuario dispone del timeout completo.
 *
 * Devuelve cadena vacía si Turnstile no está configurado.
 */
suspend fun requestTurnstileToken(timeoutMs: Int = 120_000): String {
    if (!TurnstileConfig.isConfigured) return ""

    turnstileExecuteJs(TurnstileConfig.SITE_KEY)

    val stepMs = 100
    repeat(timeoutMs / stepMs) {
        when (turnstileStatusJs()) {
            "ready" -> return turnstileTokenJs()
            "error" -> throw TurnstileException(turnstileErrorJs())
        }
        delay(stepMs.toLong())
    }
    throw TurnstileException("tiempo-agotado")
}
