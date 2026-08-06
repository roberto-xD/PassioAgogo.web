package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.ContactRepository
import network.ContactRequest
import network.TurnstileException
import network.requestTurnstileToken

private const val MIN_MENSAJE = 10
const val MAX_NOMBRE = 80
const val MAX_EMAIL = 120
const val MAX_MENSAJE = 2000

data class ContactUiState(
    val nombre: String = "",
    val email: String = "",
    val mensaje: String = "",
    val isSending: Boolean = false,
    val sent: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSending &&
            nombre.isNotBlank() &&
            isValidEmail(email) &&
            mensaje.trim().length >= MIN_MENSAJE
}

/** Validación mínima en cliente; la Edge Function vuelve a validar en el servidor. */
fun isValidEmail(email: String): Boolean {
    val at = email.indexOf('@')
    val dot = email.lastIndexOf('.')
    return at > 0 && dot > at + 1 && dot < email.length - 1 && !email.contains(' ')
}

class ContactViewModel(
    private val repository: ContactRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(ContactUiState())
    val uiState: StateFlow<ContactUiState> = _uiState.asStateFlow()

    fun updateNombre(value: String) = _uiState.update {
        it.copy(nombre = value.take(MAX_NOMBRE), errorMessage = null)
    }

    fun updateEmail(value: String) = _uiState.update {
        it.copy(email = value.take(MAX_EMAIL), errorMessage = null)
    }

    fun updateMensaje(value: String) = _uiState.update {
        it.copy(mensaje = value.take(MAX_MENSAJE), errorMessage = null)
    }

    /** Vuelve al formulario vacío tras un envío correcto. */
    fun reset() {
        _uiState.value = ContactUiState()
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        scope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }

            // Turnstile invisible: normalmente resuelve solo; si Cloudflare pide
            // interacción, el widget aparece sobre el canvas y esto espera al usuario.
            val token = try {
                requestTurnstileToken()
            } catch (e: TurnstileException) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = "No se pudo completar la verificación anti-robots. " +
                            "Recarga la página e inténtalo de nuevo.",
                    )
                }
                return@launch
            }

            val error = repository.send(
                ContactRequest(
                    nombre = state.nombre.trim(),
                    email = state.email.trim(),
                    mensaje = state.mensaje.trim(),
                    token = token,
                )
            )

            _uiState.update {
                if (error == null) it.copy(isSending = false, sent = true, errorMessage = null)
                else it.copy(isSending = false, errorMessage = error)
            }
        }
    }
}
