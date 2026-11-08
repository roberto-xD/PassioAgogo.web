package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import models.Acuse
import network.AcuseRepository
import network.AddressCrypto
import network.Direccion
import network.ResultadoAcuse

data class ThankYouUiState(
    val cargando: Boolean = false,
    val acuse: Acuse? = null,
    /** La dirección del pedido, ya abierta. Nula si no se pudo. */
    val direccion: Direccion? = null,
    /**
     * Hay dirección guardada en el pedido pero esta pestaña no puede abrirla.
     *
     * Pasa cuando la clave no está: se pagó desde el móvil y se volvió en otra pestaña,
     * o se cerró y reabrió. La clave vive en `sessionStorage`, que sobrevive a irse a
     * Mercado Pago y volver **en la misma pestaña**, pero no a más que eso.
     */
    val direccionBloqueada: Boolean = false,
    /** Se sigue preguntando a la base si el webhook ya contó algo. */
    val esperandoPago: Boolean = false,
    /** El detalle de los artículos, que empieza plegado. */
    val detalleAbierto: Boolean = false,
    /** La sesión caducó: hay que ofrecerle entrar, no decirle que su pedido no existe. */
    val sinSesion: Boolean = false,
    /** La promesa de empaque discreto, de `site_settings`. Vacía la oculta. */
    val empaque: String = "",
    /** Lo que se leerá en el estado de cuenta de la tarjeta. */
    val statement: String = "",
    /**
     * Los SKU sugeridos, en el orden configurado. Quien pinta la pantalla los resuelve
     * contra el catálogo: así las fichas son **las mismas** del catálogo y se abren en
     * el mismo diálogo, en vez de una card parecida que lleva a otra parte.
     */
    val recomendadosSkus: List<String> = emptyList(),
    val error: String? = null,
)

/**
 * La pantalla de «gracias», al volver de Mercado Pago.
 *
 * Su único trabajo difícil es el tiempo. Volver de pagar **no** significa que sepamos el
 * resultado: la URL de regreso la puede escribir cualquiera, y la verdad la escribe el
 * webhook, que llega por su cuenta y tarda segundos. Así que al entrar se lee la fila y,
 * mientras el pago siga sin confirmar, se vuelve a preguntar unas cuantas veces.
 *
 * Y se para. Un sondeo sin fin dejaría la página preguntando toda la tarde a quien pagó
 * en efectivo con una ficha que no va a pagar hasta mañana.
 */
class ThankYouViewModel(
    private val repository: AcuseRepository = AcuseRepository(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(ThankYouUiState())
    val uiState: StateFlow<ThankYouUiState> = _uiState.asStateFlow()

    private var enCurso: Job? = null
    private var ultimoId: String = ""

    /**
     * Cada cuánto y cuántas veces se vuelve a preguntar.
     *
     * Doce intentos cada dos segundos son veinticuatro, que es de sobra para un webhook
     * que suele tardar menos de cinco. Pasado eso se deja de preguntar y se enseña lo
     * que diga la base, que para un pago en efectivo es la respuesta correcta y
     * definitiva.
     */
    private val esperaMs = 2_000L
    private val intentosMax = 12

    fun cargar(solicitudId: String) {
        if (solicitudId.isBlank()) {
            _uiState.value = ThankYouUiState(error = "No sabemos de qué pedido se trata.")
            return
        }
        // Al volver a la misma pantalla no se reinicia el sondeo: lo dejaría empezando
        // de cero en cada recomposición.
        if (solicitudId == ultimoId && enCurso?.isActive == true) return

        ultimoId = solicitudId
        enCurso?.cancel()
        _uiState.value = ThankYouUiState(cargando = true)

        // Los ajustes y las sugerencias van por su lado: no dependen del pedido ni del
        // sondeo, y hacerlos esperar retrasaría lo único que a esa persona le urge ver.
        scope.launch { cargarAdornos() }

        enCurso = scope.launch {
            var intentos = 0
            while (true) {
                val acuse = when (val r = repository.leer(solicitudId)) {
                    is ResultadoAcuse.Encontrado -> r.acuse
                    ResultadoAcuse.SinSesion -> {
                        _uiState.update {
                            it.copy(
                                cargando = false,
                                esperandoPago = false,
                                sinSesion = true,
                                // Su pago está hecho y registrado; lo único que falta
                                // es volver a identificarse para poder verlo.
                                error = "Vuelve a entrar para ver tu pedido. Si tu pago " +
                                    "se completó, está registrado.",
                            )
                        }
                        return@launch
                    }
                    ResultadoAcuse.NoEsta -> {
                        _uiState.update {
                            it.copy(
                                cargando = false,
                                esperandoPago = false,
                                error = "No encontramos ese pedido.",
                            )
                        }
                        return@launch
                    }
                }

                abrirDireccion(acuse)

                val sigueEsperando = acuse.confirmando && intentos < intentosMax
                _uiState.update {
                    it.copy(cargando = false, acuse = acuse, esperandoPago = sigueEsperando)
                }
                if (!sigueEsperando) return@launch

                intentos++
                delay(esperaMs)
            }
        }
    }

    /**
     * El empaque, el rótulo del estado de cuenta y las sugerencias.
     *
     * Nada de esto es crítico: si no llega, la pantalla se enseña sin ello. Por eso va
     * en su propia corrutina y no corta el resto si falla.
     */
    private suspend fun cargarAdornos() {
        val ajustes = repository.ajustes()
        _uiState.update {
            it.copy(
                empaque = ajustes["acuse_empaque"].orEmpty(),
                statement = ajustes["pago_statement"].orEmpty(),
            )
        }

        val skus = ajustes["acuse_recomendados"].orEmpty()
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
        _uiState.update { it.copy(recomendadosSkus = skus) }
    }

    /**
     * Abre el sobre de la dirección con la clave de la pestaña.
     *
     * No se vuelve a intentar en cada vuelta del sondeo: el sobre no cambia, y
     * descifrar cuesta — la clave se deriva con muchas iteraciones a propósito.
     */
    private suspend fun abrirDireccion(acuse: Acuse) {
        if (_uiState.value.direccion != null) return
        val sobre = acuse.sobre
        if (sobre == null) {
            _uiState.update { it.copy(direccion = null, direccionBloqueada = false) }
            return
        }

        val abierta = AddressCrypto.descifrar(sobre)
        _uiState.update {
            it.copy(direccion = abierta, direccionBloqueada = abierta == null)
        }
    }

    fun alternarDetalle() = _uiState.update { it.copy(detalleAbierto = !it.detalleAbierto) }

    /** Al salir de la pantalla: nada de seguir preguntando en segundo plano. */
    fun limpiar() {
        enCurso?.cancel()
        enCurso = null
        ultimoId = ""
        _uiState.value = ThankYouUiState()
    }
}
