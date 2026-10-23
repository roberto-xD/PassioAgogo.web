package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.AddressCrypto
import network.AddressRepository
import network.Direccion
import network.SobreDireccion

data class AddressUiState(
    val cargando: Boolean = false,
    /** Hay una dirección guardada, se haya podido abrir o no. */
    val hayGuardada: Boolean = false,
    /** La que se ve y se edita. Vacía mientras no se abra una guardada. */
    val direccion: Direccion = Direccion(),
    /**
     * Hay sobre guardado pero esta pestaña no puede abrirlo. Dos motivos posibles y no
     * se distinguen: no hay clave en la pestaña, o la persona restableció su contraseña
     * y su copia del sobre ya no abre.
     */
    val bloqueada: Boolean = false,
    val guardando: Boolean = false,
    val mensaje: String? = null,
    val error: String? = null,
    /** El sobre tal cual, para copiarlo a la solicitud sin volver a cifrar. */
    val sobre: SobreDireccion? = null,
) {
    val puedeGuardar: Boolean
        get() = !guardando && !direccion.vacia
}

/**
 * La dirección del cliente, cifrada extremo a extremo.
 *
 * Este ViewModel ve la dirección en claro porque es quien la enseña y la edita, pero no
 * toca la clave: [AddressCrypto] la saca de la pestaña y la usa. Lo que viaja a la base
 * es siempre el sobre cerrado.
 */
class AddressViewModel(
    private val repository: AddressRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(AddressUiState())
    val uiState: StateFlow<AddressUiState> = _uiState.asStateFlow()

    private var salUsuario: String = ""

    /**
     * Carga la dirección y trata de abrirla.
     *
     * Se llama al entrar a la cuenta y tras acceder. Sin clave en la pestaña, la fila se
     * lee igual —para saber que existe— pero queda cerrada.
     */
    fun cargar(sal: String) {
        salUsuario = sal
        if (_uiState.value.cargando) return
        _uiState.update { it.copy(cargando = true, error = null) }

        scope.launch {
            val fila = repository.sobreGuardado()
            if (fila == null) {
                _uiState.update {
                    it.copy(cargando = false, hayGuardada = false, bloqueada = false, sobre = null)
                }
                return@launch
            }

            val sobre = fila.aSobre()
            val abierta = AddressCrypto.descifrar(sobre)
            _uiState.update {
                it.copy(
                    cargando = false,
                    hayGuardada = true,
                    sobre = sobre,
                    direccion = abierta ?: Direccion(),
                    bloqueada = abierta == null,
                )
            }
        }
    }

    fun editar(bloque: (Direccion) -> Direccion) = _uiState.update {
        it.copy(direccion = bloque(it.direccion), error = null, mensaje = null)
    }

    /**
     * Cierra el sobre y lo guarda.
     *
     * Si la dirección estaba bloqueada, guardar la desbloquea: el sobre nuevo se envuelve
     * con la clave de ahora, así que vuelve a abrir.
     */
    fun guardar() {
        val estado = _uiState.value
        if (!estado.puedeGuardar) return

        if (!AddressCrypto.hayClave()) {
            _uiState.update {
                it.copy(error = "Vuelve a entrar para poder guardar tu dirección cifrada.")
            }
            return
        }
        _uiState.update { it.copy(guardando = true, error = null, mensaje = null) }

        scope.launch {
            val clave = repository.claveAdmin()
            if (clave == null) {
                _uiState.update {
                    it.copy(
                        guardando = false,
                        error = "Falta configurar la clave de seguridad de la tienda. Avísanos.",
                    )
                }
                return@launch
            }

            val sobre = AddressCrypto.cifrar(
                direccion = estado.direccion,
                sal = salUsuario,
                adminKeyId = clave.id,
                adminPublica = clave.publica,
            )
            if (sobre == null) {
                _uiState.update {
                    it.copy(guardando = false, error = "No se pudo cifrar la dirección.")
                }
                return@launch
            }

            val error = repository.guardar(sobre, etiqueta = "")
            _uiState.update {
                if (error != null) it.copy(guardando = false, error = error)
                else it.copy(
                    guardando = false,
                    hayGuardada = true,
                    bloqueada = false,
                    sobre = sobre,
                    mensaje = "Dirección guardada y cifrada",
                )
            }
        }
    }

    fun borrar() {
        scope.launch {
            _uiState.update { it.copy(guardando = true, error = null, mensaje = null) }
            val error = repository.borrar()
            _uiState.update {
                if (error != null) it.copy(guardando = false, error = error)
                else AddressUiState(mensaje = "Dirección eliminada")
            }
        }
    }

    /** Al cerrar sesión no queda nada en pantalla de la persona anterior. */
    fun limpiar() {
        salUsuario = ""
        _uiState.value = AddressUiState()
    }
}
