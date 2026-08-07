package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import models.ProfileDto
import network.AuthRepository

/** Contraseña mínima admitida por Supabase Auth por defecto. */
private const val MIN_PASSWORD = 6

enum class AuthMode { SignIn, SignUp }

data class AuthUiState(
    val isAuthenticated: Boolean = false,
    val profile: ProfileDto? = null,
    val accountEmail: String = "",
    val mode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val nombre: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting &&
            isValidEmail(email.trim()) &&
            password.length >= MIN_PASSWORD &&
            (mode == AuthMode.SignIn || nombre.isNotBlank())
}

class AuthViewModel(
    private val repository: AuthRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // El SDK restaura la sesión guardada al cargar la página, así que esto refleja
        // tanto el inicio de sesión manual como la sesión recuperada.
        scope.launch {
            repository.isAuthenticated.collect { authenticated ->
                _uiState.update {
                    it.copy(
                        isAuthenticated = authenticated,
                        accountEmail = if (authenticated) repository.currentEmail() else "",
                        profile = if (authenticated) it.profile else null,
                    )
                }
                if (authenticated) refreshProfile()
            }
        }
    }

    fun updateEmail(value: String) = _uiState.update {
        it.copy(email = value.trim(), errorMessage = null)
    }

    fun updatePassword(value: String) = _uiState.update {
        it.copy(password = value, errorMessage = null)
    }

    fun updateNombre(value: String) = _uiState.update {
        it.copy(nombre = value.take(MAX_NOMBRE), errorMessage = null)
    }

    fun switchMode(mode: AuthMode) = _uiState.update {
        it.copy(mode = mode, errorMessage = null, infoMessage = null)
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }

            val error = when (state.mode) {
                AuthMode.SignIn -> repository.signIn(state.email.trim(), state.password)
                AuthMode.SignUp -> repository.signUp(
                    email = state.email.trim(),
                    password = state.password,
                    nombre = state.nombre.trim(),
                )
            }

            _uiState.update {
                when {
                    error != null -> it.copy(isSubmitting = false, errorMessage = error)
                    // Si el proyecto exige confirmar el correo, el alta no abre sesión:
                    // el cambio de estado llegará por el flujo cuando el usuario confirme.
                    state.mode == AuthMode.SignUp -> it.copy(
                        isSubmitting = false,
                        password = "",
                        infoMessage = "Cuenta creada. Si tu proyecto requiere confirmación, " +
                            "revisa tu correo antes de iniciar sesión.",
                        mode = AuthMode.SignIn,
                    )
                    else -> it.copy(isSubmitting = false, password = "")
                }
            }
        }
    }

    fun signOut() {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val error = repository.signOut()
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    errorMessage = error,
                    email = "",
                    password = "",
                    nombre = "",
                    infoMessage = null,
                )
            }
        }
    }

    private suspend fun refreshProfile() {
        val profile = repository.loadProfile()
        _uiState.update { it.copy(profile = profile) }
    }
}
