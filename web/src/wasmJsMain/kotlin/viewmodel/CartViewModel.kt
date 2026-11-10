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
import network.CancelacionEstado
import network.CancelacionResult
import network.CartStorage
import network.CodigoRepository
import network.CodigoResult
import network.CotizacionResult
import network.EnvioResult
import network.OrdersRepository
import network.PagoResult
import network.PaymentsRepository
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

/**
 * El desenlace del último intento de cancelación, para el diálogo.
 *
 * Lleva el id de la solicitud porque el historial tiene varias tarjetas y el aviso es
 * de una: sin él, cancelar la de arriba contestaría también en la de abajo.
 */
data class AvisoCancelacion(
    val solicitudId: String,
    val estado: CancelacionEstado,
    val mensaje: String,
    val esError: Boolean = false,
)

data class CartUiState(
    val cart: Cart = Cart(),
    /** Datos de contacto que acompañan a la solicitud. */
    val nombre: String = "",
    val telefono: String = "",
    val email: String = "",
    val notas: String = "",
    val entrega: MetodoEntrega = MetodoEntrega.Domicilio,
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
    /**
     * Id de la solicitud recién enviada, para poder cobrarla sin salir del acuse.
     *
     * Va aparte del folio porque son dos cosas con dos usos: el folio es lo que se le
     * enseña a la persona y lo que dice por WhatsApp; el id es lo que identifica la
     * solicitud para `crear-pago`.
     */
    val idEnviada: String? = null,
    /**
     * Lo que sumaba lo enviado, artículos más envío.
     *
     * Se guarda porque al enviar se vacía el carrito, y el acuse necesita poder decir
     * cuánto se va a pagar. Sacarlo de `cart` daría cero.
     */
    val totalEnviado: Double = 0.0,
    /** Qué solicitud se está cobrando. Para apagar solo su botón, no todos. */
    val pagando: String? = null,
    val errorPago: String? = null,
    /**
     * La URL de Mercado Pago, en cuanto la hay. La pantalla lleva allí el navegador y
     * la borra.
     *
     * Pasa por el estado en vez de navegarse desde aquí porque irse de la página es
     * cosa de la pantalla; este ViewModel no sabe de navegadores.
     */
    val urlPago: String? = null,
    /** Lo que se está escribiendo en el campo del código. */
    val codigo: String = "",
    val validandoCodigo: Boolean = false,
    /** El código ya comprobado. Vacío si no hay ninguno puesto. */
    val codigoAplicado: String = "",
    /** El porcentaje de ese código. Solo para enseñar el total: cobra el servidor. */
    val codigoPct: Int = 0,
    /** Con un premio de regalo, qué se gana. Vacío si el código descuenta. */
    val codigoRegalo: String = "",
    /** `descuento`, `regalo` o `envio_gratis`. */
    val codigoTipo: String = "",
    /**
     * El código sirve pero el carrito no llega a su mínimo.
     *
     * Se queda puesto, en gris, y se reactiva solo al subir: quitarlo obligaría a
     * volver a teclearlo después de añadir un artículo.
     */
    val codigoEsperandoMinimo: Boolean = false,
    val codigoFaltan: Double = 0.0,
    /** El «a partir de $X» del código puesto. Cero si no tiene mínimo. */
    val codigoMinimo: Double = 0.0,
    val codigoError: String? = null,
    /** Se intentó enviar sin sesión: hay que pedir acceso sin perder el carrito. */
    val pideSesion: Boolean = false,
    val solicitudes: List<Solicitud>? = null,
    val cargandoSolicitudes: Boolean = false,
    /** Qué solicitud se está cancelando. Para apagar solo su botón, no todos. */
    val cancelando: String? = null,
    /** Lo que hay que contarle del último intento de cancelación. */
    val avisoCancelacion: AvisoCancelacion? = null,
    /**
     * Cuántas veces se ha añadido algo. No es el número de artículos: es un contador
     * que solo sube, para que la pantalla sepa que hubo un añadido nuevo aunque el
     * mensaje sea idéntico al anterior.
     */
    val agregados: Int = 0,
) {
    val unidades: Int get() = cart.unidades

    /**
     * El envío va gratis: código de envío gratis puesto y con su mínimo cumplido.
     *
     * Mientras falte para el mínimo **no** aplica, así que la lista de paqueterías
     * sigue a la vista: quien se quede corto tiene que poder elegir y pagar su envío.
     *
     * Lleva dentro el método de entrega a propósito. Un código de envío gratis puesto
     * en una entrega personal no cubre nada —no hay envío—, y sin esta condición el
     * carrito enseñaría un renglón «Envío · Gratis» en un pedido que se recoge a mano.
     */
    val envioGratis: Boolean
        get() = codigoTipo == "envio_gratis" &&
            codigoAplicado.isNotBlank() &&
            !codigoEsperandoMinimo &&
            entrega == MetodoEntrega.Domicilio

    /**
     * Lo que descuenta el código, en pesos.
     *
     * **Solo sobre los artículos.** El envío lo paga la tienda entero a la paquetería y
     * recortarlo saldría de su bolsillo. Mismo cálculo que hace `crear-pago`, que es
     * quien cobra: esto solo enseña la cifra.
     */
    val descuento: Double
        get() = if (codigoPct > 0 && !codigoEsperandoMinimo) {
            // Redondeado, **igual que `crear-pago`**. Truncar aquí y redondear allí
            // haría que la pantalla prometiera un centavo distinto del que se cobra, y
            // un centavo de diferencia en un total es de las cosas que más desconfianza
            // generan.
            kotlin.math.round(cart.total * codigoPct) / 100.0
        } else {
            0.0
        }

    /**
     * Lo que va a pagar: artículos, menos el descuento, más el envío.
     *
     * Vive aquí y no en la pantalla porque lo necesitan dos sitios —los totales del
     * carrito y el acuse—, y dos sumas iguales escritas aparte acaban siendo dos sumas
     * distintas.
     *
     * Para recoger en persona no hay envío que sumar aunque quede una cotización vieja
     * de cuando la elección era otra. Con envío gratis tampoco, aunque siga elegida
     * una paquetería de antes de poner el código.
     */
    val totalConEnvio: Double
        get() = cart.total - descuento + when {
            entrega != MetodoEntrega.Domicilio -> 0.0
            envioGratis -> 0.0
            else -> envioElegido?.precio ?: 0.0
        }

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

    /**
     * Lo que hace falta salga por donde salga: quién pide, cómo localizarle y, para
     * envío a domicilio, a dónde se manda.
     */
    val datosListos: Boolean
        get() = !isSubmitting &&
            pagando == null &&
            cart.items.isNotEmpty() &&
            nombre.trim().isNotEmpty() &&
            telefonoCompleto &&
            !faltaDireccion

    /**
     * ¿Se puede ir a pagar?
     *
     * A domicilio hace falta haber elegido envío: sin eso el total no lleva el envío
     * dentro y cobraríamos de menos. Para recoger en persona no hay envío que elegir.
     */
    val puedePagar: Boolean
        get() = datosListos && (
            entrega == MetodoEntrega.Personal ||
                envioGratis ||
                envioElegido != null
            )

    /**
     * ¿Se puede seguir por WhatsApp?
     *
     * Solo en entrega personal, y con la casilla marcada: ese camino deja el trato a
     * medias en un mensaje, y la casilla es donde se cuenta cómo sigue.
     *
     * La casilla **no** bloquea el pago. Pagar es el mismo acto en los dos escenarios y
     * a domicilio no se pide ninguna casilla; exigirla aquí y no allí sería arbitrario.
     */
    val puedeSeguirPorWhatsApp: Boolean
        get() = datosListos && entrega == MetodoEntrega.Personal

    val entregaPersonal: Boolean
        get() = entrega == MetodoEntrega.Personal
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
    private val payments: PaymentsRepository = PaymentsRepository(),
    private val codigos: CodigoRepository = CodigoRepository(),
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

    fun setAutenticado(v: Boolean) = _uiState.update { it.copy(autenticado = v) }

    /** Quién tenía la sesión la última vez que se miró. Nulo: todavía no se ha mirado. */
    private var ultimoUsuario: String? = null

    /**
     * Cambió quién tiene la sesión: hay que olvidar a la persona anterior.
     *
     * Nada de esto se limpiaba al cerrar sesión, y el resultado era que la cuenta
     * siguiente heredaba lo de la anterior en la misma pestaña: su nombre, su
     * teléfono, su historial de solicitudes, su código de la ruleta y —lo más grave—
     * el sobre cifrado de su dirección, que se habría copiado tal cual a la solicitud
     * de otra persona. `prefill` no lo tapaba porque rellena con `ifBlank`, y los
     * campos no estaban en blanco: estaban llenos con los datos del anterior.
     *
     * **Entrar desde invitado no limpia nada.** Ese es el camino de quien arma un
     * carrito, se topa con «necesitas una cuenta» y accede: vaciárselo justo entonces
     * sería castigarle por haber hecho lo que se le pidió. Solo se limpia cuando la
     * persona que había deja de ser la que hay.
     */
    fun sesionCambio(usuarioId: String) {
        val anterior = ultimoUsuario
        ultimoUsuario = usuarioId
        // Primera noticia de la sesión —al cargar la página— o un invitado que entra.
        if (anterior == null || anterior.isBlank()) return
        if (anterior == usuarioId) return

        CartStorage.clear()
        sobreDireccion = null
        _uiState.value = CartUiState(autenticado = usuarioId.isNotBlank())
    }

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

    fun updateCodigo(valor: String) = _uiState.update {
        // Se quita lo aplicado al tocar el campo: con un código puesto y otro a medio
        // escribir, el total de arriba no se sabría cuál de los dos es.
        it.copy(
            codigo = valor.uppercase().take(24),
            codigoError = null,
            codigoAplicado = "",
            codigoTipo = "",
            codigoPct = 0,
            codigoRegalo = "",
            codigoEsperandoMinimo = false,
            codigoFaltan = 0.0,
            codigoMinimo = 0.0,
        )
    }

    /**
     * Comprueba el código contra la Edge Function.
     *
     * Lo que vuelve es **para enseñar**: el descuento lo aplica `crear-pago`, que lo
     * valida otra vez por su cuenta. Si esta respuesta se manipulara, el cobro saldría
     * igual de bien.
     */
    fun aplicarCodigo() {
        val estado = _uiState.value
        val codigo = estado.codigo.trim()
        if (codigo.isBlank() || estado.validandoCodigo) return
        comprobar(codigo, estado.cart.items)
    }

    private fun comprobar(codigo: String, items: List<CartItem>) {
        _uiState.update { it.copy(validandoCodigo = true, codigoError = null) }

        scope.launch {
            when (val resultado = codigos.validar(codigo, items)) {
                is CodigoResult.Vale -> _uiState.update {
                    it.copy(
                        validandoCodigo = false,
                        codigo = resultado.codigo,
                        codigoAplicado = resultado.codigo,
                        codigoTipo = resultado.tipo,
                        codigoPct = resultado.pct,
                        codigoRegalo = if (resultado.tipo == "regalo") resultado.detalle else "",
                        codigoEsperandoMinimo = resultado.esperandoMinimo,
                        codigoFaltan = resultado.faltan,
                        codigoMinimo = resultado.minimo,
                        codigoError = null,
                    )
                }
                is CodigoResult.NoVale -> _uiState.update {
                    it.copy(
                        validandoCodigo = false,
                        codigoAplicado = "",
                        codigoTipo = "",
                        codigoPct = 0,
                        codigoRegalo = "",
                        codigoEsperandoMinimo = false,
                        codigoFaltan = 0.0,
                        codigoMinimo = 0.0,
                        codigoError = resultado.motivo,
                    )
                }
                CodigoResult.SinSesion -> _uiState.update {
                    it.copy(validandoCodigo = false, pideSesion = true)
                }
            }
        }
    }

    fun quitarCodigo() = _uiState.update {
        it.copy(
            codigo = "",
            codigoAplicado = "",
            codigoTipo = "",
            codigoPct = 0,
            codigoRegalo = "",
            codigoEsperandoMinimo = false,
            codigoFaltan = 0.0,
            codigoMinimo = 0.0,
            codigoError = null,
        )
    }

    /** Se vuelve del acuse al carrito, o se descarta el aviso de sesión. */
    fun reset() = _uiState.update {
        it.copy(
            folioEnviado = null,
            idEnviada = null,
            pideSesion = false,
            errorMessage = null,
            errorPago = null,
        )
    }

    /**
     * Abre el cobro de una solicitud en Mercado Pago.
     *
     * No lleva monto: lo calcula la Edge Function leyendo los precios de la base. Desde
     * aquí solo se dice cuál solicitud.
     */
    fun pagar(solicitudId: String) {
        if (solicitudId.isBlank() || _uiState.value.pagando != null) return
        scope.launch { cobrar(solicitudId, folioSiFalla = null) }
    }

    /**
     * Abre el cobro. Lo usan el botón del historial y el carrito encadenado.
     *
     * [folioSiFalla] solo llega desde el carrito: es el folio de la solicitud que
     * acabamos de crear, para poder enseñar el acuse si el cobro no abre. Desde el
     * historial no hace falta, porque de ahí no nos movemos.
     */
    private suspend fun cobrar(solicitudId: String, folioSiFalla: Long?) {
        _uiState.update { it.copy(pagando = solicitudId, errorPago = null) }

        when (val resultado = payments.abrirCobro(solicitudId)) {
            is PagoResult.Listo -> _uiState.update {
                // `pagando` se queda puesto: entre esto y que el navegador se vaya
                // pasa un instante, y apagarlo antes haría parpadear el botón.
                it.copy(urlPago = resultado.checkoutUrl)
            }
            is PagoResult.Fallo -> _uiState.update {
                it.copy(
                    pagando = null,
                    errorPago = resultado.mensaje,
                    folioEnviado = it.folioEnviado ?: folioSiFalla,
                )
            }
            PagoResult.SinSesion -> _uiState.update {
                it.copy(
                    pagando = null,
                    pideSesion = true,
                    folioEnviado = it.folioEnviado ?: folioSiFalla,
                )
            }
        }
    }

    /** La pantalla ya llevó el navegador a Mercado Pago. */
    fun pagoAbierto() = _uiState.update { it.copy(urlPago = null, pagando = null) }

    /**
     * Se vuelve de Mercado Pago.
     *
     * Lo único que se hace es volver a leer el historial: el estado de verdad lo escribe
     * el webhook, no el regreso del navegador. Puede que el aviso todavía no haya llegado
     * —tarda segundos— y entonces se siga viendo «en proceso», que es la verdad.
     */
    fun volviDelPago() {
        _uiState.update { it.copy(solicitudes = null, errorPago = null) }
        loadSolicitudes()
    }

    /** El sobre de la dirección, puesto desde fuera; se copia tal cual a la solicitud. */
    private var sobreDireccion: SobreDireccion? = null

    fun setDireccion(sobre: SobreDireccion?) {
        // Cambió la dirección: lo cotizado era para otro destino. Mantenerlo enseñaría
        // el precio de llegar a un sitio al que ya no se va, y es un precio que la
        // persona acaba pagando.
        val cambio = sobre?.contenido != sobreDireccion?.contenido
        sobreDireccion = sobre

        _uiState.update { estado ->
            val base = if (cambio) olvidarCotizacion(estado) else estado
            base.copy(hayDireccion = sobre != null)
        }
    }

    /**
     * Crea la solicitud y, si [conPago], abre el cobro a continuación.
     *
     * Encadenado a propósito: el carrito termina en «Pagar», no en «Enviar solicitud y
     * ahora busca dónde pagarla». La solicitud se crea igual aunque el cobro no llegue
     * a abrirse o la persona lo cancele en Mercado Pago — queda registrada y pendiente,
     * y se paga después desde el historial.
     */
    fun submit(conPago: Boolean = false) {
        val estado = _uiState.value
        val puede = if (conPago) estado.puedePagar else estado.puedeSeguirPorWhatsApp
        if (!puede) return
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
                // Con envío gratis no viaja paquetería. Puede quedar una elegida de
                // antes de poner el código, y mandarla diría que el cliente escogió
                // esa: `generar-guia` la trataría como su decisión y no dejaría
                // cambiarla, cuando aquí la elección es de la tienda, que es quien
                // paga. La columna `envio_gratis` la escribe `crear-pago` tras validar
                // el código por su cuenta; desde el navegador no se escribe.
                envio = estado.envioElegido.takeIf {
                    estado.entrega == MetodoEntrega.Domicilio && !estado.envioGratis
                },
                // Solo el código: el descuento lo calcula y lo aplica `crear-pago`.
                //
                // Un código en gris —puesto pero sin llegar a su mínimo— no viaja. No
                // está aplicado, el total que se enseña no lo cuenta, y atarlo a esta
                // solicitud se lo quitaría de en medio: el índice único de
                // `codigo_descuento` no deja usarlo en otro carrito mientras siga ahí.
                codigo = estado.codigoAplicado
                    .takeIf { !estado.codigoEsperandoMinimo }
                    ?.ifBlank { null },
            )
            when (resultado) {
                is EnvioResult.Enviada -> {
                    // Antes de vaciar: el acuse ofrece pagar y necesita el importe.
                    val total = estado.totalConEnvio
                    // El carrito se vacía solo cuando la base confirmó: si fallara, lo
                    // que la persona había armado seguiría ahí.
                    CartStorage.clear()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            cart = Cart(),
                            notas = "",
                            // El código se va con la solicitud: queda atado a ella y
                            // no debe arrastrarse al siguiente carrito.
                            codigo = "",
                            codigoAplicado = "",
                            codigoTipo = "",
                            codigoPct = 0,
                            codigoRegalo = "",
                            codigoEsperandoMinimo = false,
                            codigoFaltan = 0.0,
                            codigoMinimo = 0.0,
                            codigoError = null,
                            // El acuse solo cuando nos quedamos. Si vamos a Mercado
                            // Pago, enseñar «solicitud enviada» un instante antes de
                            // que la página se vaya es un parpadeo que no informa de
                            // nada.
                            folioEnviado = if (conPago) null else resultado.folio,
                            idEnviada = resultado.id,
                            totalEnviado = total,
                            solicitudes = null,
                        )
                    }

                    // Si el cobro no se puede abrir, el acuse aparece entonces: la
                    // solicitud existe, y dejar el carrito vacío con un error suelto
                    // haría pensar que se perdió.
                    if (conPago) cobrar(resultado.id, folioSiFalla = resultado.folio)
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

    /**
     * Pide cancelar una solicitud.
     *
     * No decide nada: manda el id y enseña lo que conteste la base, que es quien mira
     * el pago y la guía en la misma sentencia que escribe. Si la pantalla creía que
     * estaba sin pagar y entretanto llegó el aviso del cobro, lo que vale es lo que la
     * base vea al escribir, no lo que esta tarjeta tuviera dibujado.
     */
    fun cancelar(solicitudId: String, motivo: String = "") {
        if (solicitudId.isBlank() || _uiState.value.cancelando != null) return
        _uiState.update { it.copy(cancelando = solicitudId, avisoCancelacion = null) }

        scope.launch {
            when (val resultado = repository.cancelar(solicitudId, motivo)) {
                is CancelacionResult.Hecho -> {
                    _uiState.update {
                        it.copy(
                            cancelando = null,
                            avisoCancelacion = AvisoCancelacion(
                                solicitudId = solicitudId,
                                estado = resultado.resultado,
                                mensaje = resultado.mensaje,
                            ),
                        )
                    }
                    // La tarjeta tiene que reflejar lo que acaba de pasar. Se relee en
                    // vez de retocarla aquí: lo que vale es lo que quedó escrito, y
                    // adivinarlo en el cliente es cómo se separan las dos versiones.
                    if (resultado.resultado == CancelacionEstado.Cancelada ||
                        resultado.resultado == CancelacionEstado.Solicitada ||
                        resultado.resultado == CancelacionEstado.YaCancelada
                    ) {
                        recargarSolicitudes()
                    }
                }
                is CancelacionResult.Fallo -> _uiState.update {
                    it.copy(
                        cancelando = null,
                        avisoCancelacion = AvisoCancelacion(
                            solicitudId = solicitudId,
                            estado = CancelacionEstado.Desconocido,
                            mensaje = resultado.mensaje,
                            esError = true,
                        ),
                    )
                }
                CancelacionResult.SinSesion -> _uiState.update {
                    it.copy(cancelando = null, pideSesion = true)
                }
            }
        }
    }

    /** El diálogo se cerró: el aviso ya se leyó y no tiene que reaparecer. */
    fun descartarAvisoCancelacion() = _uiState.update { it.copy(avisoCancelacion = null) }

    /** Vuelve a leer el historial aunque ya se esté leyendo otra cosa. */
    private suspend fun recargarSolicitudes() {
        val lista = repository.misSolicitudes()
        _uiState.update { it.copy(cargandoSolicitudes = false, solicitudes = lista) }
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
        // Y el mínimo de un código puede haber dejado de cumplirse —o haberse
        // cumplido—. Se vuelve a preguntar porque quien decide es el servidor: aquí no
        // se sabe cuánto pedía ese código.
        revalidarCodigo()
    }

    /**
     * Vuelve a comprobar el código aplicado tras cambiar el carrito.
     *
     * Es lo que hace que un código de envío gratis se encienda solo al pasar del
     * mínimo y se apague al bajar, sin que haya que volver a escribirlo.
     *
     * Solo molesta al servidor cuando puede cambiar la respuesta: con un código puesto
     * y con mínimo. Uno sin mínimo contesta lo mismo con un artículo que con diez, y
     * preguntarlo en cada clic del «+» serían diez llamadas para nada.
     */
    private fun revalidarCodigo() {
        val estado = _uiState.value
        if (estado.codigoAplicado.isBlank() || estado.validandoCodigo) return
        if (estado.codigoMinimo <= 0.0) return
        if (estado.cart.items.isEmpty()) return
        comprobar(estado.codigoAplicado, estado.cart.items)
    }
}
