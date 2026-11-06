package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import models.Acuse
import models.EstadoPago
import models.MetodoEntrega
import models.Recomendado
import models.esPagoConTarjeta
import models.etiquetaDePago
import models.formatPrice
import network.WhatsAppConfig
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.copyToClipboard
import ui.components.openInNewTab
import ui.theme.PassionTheme
import viewmodel.ThankYouUiState

/**
 * El acuse al volver de pagar.
 *
 * Dos cosas la separan de un «gracias por tu compra» cualquiera.
 *
 * La primera es que **al cargar no sabemos si el pago entró**. La URL de regreso dice
 * `status=approved` y no significa nada: la escribe quien quiera. Lo que vale lo escribe
 * el webhook, que llega por su cuenta. Así que hay un estado intermedio honesto —
 * «estamos confirmando» — en vez de felicitar por un cobro que quizá se rechazó.
 *
 * La segunda es la discreción. El detalle de lo comprado empieza **plegado**: esta
 * pantalla se abre sola al volver de Mercado Pago, en el navegador de alguien que puede
 * no estar solo. Es el mismo criterio por el que la guía declara «Artículos de higiene
 * personal» y el cargo sale como `PAG`.
 */
@Composable
fun ThankYouScreen(
    state: ThankYouUiState,
    onAlternarDetalle: () -> Unit,
    onGoToCatalog: () -> Unit,
    onGoToRequests: () -> Unit,
    onGoToLogin: () -> Unit = {},
    onAbrirProducto: (Recomendado) -> Unit = {},
) {
    ContentScreen(title = "Gracias por tu compra") {
        when {
            state.cargando -> Paragraph("Cargando tu pedido…")
            state.error != null -> {
                Paragraph(state.error)
                Spacer(Modifier.height(PassionTheme.spacing.s4))
                if (state.sinSesion) {
                    Button(onClick = onGoToLogin, shape = MaterialTheme.shapes.extraLarge) {
                        Text("Entrar", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(onClick = onGoToRequests, shape = MaterialTheme.shapes.extraLarge) {
                        Text(
                            text = "Ver mis solicitudes",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            state.acuse != null -> Contenido(
                state = state,
                acuse = state.acuse,
                onAlternarDetalle = onAlternarDetalle,
                onGoToCatalog = onGoToCatalog,
                onGoToRequests = onGoToRequests,
                onAbrirProducto = onAbrirProducto,
            )
        }
    }
}

@Composable
private fun Contenido(
    state: ThankYouUiState,
    acuse: Acuse,
    onAlternarDetalle: () -> Unit,
    onGoToCatalog: () -> Unit,
    onGoToRequests: () -> Unit,
    onAbrirProducto: (Recomendado) -> Unit,
) {
    Encabezado(acuse, state.esperandoPago)

    Spacer(Modifier.height(PassionTheme.spacing.s4))
    Importes(acuse, state.detalleAbierto, onAlternarDetalle)

    Spacer(Modifier.height(PassionTheme.spacing.s4))
    Pago(acuse, state.esperandoPago, state.statement)

    Spacer(Modifier.height(PassionTheme.spacing.s4))
    Entrega(acuse, state)

    // El empaque discreto va justo después de la dirección, que es donde la pregunta
    // aparece sola: acabas de ver a dónde llega el paquete y lo siguiente que te
    // preguntas es qué va a ver quien te lo entregue.
    if (state.empaque.isNotBlank()) {
        Spacer(Modifier.height(PassionTheme.spacing.s4))
        Tarjeta {
            Titulo("Empaque discreto")
            Text(
                text = state.empaque,
                color = PassionTheme.semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    Spacer(Modifier.height(PassionTheme.spacing.s4))
    QueSigue(acuse)

    Spacer(Modifier.height(PassionTheme.spacing.s5))
    Button(onClick = onGoToRequests, shape = MaterialTheme.shapes.extraLarge) {
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

    if (state.recomendados.isNotEmpty()) {
        Spacer(Modifier.height(PassionTheme.spacing.s6))
        Recomendados(state.recomendados, onAbrirProducto)
    }

    Spacer(Modifier.height(PassionTheme.spacing.s5))
    Contacto(acuse)
    Spacer(Modifier.height(PassionTheme.spacing.s6))
}

/**
 * Lo que se sugiere al final.
 *
 * Al final **a propósito**: lo primero que esa persona necesita es saber que su pago
 * entró y qué pasa ahora. Ponerle más productos delante de eso sería contarle lo que
 * nos interesa a nosotros antes que lo que le interesa a ella.
 *
 * Se eligen a mano, por SKU, desde `site_settings.acuse_recomendados`.
 */
@Composable
private fun Recomendados(
    productos: List<Recomendado>,
    onAbrir: (Recomendado) -> Unit,
) {
    Text(
        text = "Combina bien con tu compra",
        color = PassionTheme.semantics.onBackgroundStrong,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = PassionTheme.spacing.s2),
    )

    productos.forEach { producto ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PassionTheme.spacing.s2)
                .clip(MaterialTheme.shapes.medium)
                .clickable { onAbrir(producto) }
                .background(PassionTheme.semantics.overlayWeak)
                .padding(PassionTheme.spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (producto.imagen.isNotBlank()) {
                AsyncImage(
                    model = producto.imagen,
                    contentDescription = producto.nombre,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.small),
                )
                Spacer(Modifier.width(PassionTheme.spacing.s3))
            }

            Column(modifier = Modifier.weight(1f)) {
                if (producto.categoria.isNotBlank()) {
                    Text(
                        text = producto.categoria,
                        color = PassionTheme.semantics.onBackgroundSubtle,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    text = producto.nombre,
                    color = PassionTheme.semantics.onBackgroundStrong,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = formatPrice(producto.precio),
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = PassionTheme.spacing.s2),
            )
        }
    }
}

/**
 * El titular, que cambia con el estado del pago.
 *
 * Mientras se confirma no se felicita a nadie: decir «pago recibido» y desdecirse un
 * segundo después es peor que esperar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Encabezado(acuse: Acuse, esperando: Boolean) {
    // Antetítulo, titular y explicación. El antetítulo es el estado en dos palabras,
    // para quien solo mira; el titular, lo que le pasa a su pedido; el texto, qué hacer.
    val (antetitulo, resto) = when {
        esperando -> "Confirmando el pago" to (
            "Estamos confirmando tu pago" to
                "Tarda unos segundos. No cierres esta página; se actualiza sola."
            )
        acuse.pago == EstadoPago.Aprobado -> "Pedido confirmado" to (
            "Tu pedido ya está en preparación" to
                "Recibimos tu pago. Te escribimos por WhatsApp para confirmarte la entrega."
            )
        acuse.pago == EstadoPago.Pendiente -> "Pedido reservado" to (
            "Tu pedido está apartado" to
                "El pago todavía no se ha confirmado. Si elegiste efectivo, se confirma " +
                "cuando la tienda reporte el pago; suele tardar unos minutos."
            )
        acuse.pago == EstadoPago.Rechazado -> "Pago no completado" to (
            "El pago no se completó" to
                "No se te cobró nada. Puedes volver a intentarlo desde tu historial, con " +
                "otra tarjeta u otro método."
            )
        acuse.pago == EstadoPago.Cancelado -> "Pago cancelado" to (
            "El pago se canceló" to
                "No se te cobró nada. Tu solicitud sigue guardada y puedes pagarla desde " +
                "tu historial."
            )
        acuse.pago == EstadoPago.Reembolsado -> "Pago reembolsado" to (
            "Te devolvimos el importe" to
                "El dinero vuelve a tu método de pago; tu banco puede tardar unos días " +
                "en reflejarlo."
            )
        // Volvió sin pagar: dio «volver a la tienda» o cerró la página de Mercado Pago.
        else -> "Solicitud registrada" to (
            "Tu solicitud está registrada" to
                "Todavía no hay ningún pago. Puedes pagarla cuando quieras desde tu " +
                "historial, o acordarlo con nosotros por WhatsApp."
            )
    }
    val (titulo, texto) = resto

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s3),
    ) {
        Insignia(acuse, esperando)

        Text(
            text = antetitulo.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )

        // El «¡Gracias!» solo cuando hay algo que agradecer. Sobre «el pago no se
        // completó» sonaría a burla, y sobre «estamos confirmando» sería hablar antes
        // de tiempo.
        if (acuse.pago == EstadoPago.Aprobado && !esperando) {
            Text(
                text = "¡Gracias!",
                color = MaterialTheme.colorScheme.primary,
                style = PassionTheme.type.scriptAccent.copy(
                    fontSize = 64.sp,
                    lineHeight = 64.sp,
                ),
            )
        }

        Text(
            text = titulo,
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 640.dp),
        )

        Text(
            text = texto,
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )

        // Pedido, fecha y total en una línea, que es lo que se busca de un vistazo.
        // Aquí y no en una tarjeta aparte: estaban los dos y decían lo mismo.
        FlowRow(
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            horizontalArrangement = Arrangement.spacedBy(
                PassionTheme.spacing.s5,
                Alignment.CenterHorizontally,
            ),
            verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s1),
        ) {
            DatoDelTitular("Pedido ", "#${acuse.folio}")
            Text(
                text = acuse.fecha,
                color = PassionTheme.semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            DatoDelTitular("Total ", formatPrice(acuse.total))
        }
    }
}

/** «Pedido #6»: la etiqueta en tono suave y el dato en fuerte, sin dos `Text` seguidos. */
@Composable
private fun DatoDelTitular(etiqueta: String, valor: String) {
    Text(
        text = buildAnnotatedString {
            append(etiqueta)
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.SemiBold,
                    color = PassionTheme.semantics.onBackgroundStrong,
                )
            ) { append(valor) }
        },
        color = PassionTheme.semantics.onBackgroundMuted,
        style = MaterialTheme.typography.bodySmall,
    )
}

/**
 * El disco con el signo del estado.
 *
 * El signo se dibuja en vez de traerse de una librería de iconos: el proyecto no
 * depende de ninguna, y arrastrar una entera por tres trazos engordaría un wasm que ya
 * pesa ocho megas. Dibujado, además, se tiñe con el color que toca y escala sin pixelar.
 */
@Composable
private fun Insignia(acuse: Acuse, esperando: Boolean) {
    val semantics = PassionTheme.semantics
    val color = when {
        esperando -> MaterialTheme.colorScheme.primary
        acuse.pago == EstadoPago.Aprobado -> semantics.success
        acuse.pago == EstadoPago.Rechazado || acuse.pago == EstadoPago.Cancelado ->
            semantics.danger
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .size(88.dp)
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                ambientColor = color,
                spotColor = color,
            )
            .background(semantics.barSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val trazo = Stroke(
                width = size.minDimension * 0.11f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            val w = size.width
            val h = size.height

            when {
                // Tres puntos: se está esperando a que Mercado Pago conteste.
                esperando -> {
                    val r = w * 0.07f
                    listOf(0.22f, 0.5f, 0.78f).forEach { x ->
                        drawCircle(color, radius = r, center = Offset(w * x, h * 0.5f))
                    }
                }
                // Palomita.
                acuse.pago == EstadoPago.Aprobado -> drawPath(
                    path = Path().apply {
                        moveTo(w * 0.18f, h * 0.52f)
                        lineTo(w * 0.42f, h * 0.74f)
                        lineTo(w * 0.82f, h * 0.28f)
                    },
                    color = color,
                    style = trazo,
                )
                // Aspa: no se cobró.
                acuse.pago == EstadoPago.Rechazado || acuse.pago == EstadoPago.Cancelado -> {
                    drawLine(
                        color, Offset(w * 0.25f, h * 0.25f), Offset(w * 0.75f, h * 0.75f),
                        strokeWidth = trazo.width, cap = StrokeCap.Round,
                    )
                    drawLine(
                        color, Offset(w * 0.75f, h * 0.25f), Offset(w * 0.25f, h * 0.75f),
                        strokeWidth = trazo.width, cap = StrokeCap.Round,
                    )
                }
                // Flecha de vuelta: el dinero se devolvió.
                acuse.pago == EstadoPago.Reembolsado -> drawPath(
                    path = Path().apply {
                        moveTo(w * 0.42f, h * 0.22f)
                        lineTo(w * 0.18f, h * 0.46f)
                        lineTo(w * 0.42f, h * 0.70f)
                    },
                    color = color,
                    style = trazo,
                ).also {
                    drawArc(
                        color = color,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.18f, h * 0.46f),
                        size = Size(w * 0.64f, h * 0.48f),
                        style = trazo,
                    )
                }
                // Sobre cerrado: la solicitud está, el pago no.
                else -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.15f, h * 0.26f),
                        size = Size(w * 0.70f, h * 0.48f),
                        cornerRadius = CornerRadius(w * 0.06f),
                        style = trazo,
                    )
                    drawPath(
                        path = Path().apply {
                            moveTo(w * 0.15f, h * 0.30f)
                            lineTo(w * 0.50f, h * 0.55f)
                            lineTo(w * 0.85f, h * 0.30f)
                        },
                        color = color,
                        style = trazo,
                    )
                }
            }
        }
    }
}

@Composable
private fun Importes(acuse: Acuse, detalleAbierto: Boolean, onAlternar: () -> Unit) {
    Tarjeta {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Total",
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = formatPrice(acuse.total),
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        FilaDato("Artículos", formatPrice(acuse.totalArticulos))
        if (acuse.tieneEnvio) {
            FilaDato(
                etiqueta = "Envío" + acuse.envioDescripcion.takeIf { it.isNotBlank() }
                    ?.let { " · $it" }.orEmpty(),
                valor = formatPrice(acuse.envioCosto),
            )
        }

        // El detalle va plegado a propósito. Ver abajo, en el KDoc de la pantalla.
        if (acuse.articulos.isNotEmpty()) {
            Spacer(Modifier.height(PassionTheme.spacing.s2))
            Text(
                text = if (detalleAbierto) "Ocultar detalle" else "Ver detalle",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = onAlternar)
                    .padding(vertical = PassionTheme.spacing.s1),
            )

            if (detalleAbierto) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = PassionTheme.spacing.s2),
                )
                acuse.articulos.forEach { articulo ->
                    FilaDato(
                        etiqueta = "${articulo.cantidad} × ${articulo.nombre}",
                        valor = formatPrice(articulo.subtotal),
                    )
                }
            }
        }
    }
}

@Composable
private fun Pago(acuse: Acuse, esperando: Boolean, statement: String) {
    Tarjeta {
        Titulo("Pago")

        if (esperando) {
            Paragraph("Confirmando con Mercado Pago…")
            return@Tarjeta
        }

        FilaDato(
            etiqueta = "Estado",
            valor = when (acuse.pago) {
                EstadoPago.Aprobado -> "Pagado"
                EstadoPago.Pendiente -> "Pendiente"
                EstadoPago.Rechazado -> "No completado"
                EstadoPago.Cancelado -> "Cancelado"
                EstadoPago.Reembolsado -> "Reembolsado"
                EstadoPago.Ninguno -> "Sin pagar"
            },
        )
        if (acuse.pagoMetodo.isNotBlank()) {
            // Con los cuatro dígitos cuando los hay: es lo que permite reconocer *con
            // cuál* de sus tarjetas pagó, que es la duda real al revisar un cobro.
            // La marca antes que la clase: «Mastercard ···· 4821» se reconoce de un
            // vistazo; «Tarjeta de crédito ···· 4821» obliga a pensar cuál era.
            FilaDato(
                etiqueta = "Método",
                valor = etiquetaDePago(
                    marca = acuse.pagoMarca,
                    metodo = acuse.pagoMetodo,
                    ultimos4 = acuse.pagoUltimos4,
                ),
            )
        }

        // Lo que verá en su estado de cuenta. Sale de `site_settings`, el mismo sitio
        // del que lo lee la Edge Function al crear la orden: con dos copias acabarían
        // diciendo cosas distintas y le prometeríamos algo falso sobre su tarjeta.
        if (statement.isNotBlank() && esPagoConTarjeta(acuse.pagoMetodo)) {
            Spacer(Modifier.height(PassionTheme.spacing.s2))
            Text(
                text = "En tu estado de cuenta aparecerá como «$statement».",
                color = PassionTheme.semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // La referencia es su comprobante: es lo que sirve para reclamar en Mercado
        // Pago, donde nuestro folio no significa nada.
        if (acuse.pagoReferencia.isNotBlank()) {
            var copiado by remember(acuse.id) { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PassionTheme.spacing.s1),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Referencia",
                    color = PassionTheme.semantics.onBackgroundMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = if (copiado) "copiado" else acuse.pagoReferencia,
                    color = PassionTheme.semantics.onBackgroundStrong,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable { copiado = copyToClipboard(acuse.pagoReferencia) }
                        .padding(vertical = PassionTheme.spacing.s1),
                )
            }
        }
    }
}

@Composable
private fun Entrega(acuse: Acuse, state: ThankYouUiState) {
    Tarjeta {
        if (acuse.entrega == MetodoEntrega.Personal) {
            Titulo("Entrega en persona")
            Paragraph(
                "Las entregas personales se realizan únicamente en CDMX y solo en las " +
                    "áreas de cobertura disponibles, en los días y horarios acordados " +
                    "previamente vía WhatsApp."
            )
            return@Tarjeta
        }

        Titulo("Se enviará a")

        when {
            state.direccion != null -> DireccionTexto(state.direccion)
            // La clave vive en la pestaña y no sobrevive a cerrarla. No es un error ni
            // hay nada que arreglar: la dirección está guardada y la tienda la puede
            // abrir con la suya.
            state.direccionBloqueada -> Paragraph(
                "Tu dirección va cifrada y esta pestaña no puede abrirla ahora mismo. " +
                    "No te preocupes: la tenemos guardada y la usaremos para tu envío."
            )
            else -> Paragraph("Acordaremos la entrega contigo por WhatsApp.")
        }

        if (acuse.envioEntrega.isNotBlank()) {
            Spacer(Modifier.height(PassionTheme.spacing.s2))
            FilaDato("Tiempo estimado", acuse.envioEntrega)
        }
    }
}

/**
 * Los pasos siguientes, contados como ocurren de verdad.
 *
 * El trato sigue por WhatsApp y la guía aparece en el historial. No se promete ningún
 * correo: no mandamos ninguno. El único que llega es el comprobante de Mercado Pago, que
 * es suyo y no nuestro, así que tampoco se promete aquí.
 */
@Composable
private fun QueSigue(acuse: Acuse) {
    Tarjeta {
        Titulo("¿Qué sigue?")

        val pasos = buildList {
            if (acuse.pago != EstadoPago.Aprobado) {
                add(
                    "Completar el pago" to
                        "Puedes hacerlo desde tu historial cuando quieras, o acordarlo " +
                        "con nosotros por WhatsApp."
                )
            }
            add(
                "Te escribimos" to
                    "Confirmamos disponibilidad y la entrega por WhatsApp, al número " +
                    "que nos diste."
            )
            // Sin repetir aquí lo del empaque: ya tiene su propia tarjeta arriba, y
            // decirlo dos veces en la misma pantalla suena a insistir.
            add("Preparamos tu paquete" to "En cuanto confirmemos, lo dejamos listo.")
            if (acuse.entrega == MetodoEntrega.Domicilio) {
                add(
                    "Lo enviamos" to
                        // Sin flecha: la tipografía del sitio no trae «→» y se pinta
                        // como un cuadro vacío.
                        "Tu número de guía aparecerá en esta misma página, en el " +
                        "historial de solicitudes de tu cuenta, para que lo rastrees."
                )
            }
        }

        pasos.forEachIndexed { i, (titulo, texto) ->
            Text(
                text = "${i + 1}. $titulo",
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            )
            Text(
                text = texto,
                color = PassionTheme.semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Contacto(acuse: Acuse) {
    Text(
        text = "¿Dudas sobre tu pedido?",
        color = PassionTheme.semantics.onBackgroundMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    if (WhatsAppConfig.isConfigured) {
        Text(
            text = "Escríbenos por WhatsApp",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { openInNewTab(WhatsAppConfig.enlaceParaPedido(acuse.folio)) }
                .padding(vertical = PassionTheme.spacing.s1),
        )
    }
}

// ---------------------------------------------------------------------------
// Piezas sueltas
// ---------------------------------------------------------------------------

@Composable
private fun Tarjeta(contenido: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(PassionTheme.semantics.overlayWeak)
            .padding(PassionTheme.spacing.s4),
    ) {
        contenido()
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(
        text = texto,
        color = PassionTheme.semantics.onBackgroundStrong,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = PassionTheme.spacing.s1),
    )
}

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = etiqueta,
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(end = PassionTheme.spacing.s3),
        )
        Text(
            text = valor,
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}



