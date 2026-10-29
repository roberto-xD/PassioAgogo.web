package ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import models.CartItem
import models.MetodoEntrega
import models.OpcionEnvio
import models.formatPrice
import network.Direccion
import ui.Chip
import ui.components.BottomSheet
import ui.components.ContentScreen
import ui.components.DataCard
import ui.components.DataLine
import ui.components.FormField
import ui.components.Paragraph
import ui.components.SectionTitle
import ui.theme.PassionTheme
import viewmodel.AddressUiState
import viewmodel.CartUiState

/** Lado de la miniatura de cada renglón. */
private val MINIATURA = 64.dp

/**
 * Lo que «Día siguiente» significa de verdad.
 *
 * Va en una hoja aparte y no suelto bajo la lista: es largo, y puesto ahí nadie leería
 * ni el principio. Pero tiene que estar junto a los plazos, porque es justo donde
 * alguien acaba de leer «Día siguiente» y se hace una idea que conviene corregir antes
 * de que pague.
 */
private const val AVISO_TIEMPOS =
    "Después de enviar la solicitud y una vez comprobado el pago, el paquete se " +
        "recolecta / entrega con paquetería hasta el siguiente día hábil, implicando " +
        "que los pedidos confirmados los días viernes, sábado o domingo se procesan a " +
        "partir del lunes (o martes en caso de ser feriado) esto debido a que las " +
        "mismas paqueterías no procesan envíos en fines de semana. Por lo tanto los " +
        "envíos marcados como «Al día siguiente» pueden tardar más de un día en ser " +
        "entregados. Una vez el paquete es procesado por la paquetería los tiempos de " +
        "entrega dependen exclusivamente de ellos, sin embargo siempre estamos " +
        "pendientes en caso de presentarse cualquier inconveniente y dispuestos a " +
        "apoyar en tanto nos sea posible."

/**
 * Carrito y envío de la solicitud de compra.
 *
 * Lo que se envía es una **solicitud**, no una venta: no mueve inventario y los precios
 * que lleva son los que se vieron aquí. Administración la revisa, recotiza con la
 * promoción vigente de ese día y la convierte en pedido. Decirlo en la pantalla evita
 * que alguien se crea que ya compró.
 */
@Composable
fun CartScreen(
    state: CartUiState,
    onSetCantidad: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onNombreChange: (String) -> Unit,
    onTelefonoChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onNotasChange: (String) -> Unit,
    onEntregaChange: (MetodoEntrega) -> Unit,
    onConfirmadoChange: (Boolean) -> Unit,
    onCotizarEnvio: (String) -> Unit = {},
    onElegirEnvio: (OpcionEnvio) -> Unit = {},
    onSubmit: () -> Unit,
    onReset: () -> Unit,
    onGoToLogin: () -> Unit,
    onGoToCatalog: () -> Unit,
    onGoToRequests: () -> Unit,
    direccion: AddressUiState = AddressUiState(),
    onEditarDireccion: ((Direccion) -> Direccion) -> Unit = {},
    onGuardarDireccion: () -> Unit = {},
) {
    ContentScreen(title = "Mi solicitud") {
        // Acuse: ocupa la pantalla entera porque es el final del camino, y mezclarlo con
        // un carrito ya vacío haría dudar de si se envió.
        state.folioEnviado?.let { folio ->
            Acuse(
                folio = folio,
                onGoToRequests = onGoToRequests,
                onGoToCatalog = { onReset(); onGoToCatalog() },
            )
            return@ContentScreen
        }

        if (state.cart.items.isEmpty()) {
            Paragraph("Todavía no has agregado nada.")
            Spacer(Modifier.height(PassionTheme.spacing.s3))
            Button(onClick = onGoToCatalog, shape = MaterialTheme.shapes.extraLarge) {
                Text("Ver el catálogo", style = MaterialTheme.typography.labelLarge)
            }
            return@ContentScreen
        }

        Paragraph(
            "Esto es una solicitud, no una compra cerrada. La revisamos, confirmamos " +
                "disponibilidad y precio del día, y te contactamos para cerrar el pedido."
        )

        state.cart.items.forEach { item ->
            Renglon(
                item = item,
                onSetCantidad = { onSetCantidad(item.variantId, it) },
                onRemove = { onRemove(item.variantId) },
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = PassionTheme.spacing.s4),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Total estimado",
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatPrice(state.cart.total),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "No incluye envío. El precio final se confirma al atender la solicitud.",
            color = PassionTheme.semantics.onBackgroundSubtle,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = PassionTheme.spacing.s1),
        )

        SectionTitle("¿A nombre de quién?")
        DatosCliente(
            state = state,
            onNombreChange = onNombreChange,
            onTelefonoChange = onTelefonoChange,
            onEmailChange = onEmailChange,
        )

        SectionTitle("¿Cómo lo quieres recibir?")
        Row(
            modifier = Modifier.padding(top = PassionTheme.spacing.s1),
            horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
        ) {
            MetodoEntrega.entries.forEach { metodo ->
                Chip(
                    label = metodo.etiqueta,
                    selected = state.entrega == metodo,
                    onClick = { onEntregaChange(metodo) },
                )
            }
        }

        Spacer(Modifier.height(PassionTheme.spacing.s3))
        when (state.entrega) {
            MetodoEntrega.Domicilio -> {
                EntregaDomicilio(
                    autenticado = state.autenticado,
                    direccion = direccion,
                    onEditarDireccion = onEditarDireccion,
                    onGuardarDireccion = onGuardarDireccion,
                )
                // Debajo de la tarjeta de la dirección, porque es de su código postal de
                // donde sale el precio: puesto antes no tendría a dónde cotizar.
                OpcionesDeEnvio(
                    state = state,
                    cp = direccion.direccion.cp,
                    onCotizar = onCotizarEnvio,
                    onElegir = onElegirEnvio,
                )
            }
            MetodoEntrega.Personal -> DataCard(
                titulo = "Entrega en persona",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Las entregas personales se realizan únicamente en CDMX y " +
                        "solo en las áreas de cobertura disponibles, en los días y " +
                        "horarios acordados previamente vía WhatsApp.",
                    color = PassionTheme.semantics.onBackgroundMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = PassionTheme.spacing.s2),
                )
            }
        }

        FormField(
            value = state.notas,
            onValueChange = onNotasChange,
            label = "Notas",
            singleLine = false,
            supportingText = "Tallas, colores, horarios de entrega… lo que quieras que sepamos.",
        )

        if (state.pideSesion) {
            Aviso(
                texto = "Para enviar la solicitud necesitas una cuenta. Tu carrito se " +
                    "queda guardado mientras accedes.",
                esError = false,
            )
            Button(
                onClick = { onReset(); onGoToLogin() },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.padding(top = PassionTheme.spacing.s3),
            ) {
                Text("Acceder o registrarme", style = MaterialTheme.typography.labelLarge)
            }
        }

        state.errorMessage?.let { Aviso(texto = it, esError = true) }

        Spacer(Modifier.height(PassionTheme.spacing.s4))
        Confirmacion(marcado = state.confirmado, onMarcar = onConfirmadoChange)

        Spacer(Modifier.height(PassionTheme.spacing.s4))
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (state.isSubmitting) "Enviando…" else "Enviar solicitud",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        // Un botón apagado sin explicación se lee como una app rota. Se dice lo que
        // falta, y solo eso: en cuanto se cumple, el texto desaparece.
        queFalta(state)?.let {
            Text(
                text = it,
                color = PassionTheme.semantics.onBackgroundSubtle,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            )
        }
    }
}

/** Lo que impide enviar, en el orden en que la persona se lo encuentra al bajar. */
private fun queFalta(state: CartUiState): String? = when {
    state.isSubmitting -> null
    state.nombre.trim().isEmpty() -> "Falta el nombre."
    !state.telefonoCompleto -> "Falta tu teléfono de contacto."
    state.faltaDireccion -> "Falta guardar la dirección de envío."
    !state.confirmado -> "Marca la casilla para continuar."
    else -> null
}

/**
 * Los datos de contacto, en texto.
 *
 * Se enseñan y no se editan porque ya están en la cuenta y este no es el sitio para
 * cambiarlos. La excepción es el teléfono: sin él no hay por dónde seguir el pedido, así
 * que si falta se pide aquí mismo en lugar de mandar a la persona a «Mi cuenta» y de
 * vuelta.
 */
@Composable
private fun DatosCliente(
    state: CartUiState,
    onNombreChange: (String) -> Unit,
    onTelefonoChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
) {
    DataCard(titulo = null, modifier = Modifier.fillMaxWidth()) {
        if (!state.nombreEnPerfil) {
            FormField(
                value = state.nombre,
                onValueChange = onNombreChange,
                label = "Nombre *",
            )
        } else {
            DataLine("Nombre", state.nombre)
        }

        if (!state.telefonoEnPerfil) {
            FormField(
                value = state.telefono,
                // Solo dígitos: con espacios y guiones de por medio, «¿son diez?» deja
                // de ser una pregunta que la pantalla pueda responder.
                onValueChange = { v -> onTelefonoChange(v.filter { it.isDigit() }.take(10)) },
                label = "Teléfono *",
                keyboardType = KeyboardType.Phone,
                supportingText = "10 dígitos. Por aquí seguimos tu pedido.",
            )
        } else {
            DataLine("Teléfono", state.telefono)
        }

        if (!state.emailEnPerfil) {
            FormField(
                value = state.email,
                onValueChange = onEmailChange,
                label = "Correo",
                keyboardType = KeyboardType.Email,
            )
        } else {
            DataLine("Correo", state.email)
        }
    }
}

/**
 * La tarjeta de «Se enviará a:».
 *
 * Con dirección guardada se enseña en texto; sin ella se captura aquí mismo, reutilizando
 * el mismo bloque de «Mi cuenta» para que se cifre igual. Mandar a la persona a otra
 * pantalla a mitad de un pedido es la mejor forma de que no vuelva.
 */
@Composable
private fun EntregaDomicilio(
    autenticado: Boolean,
    direccion: AddressUiState,
    onEditarDireccion: ((Direccion) -> Direccion) -> Unit,
    onGuardarDireccion: () -> Unit,
) {
    var editando by remember { mutableStateOf(false) }
    val guardada = direccion.hayGuardada && !direccion.bloqueada

    DataCard(titulo = "Se enviará a:", modifier = Modifier.fillMaxWidth()) {
        when {
            // Sin sesión no hay dónde guardarla: enseñar el formulario aquí sería dar un
            // botón que no puede funcionar.
            !autenticado -> Text(
                text = "Te pedimos la dirección al acceder. Se guarda cifrada en tu " +
                    "navegador: solo tú y la tienda pueden leerla.",
                color = PassionTheme.semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            )

            direccion.cargando -> Paragraph("Cargando…")

            guardada && !editando -> {
                DireccionTexto(direccion.direccion)
                Text(
                    text = "Cambiar",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(top = PassionTheme.spacing.s2)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable { editando = true }
                        .padding(vertical = PassionTheme.spacing.s1),
                )
            }

            else -> DireccionSection(
                state = direccion,
                onEditar = onEditarDireccion,
                onGuardar = onGuardarDireccion,
                onBorrar = {},
                titulo = null,
                conBorrar = false,
            )
        }
    }

    // Guardar desde aquí cierra el formulario y deja la dirección a la vista, que es la
    // señal de que quedó registrada.
    LaunchedEffect(direccion.sobre) {
        if (direccion.hayGuardada && !direccion.bloqueada) editando = false
    }
}

/**
 * El bloque de cotización: botón, lista de opciones y el aviso de plazos.
 *
 * Se cotiza al pulsar y no al abrir el carrito. Cada cotización son varias llamadas a
 * paqueterías, y quien solo entra a ver qué lleva en el carrito no tiene por qué
 * esperarlas.
 */
@Composable
private fun OpcionesDeEnvio(
    state: CartUiState,
    cp: String,
    onCotizar: (String) -> Unit,
    onElegir: (OpcionEnvio) -> Unit,
) {
    // Sin dirección guardada no hay código postal al que cotizar, y el botón solo
    // serviría para enseñar un error que ya anuncia la tarjeta de arriba.
    if (!state.autenticado || cp.length != 5) return

    var verAviso by remember { mutableStateOf(false) }
    val opciones = state.opcionesEnvio

    Spacer(Modifier.height(PassionTheme.spacing.s3))

    if (opciones == null) {
        Button(
            onClick = { onCotizar(cp) },
            enabled = !state.cotizando,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(
                text = if (state.cotizando) "Calculando…" else "Ver opciones de envío",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        state.errorEnvio?.let { Aviso(texto = it, esError = true) }
        return
    }

    Text(
        text = "Opciones de envío",
        color = PassionTheme.semantics.onBackgroundStrong,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = PassionTheme.spacing.s2),
    )

    opciones.forEach { opcion ->
        FilaEnvio(
            opcion = opcion,
            elegida = opcion.clave == state.envioElegido?.clave,
            onClick = { onElegir(opcion) },
        )
    }

    if (state.envioRecortado) {
        Text(
            text = "Tu pedido es grande: el costo definitivo puede subir un poco y te " +
                "lo confirmamos antes de cobrarlo.",
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        )
    }

    // El aviso de plazos va al final de la lista, que es donde alguien acaba de ver un
    // «Día siguiente» y conviene explicarle qué significa de verdad.
    Text(
        text = "Sobre los tiempos de entrega",
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .padding(top = PassionTheme.spacing.s3)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable { verAviso = true }
            .padding(vertical = PassionTheme.spacing.s1),
    )

    if (verAviso) {
        BottomSheet(title = "Sobre los tiempos de entrega", onDismiss = { verAviso = false }) {
            Paragraph(AVISO_TIEMPOS)
        }
    }
}

/** Una opción de envío, elegible. */
@Composable
private fun FilaEnvio(opcion: OpcionEnvio, elegida: Boolean, onClick: () -> Unit) {
    val forma = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s2)
            .clip(forma)
            .border(
                width = if (elegida) 2.dp else 1.dp,
                color = if (elegida) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = forma,
            )
            .clickable(onClick = onClick)
            .padding(PassionTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = opcion.titulo,
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (elegida) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (opcion.entrega.isNotBlank()) {
                Text(
                    text = opcion.entrega,
                    color = PassionTheme.semantics.onBackgroundMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            text = formatPrice(opcion.precio),
            color = if (elegida) MaterialTheme.colorScheme.primary
            else PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** La casilla que habilita el envío. */
@Composable
private fun Confirmacion(marcado: Boolean, onMarcar: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            // Toda la fila es pulsable, no solo el cuadrito: en un teléfono acertarle a
            // la casilla sola es una lotería.
            .clickable { onMarcar(!marcado) }
            .padding(vertical = PassionTheme.spacing.s1),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = marcado, onCheckedChange = onMarcar)
        Text(
            text = "Al dar click en «Enviar solicitud» el proceso de compra continúa vía " +
                "mensaje directo en WhatsApp, donde se le dará seguimiento al método de " +
                "envío / entrega seleccionado, y podrás realizar el pago de manera " +
                "electrónica.",
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = PassionTheme.spacing.s2,
                    top = PassionTheme.spacing.s3,
                ),
        )
    }
}

@Composable
private fun Renglon(item: CartItem, onSetCantidad: (Int) -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(MINIATURA)
                .clip(MaterialTheme.shapes.small)
                .background(PassionTheme.semantics.imagePlaceholder),
        ) {
            if (item.imagen.isNotBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(item.imagen),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = PassionTheme.spacing.s3),
        ) {
            Text(
                text = item.nombre,
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatPrice(item.precio)} c/u",
                color = PassionTheme.semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Quitar",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .padding(top = PassionTheme.spacing.s1)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = onRemove)
                    .padding(vertical = PassionTheme.spacing.s1),
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Contador(cantidad = item.cantidad, onSetCantidad = onSetCantidad)
            Text(
                text = formatPrice(item.subtotal),
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = PassionTheme.spacing.s1),
            )
        }
    }
}

/** Menos / cantidad / más. A uno, el menos quita el renglón. */
@Composable
private fun Contador(cantidad: Int, onSetCantidad: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Paso("−") { onSetCantidad(cantidad - 1) }
        Text(
            text = cantidad.toString(),
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = PassionTheme.spacing.s3),
        )
        Paso("+") { onSetCantidad(cantidad + 1) }
    }
}

@Composable
private fun Paso(signo: String, onClick: () -> Unit) {
    val forma = MaterialTheme.shapes.extraLarge
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(forma)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, forma)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = signo,  // U+2212 para el menos: el guion normal se ve corto al lado del +
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun Acuse(folio: Long, onGoToRequests: () -> Unit, onGoToCatalog: () -> Unit) {
    val forma = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(PassionTheme.semantics.overlayWeak)
            .padding(PassionTheme.spacing.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "¡Solicitud enviada!",
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Folio #$folio",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        )
        Text(
            text = "La revisamos y te contactamos para confirmar disponibilidad, precio " +
                "del día y entrega.",
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s3),
        )

        Button(
            onClick = onGoToRequests,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.padding(top = PassionTheme.spacing.s5),
        ) {
            Text("Ver mis solicitudes", style = MaterialTheme.typography.labelLarge)
        }
        Button(
            onClick = onGoToCatalog,
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        ) {
            Text("Seguir viendo", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Aviso(texto: String, esError: Boolean) {
    Text(
        text = texto,
        color = if (esError) MaterialTheme.colorScheme.error
        else PassionTheme.semantics.onBackgroundMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s4),
    )
}
