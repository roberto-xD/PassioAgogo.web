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
import models.MetodoEntrega
import models.OpcionEnvio
import models.Solicitud
import network.CartStorage
import network.CotizacionResult
import network.EnvioResult
import network.OrdersRepository
import network.ShippingRepository
import network.SobreDireccion
import ui.pgmodels.PGDataCard
import ui.pgmodels.PGVariant

/** Tope por renglón. Más que esto deja de ser una compra y es un pedido a mayoreo. */
private const val MAX_POR_RENGLON = 99

/**
 * Un teléfono mexicano, ya sin espacios ni guiones.
 *
 * Lo comparten el carrito, que lo exige para enviar, y el perfil, que se queda con el que
 * se escribió. Si cambia la regla, cambia en los dos sitios a la vez.
 */
internal const val DIGITOS_TELEFONO = 10

data class CartUiState(
    val cart: Cart = Cart(),
    /** Datos de contacto que acompañan a la solicitud. */
    val nombre: String = "",
    val telefono: String = "",
    val email: String = "",
    val notas: String = "",
    val entrega: MetodoEntrega = MetodoEntrega.Domicilio,
    /**
     * La casilla de «entiendo cómo sigue esto». Sin marcarla no se puede enviar: lo que
     * dice —que el trato continúa por WhatsApp y que ahí se paga— es la única parte del
     * proceso que la web no puede enseñar por sí misma.
     */
    val confirmado: Boolean = false,
    /**
     * Qué datos trajo la cuenta. Lo que vino de ahí se enseña en texto; lo que no, se pide.
     *
     * Son banderas y no «¿está vacío el campo?» porque eso cambiaría de respuesta en
     * cuanto se teclea la primera letra: el campo se convertiría en texto a media palabra.
     */
    val nombreEnPerfil: Boolean = false,
    val telefonoEnPerfil: Boolean = false,
    val emailEnPerfil: Boolean = false,
    /** Hay dirección guardada, se pueda abrir en esta sesión o no. */
    val hayDireccion: Boolean = false,
    /** Opciones de envío cotizadas, o null si todavía no se han pedido. */
    val opcionesEnvio: List<OpcionEnvio>? = null,
    val cotizando: Boolean = false,
    val errorEnvio: String? = null,
    /** La elegida; viaja con la solicitud para que administración sepa con qué enviar. */
    val envioElegido: OpcionEnvio? = null,
    /** El bulto excede la caja mayor: lo cotizado va por debajo de la guía. */
    val envioRecortado: Boolean = false,
    /** Sin sesión no hay dónde guardar una dirección cifrada, ni a quién atribuir el pedido. */
    val autenticado: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    /** Folio de la solicitud recién enviada; con él se enseña el acuse. */
    val folioEnviado: Long? = null,
    /** Se intentó enviar sin sesión: hay que pedir acceso sin perder el carrito. */
    val pideSesion: Boolean = false,
    val solicitudes: List<Solicitud>? = null,
    val cargandoSolicitudes: Boolean = false,
    /**
     * Cuántas veces se ha añadido algo. No es el número de artículos: es un contador
     * que solo sube, para que la pantalla sepa que hubo un añadido nuevo aunque el
     * mensaje sea idéntico al anterior.
     */
    val agregados: Int = 0,
) {
    val unidades: Int get() = cart.unidades

    /** El teléfono es obligatorio: es por donde sigue el trato. */
    val telefonoCompleto: Boolean
        get() = telefono.count { it.isDigit() } >= DIGITOS_TELEFONO

    /**
     * Para enviar a domicilio hace falta un domicilio. No se exige poder *leerlo*: si el
     * sobre está guardado pero esta sesión no lo abre, igual se copia a la solicitud y la
     * tienda lo descifra con su propia clave.
     *
     * Sin sesión no se exige: no hay dónde guardarlo. Quien llegue así se topa primero
     * con el aviso de que necesita una cuenta, y la dirección se le pide al volver.
     */
    val faltaDireccion: Boolean
        get() = autenticado && entrega == MetodoEntrega.Domicilio && !hayDireccion

    val canSubmit: Boolean
        get() = !isSubmitting &&
            cart.items.isNotEmpty() &&
            nombre.trim().isNotEmpty() &&
            telefonoCompleto &&
            confirmado &&
            !faltaDireccion
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
    private val shipping: ShippingRepository = ShippingRepository(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _uiState = MutableStateFlow(CartUiState(cart = CartStorage.load()))
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    /**
     * Añade un producto. Si ya estaba, suma a lo que hubiera en vez de duplicar el
     * renglón: `order_request_items` tiene única (solicitud, variante) y dos renglones
     * de lo mismo serían rechazados al enviar.
     */
    fun add(product: PGDataCard, variant: PGVariant?, cantidad: Int = 1) {
        // Sin variante elegida se usa la del precio anunciado, que es la más barata.
        val elegida = variant
            ?: product.variants.firstOrNull { it.id == product.variantId }
            ?: product.variants.firstOrNull()
            ?: return
        if (elegida.id.isBlank()) return

        actualizar { cart ->
            val existente = cart.items.firstOrNull { it.variantId == elegida.id }
            val items = if (existente == null) {
                cart.items + CartItem(
                    variantId = elegida.id,
                    // En el carrito, dos renglones del mismo producto tienen que poder
                    // distinguirse. El título de la variante ya trae el nombre del
                    // producto; sin título se compone a mano.
                    nombre = when {
                        elegida.titulo.isNotBlank() -> elegida.titulo
                        product.variants.size > 1 && elegida.etiqueta.isNotBlank() ->
                            "${product.productTittle} · ${elegida.etiqueta}"
                        else -> product.productTittle
                    },
                    sku = elegida.sku,
                    precio = elegida.precio,
                    cantidad = cantidad.coerceIn(1, MAX_POR_RENGLON),
                    slug = product.slug,
                    imagen = product.urlImage,
                )
            } else {
                cart.items.map {
                    if (it.variantId == elegida.id) {
                        it.copy(cantidad = (it.cantidad + cantidad).coerceAtMost(MAX_POR_RENGLON))
                    } else {
                        it
                    }
                }
            }
            cart.copy(items = items)
        }
        _uiState.update { it.copy(agregados = it.agregados + 1) }
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

    fun setEntrega(v: MetodoEntrega) = _uiState.update {
        olvidarCotizacion(it).copy(entrega = v, errorMessage = null)
    }

    fun setConfirmado(v: Boolean) = _uiState.update { it.copy(confirmado = v) }

    fun setAutenticado(v: Boolean) = _uiState.update { it.copy(autenticado = v) }

    /**
     * Pide las opciones de envío para el código postal de la dirección guardada.
     *
     * Se dispara con un botón y no al abrir el carrito: cada cotización son varias
     * llamadas a paqueterías, y quien solo viene a ver qué lleva en el carrito no tiene
     * por qué pagarlas en espera.
     */
    fun cotizarEnvio(cpDestino: String) {
        if (_uiState.value.cotizando) return
        _uiState.update { it.copy(cotizando = true, errorEnvio = null) }

        scope.launch {
            when (val r = shipping.cotizar(cpDestino, _uiState.value.cart.items)) {
                is CotizacionResult.Lista -> _uiState.update {
                    it.copy(
                        cotizando = false,
                        opcionesEnvio = r.opciones,
                        envioRecortado = r.recortado,
                        // Preseleccionada la más barata, que es la primera: así quien no
                        // quiera elegir tiene una válida sin hacer nada.
                        envioElegido = r.opciones.firstOrNull(),
                    )
                }
                is CotizacionResult.Fallo -> _uiState.update {
                    it.copy(cotizando = false, errorEnvio = r.mensaje, opcionesEnvio = null)
                }
            }
        }
    }

    fun elegirEnvio(opcion: OpcionEnvio) = _uiState.update { it.copy(envioElegido = opcion) }

    /**
     * Olvida lo cotizado.
     *
     * Se llama al cambiar el carrito o el método de entrega: un precio calculado para
     * otro bulto, o para un envío que ya no es a domicilio, es peor que no tener ninguno.
     */
    private fun olvidarCotizacion(estado: CartUiState) = estado.copy(
        opcionesEnvio = null,
        envioElegido = null,
        envioRecortado = false,
        errorEnvio = null,
    )

    /**
     * Rellena los datos de contacto con lo que ya sabemos de la cuenta, sin pisar lo que
     * la persona haya escrito: puede querer que el pedido vaya a nombre de otra.
     *
     * De paso retira el aviso de "necesitas una cuenta". Se llama al abrir sesión, y a
     * partir de ahí el aviso es falso: dejarlo puesto haría pensar que sigue faltando
     * algo justo cuando ya se puede enviar.
     */
    fun prefill(nombre: String, email: String, telefono: String = "") = _uiState.update {
        it.copy(
            nombre = it.nombre.ifBlank { nombre },
            email = it.email.ifBlank { email },
            telefono = it.telefono.ifBlank { telefono },
            nombreEnPerfil = nombre.isNotBlank(),
            emailEnPerfil = email.isNotBlank(),
            telefonoEnPerfil = telefono.isNotBlank(),
            pideSesion = false,
        )
    }

    /** Se vuelve del acuse al carrito, o se descarta el aviso de sesión. */
    fun reset() = _uiState.update {
        it.copy(folioEnviado = null, pideSesion = false, errorMessage = null)
    }

    /** El sobre de la dirección, puesto desde fuera; se copia tal cual a la solicitud. */
    private var sobreDireccion: SobreDireccion? = null

    fun setDireccion(sobre: SobreDireccion?) {
        sobreDireccion = sobre
        _uiState.update { it.copy(hayDireccion = sobre != null) }
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
                entrega = estado.entrega,
                // Para recoger en persona no se manda el domicilio, aunque esté
                // guardado: la tienda no lo necesita y lo que no se manda no se filtra.
                direccion = sobreDireccion.takeIf { estado.entrega == MetodoEntrega.Domicilio },
                envio = estado.envioElegido.takeIf { estado.entrega == MetodoEntrega.Domicilio },
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
                            // La casilla vuelve a su sitio: es por solicitud, no por
                            // sesión, y la siguiente tiene que confirmarse igual.
                            confirmado = false,
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
            // Cambió lo que va dentro de la caja, así que lo cotizado dejó de valer.
            olvidarCotizacion(estado).copy(cart = nuevo, errorMessage = null)
        }
    }
}
