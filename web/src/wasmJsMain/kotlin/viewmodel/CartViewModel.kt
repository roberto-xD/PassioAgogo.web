package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import models.Cart
import models.CartItem
import models.Solicitud
import network.CartStorage
import network.EnvioResult
import network.OrdersRepository
import ui.pgmodels.PGDataCard

/** Tope por renglón. Más que esto deja de ser una compra y es un pedido a mayoreo. */
private const val MAX_POR_RENGLON = 99

data class CartUiState(
    val cart: Cart = Cart(),
    /** Datos de contacto que acompañan a la solicitud. */
    val nombre: String = "",
    val telefono: String = "",
    val email: String = "",
    val notas: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    /** Folio de la solicitud recién enviada; con él se enseña el acuse. */
    val folioEnviado: Long? = null,
    /** Se intentó enviar sin sesión: hay que pedir acceso sin perder el carrito. */
    val pideSesion: Boolean = false,
    val solicitudes: List<Solicitud>? = null,
    val cargandoSolicitudes: Boolean = false,
) {
    val unidades: Int get() = cart.unidades
    val canSubmit: Boolean
        get() = !isSubmitting && cart.items.isNotEmpty() && nombre.trim().isNotEmpty()
}

/**
 * El carrito y el envío de la solicitud.
 *
 * El carrito vive en el navegador hasta que se envía: `order_requests` no tiene estado
 * de borrador, así que crear la fila antes convertiría cada carrito abandonado en una
 * solicitud pendiente para administración.
 */
class CartViewModel(
    private val repository: OrdersRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(CartUiState(cart = CartStorage.load()))
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    /**
     * Añade un producto. Si ya estaba, suma a lo que hubiera en vez de duplicar el
     * renglón: `order_request_items` tiene única (solicitud, variante) y dos renglones
     * de lo mismo serían rechazados al enviar.
     */
    fun add(product: PGDataCard, cantidad: Int = 1) {
        if (product.variantId.isBlank()) return
        actualizar { cart ->
            val existente = cart.items.firstOrNull { it.variantId == product.variantId }
            val items = if (existente == null) {
                cart.items + CartItem(
                    variantId = product.variantId,
                    nombre = product.productTittle,
                    sku = product.productCode,
                    precio = product.precioEstimado,
                    cantidad = cantidad.coerceIn(1, MAX_POR_RENGLON),
                    slug = product.slug,
                    imagen = product.urlImage,
                )
            } else {
                cart.items.map {
                    if (it.variantId == product.variantId) {
                        it.copy(cantidad = (it.cantidad + cantidad).coerceAtMost(MAX_POR_RENGLON))
                    } else {
                        it
                    }
                }
            }
            cart.copy(items = items)
        }
    }

    /** Fija la cantidad de un renglón; a cero, lo quita. */
    fun setCantidad(variantId: String, cantidad: Int) {
        if (cantidad <= 0) {
            remove(variantId)
            return
        }
        actualizar { cart ->
            cart.copy(
                items = cart.items.map {
                    if (it.variantId == variantId) {
                        it.copy(cantidad = cantidad.coerceAtMost(MAX_POR_RENGLON))
                    } else {
                        it
                    }
                }
            )
        }
    }

    fun remove(variantId: String) = actualizar { cart ->
        cart.copy(items = cart.items.filterNot { it.variantId == variantId })
    }

    fun clear() = actualizar { Cart() }

    fun updateNombre(v: String) = _uiState.update { it.copy(nombre = v, errorMessage = null) }
    fun updateTelefono(v: String) = _uiState.update { it.copy(telefono = v, errorMessage = null) }
    fun updateEmail(v: String) = _uiState.update { it.copy(email = v, errorMessage = null) }
    fun updateNotas(v: String) = _uiState.update { it.copy(notas = v) }

    /**
     * Rellena los datos de contacto con lo que ya sabemos de la cuenta, sin pisar lo que
     * la persona haya escrito: puede querer que el pedido vaya a nombre de otra.
     */
    fun prefill(nombre: String, email: String) = _uiState.update {
        it.copy(
            nombre = it.nombre.ifBlank { nombre },
            email = it.email.ifBlank { email },
        )
    }

    /** Se vuelve del acuse al carrito, o se descarta el aviso de sesión. */
    fun reset() = _uiState.update {
        it.copy(folioEnviado = null, pideSesion = false, errorMessage = null)
    }

    fun submit() {
        val estado = _uiState.value
        if (!estado.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null, pideSesion = false) }

        scope.launch {
            val resultado = repository.enviar(
                items = estado.cart.items,
                nombre = estado.nombre,
                telefono = estado.telefono,
                email = estado.email,
                notas = estado.notas,
            )
            when (resultado) {
                is EnvioResult.Enviada -> {
                    // El carrito se vacía solo cuando la base confirmó: si fallara, lo
                    // que la persona había armado seguiría ahí.
                    CartStorage.clear()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            cart = Cart(),
                            notas = "",
                            folioEnviado = resultado.folio,
                            solicitudes = null,
                        )
                    }
                }
                is EnvioResult.Fallo -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = resultado.mensaje)
                }
                EnvioResult.SinSesion -> _uiState.update {
                    it.copy(isSubmitting = false, pideSesion = true)
                }
            }
        }
    }

    fun loadSolicitudes() {
        if (_uiState.value.cargandoSolicitudes) return
        _uiState.update { it.copy(cargandoSolicitudes = true) }
        scope.launch {
            val lista = repository.misSolicitudes()
            _uiState.update { it.copy(cargandoSolicitudes = false, solicitudes = lista) }
        }
    }

    /** Toda modificación pasa por aquí para que el navegador y la pantalla no se separen. */
    private fun actualizar(bloque: (Cart) -> Cart) {
        _uiState.update { estado ->
            val nuevo = bloque(estado.cart)
            CartStorage.save(nuevo)
            estado.copy(cart = nuevo, errorMessage = null)
        }
    }
}
