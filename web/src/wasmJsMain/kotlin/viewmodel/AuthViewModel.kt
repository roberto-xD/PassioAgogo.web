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
import network.AddressCrypto
import network.AddressRepository
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
    /** Datos del perfil mientras se editan en «Mi cuenta». */
    val perfilNombre: String = "",
    val perfilTelefono: String = "",
    val guardandoPerfil: Boolean = false,
    val perfilMensaje: String? = null,
    val perfilError: String? = null,
    /**
     * Sal del cliente para derivar su clave de cifrado. No es secreta: su trabajo es
     * que la misma contraseña en dos cuentas distintas no dé la misma clave.
     */
    val kdfSalt: String = "",
) {
    /** Hay algo distinto de lo guardado, y el nombre no quedó vacío. */
    val perfilModificado: Boolean
        get() = perfilNombre.trim().isNotEmpty() && (
            perfilNombre.trim() != profile?.nombre.orEmpty().trim() ||
                perfilTelefono.trim() != profile?.telefono.orEmpty().trim()
            )
    val canSubmit: Boolean
        get() = !isSubmitting &&
            isValidEmail(email.trim()) &&
            password.length >= MIN_PASSWORD &&
            (mode == AuthMode.SignIn || nombre.isNotBlank())
}

class AuthViewModel(
    private val repository: AuthRepository,
    private val addresses: AddressRepository = AddressRepository(),
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

            // El cifrado se prepara aquí y no después porque este es el único momento
            // en que la web tiene la contraseña. Un paso más tarde ya se ha borrado.
            if (error == null) prepararCifrado(state.password)

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
            // Primero la clave: si el cierre de sesión fallara, lo que no puede quedar
            // es la clave de descifrado viva en una pestaña que alguien más usará.
            AddressCrypto.olvidarClave()
            val error = repository.signOut()
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    errorMessage = error,
                    email = "",
                    password = "",
                    nombre = "",
                    infoMessage = null,
                    kdfSalt = "",
                )
            }
        }
    }

    fun updatePerfilNombre(value: String) = _uiState.update {
        it.copy(perfilNombre = value.take(MAX_NOMBRE), perfilError = null, perfilMensaje = null)
    }

    fun updatePerfilTelefono(value: String) = _uiState.update {
        it.copy(perfilTelefono = value.take(20), perfilError = null, perfilMensaje = null)
    }

    fun savePerfil() {
        val estado = _uiState.value
        if (estado.guardandoPerfil || !estado.perfilModificado) return
        _uiState.update { it.copy(guardandoPerfil = true, perfilError = null, perfilMensaje = null) }

        scope.launch {
            val error = repository.updateProfile(estado.perfilNombre, estado.perfilTelefono)
            if (error != null) {
                _uiState.update { it.copy(guardandoPerfil = false, perfilError = error) }
            } else {
                // Se relee en vez de dar por hecho lo enviado: así lo que se ve es lo
                // que quedó guardado, recortes y normalizaciones de la base incluidos.
                refreshProfile()
                _uiState.update { it.copy(guardandoPerfil = false, perfilMensaje = "Datos guardados") }
            }
        }
    }

    /**
     * Se queda con el teléfono que la persona escribió al enviar una solicitud.
     *
     * Así no se le vuelve a pedir en el siguiente pedido. **No pisa el que ya hubiera**:
     * una solicitud puede ir a nombre de otra persona, y su teléfono no tiene por qué
     * convertirse en el contacto de la cuenta. Ese se cambia en «Mi cuenta», a propósito.
     *
     * Falla en silencio: la solicitud ya se envió y el teléfono viaja en ella. Un aviso
     * de error aquí hablaría de algo que a quien acaba de pedir no le importa.
     */
    fun recordarTelefono(telefono: String) {
        val estado = _uiState.value
        val digitos = telefono.filter { it.isDigit() }
        if (!estado.isAuthenticated || digitos.length < DIGITOS_TELEFONO) return
        if (estado.profile?.telefono.orEmpty().isNotBlank()) return

        scope.launch {
            if (repository.updateTelefono(digitos) == null) refreshProfile()
        }
    }

    /**
     * Comprueba la sesión contra el cliente, sin esperar al flujo.
     *
     * La pantalla la llama cada poco. Si una emisión no llegara a despertar a Compose
     * —cosa que ya nos pasó con el botón de atrás del navegador—, la barra seguiría
     * diciendo «Acceder» con la sesión abierta hasta recargar a mano.
     */
    fun syncSession() {
        val hay = repository.hasSessionNow()
        if (hay == _uiState.value.isAuthenticated) return
        _uiState.update {
            it.copy(
                isAuthenticated = hay,
                accountEmail = if (hay) repository.currentEmail() else "",
                profile = if (hay) it.profile else null,
            )
        }
        if (hay) scope.launch { refreshProfile() }
    }

    /**
     * Deriva la clave de cifrado del cliente y la deja en la pestaña.
     *
     * La sal vive en su perfil. Si aún no tiene —primera vez, o cuenta creada antes de
     * que esto existiera— se genera una y se guarda. No es secreta, así que guardarla
     * en claro no quita nada.
     */
    private suspend fun prepararCifrado(password: String) {
        if (password.isBlank()) return

        val perfil = repository.loadProfile()
        val sal = perfil?.kdfSalt?.takeIf { it.isNotBlank() }
            ?: AddressCrypto.nuevaSal()?.also { nueva ->
                // Si no se puede guardar, no se deriva con ella: una clave derivada de
                // una sal que la próxima sesión no conocerá dejaría el sobre inabrible.
                if (!addresses.guardarSal(nueva)) return
            }
            ?: return

        AddressCrypto.derivarYRecordar(password, sal)
        _uiState.update { it.copy(kdfSalt = sal) }
    }

    private suspend fun refreshProfile() {
        val profile = repository.loadProfile()
        _uiState.update {
            // Los campos de edición siguen al perfil mientras no se hayan tocado.
            it.copy(
                profile = profile,
                perfilNombre = it.perfilNombre.ifBlank { profile?.nombre.orEmpty() },
                perfilTelefono = it.perfilTelefono.ifBlank { profile?.telefono.orEmpty() },
                kdfSalt = it.kdfSalt.ifBlank { profile?.kdfSalt.orEmpty() },
            )
        }
    }
}
