package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import models.Acuse
import models.EstadoPago
import models.MetodoEntrega
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
) {
    Encabezado(acuse, state.esperandoPago)

    Spacer(Modifier.height(PassionTheme.spacing.s5))
    Tarjeta {
        FilaDato("Pedido", "#${acuse.folio}")
        FilaDato("Fecha", acuse.fecha)
        FilaDato("Artículos", "${acuse.unidades}")
    }

    Spacer(Modifier.height(PassionTheme.spacing.s4))
    Importes(acuse, state.detalleAbierto, onAlternarDetalle)

    Spacer(Modifier.height(PassionTheme.spacing.s4))
    Pago(acuse, state.esperandoPago)

    Spacer(Modifier.height(PassionTheme.spacing.s4))
    Entrega(acuse, state)

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

    Spacer(Modifier.height(PassionTheme.spacing.s5))
    Contacto(acuse)
    Spacer(Modifier.height(PassionTheme.spacing.s6))
}

/**
 * El titular, que cambia con el estado del pago.
 *
 * Mientras se confirma no se felicita a nadie: decir «pago recibido» y desdecirse un
 * segundo después es peor que esperar.
 */
@Composable
private fun Encabezado(acuse: Acuse, esperando: Boolean) {
    val (titulo, texto) = when {
        esperando -> "Estamos confirmando tu pago" to
            "Tarda unos segundos. No cierres esta página; se actualiza sola."
        acuse.pago == EstadoPago.Aprobado -> "¡Pago recibido!" to
            "Tu pedido queda registrado. Te escribimos por WhatsApp para confirmarte " +
            "la entrega."
        acuse.pago == EstadoPago.Pendiente -> "Tu pedido está apartado" to
            "El pago todavía no se ha confirmado. Si elegiste efectivo, se confirma " +
            "cuando la tienda reporte el pago; suele tardar unos minutos."
        acuse.pago == EstadoPago.Rechazado -> "El pago no se completó" to
            "No se te cobró nada. Puedes volver a intentarlo desde tu historial, con " +
            "otra tarjeta u otro método."
        acuse.pago == EstadoPago.Cancelado -> "El pago se canceló" to
            "No se te cobró nada. Tu solicitud sigue guardada y puedes pagarla desde " +
            "tu historial."
        acuse.pago == EstadoPago.Reembolsado -> "Pago reembolsado" to
            "El importe se devolvió a tu método de pago."
        // Volvió sin pagar: dio «volver a la tienda» o cerró la página de Mercado Pago.
        else -> "Tu solicitud está registrada" to
            "Todavía no hay ningún pago. Puedes pagarla cuando quieras desde tu " +
            "historial, o acordarlo con nosotros por WhatsApp."
    }

    Text(
        text = titulo,
        color = PassionTheme.semantics.onBackgroundStrong,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = PassionTheme.spacing.s2),
    )
    Paragraph(texto)
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
private fun Pago(acuse: Acuse, esperando: Boolean) {
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
            FilaDato("Método", metodoDePago(acuse.pagoMetodo))
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
            add(
                "Preparamos tu paquete" to
                    "Lo empacamos sin nada que delate el contenido: la guía declara " +
                    "«Artículos de higiene personal»."
            )
            if (acuse.entrega == MetodoEntrega.Domicilio) {
                add(
                    "Lo enviamos" to
                        "Tu número de guía aparecerá en esta misma página, en «Mi " +
                        "cuenta → Historial de solicitudes», para que lo rastrees."
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

/**
 * El medio de pago con palabras, no con la clave de Mercado Pago.
 *
 * Lo que no esté en la lista se enseña tal cual antes que esconderlo: para quien revisa
 * su pedido, un `pec` raro dice más que un hueco.
 */
private fun metodoDePago(clave: String): String = when (clave.lowercase()) {
    "credit_card", "visa", "master", "amex" -> "Tarjeta de crédito"
    "debit_card", "debvisa", "debmaster" -> "Tarjeta de débito"
    "prepaid_card" -> "Tarjeta prepagada"
    "account_money" -> "Saldo en Mercado Pago"
    "ticket", "oxxo", "paycash" -> "Efectivo"
    "bank_transfer", "spei" -> "Transferencia"
    else -> clave
}
