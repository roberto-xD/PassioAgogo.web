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
import models.EstadoPago
import models.Solicitud
import models.etiquetaDePago
import models.formatPrice
import ui.components.Paragraph
import ui.components.copyToClipboard
import ui.components.openInNewTab
import ui.theme.PassionTheme
import viewmodel.CartUiState

/**
 * Las solicitudes que ha enviado quien tiene la sesión abierta.
 *
 * El RLS ya limita la consulta a las propias, así que aquí no hace falta filtrar por
 * nada: lo que llega es lo que esa persona puede ver.
 *
 * **No es el carrito.** Aquí está lo ya enviado; lo que se está armando vive en
 * `#/carrito`. Los dos nombres se parecían demasiado y daba la impresión de que el
 * carrito se vaciaba al entrar aquí, así que esta pantalla pasó a llamarse «Historial
 * de solicitudes» y avisa cuando hay algo pendiente de enviar.
 */
@Composable
fun AccountHistoryContent(
    state: CartUiState,
    onGoToCatalog: () -> Unit,
    onGoToCart: () -> Unit = {},
    onPagar: (String) -> Unit = {},
) {
    Spacer(Modifier.height(PassionTheme.spacing.s2))
    val solicitudes = state.solicitudes
    when {
        state.cargandoSolicitudes && solicitudes == null ->
            Paragraph("Cargando tus solicitudes…")

        solicitudes == null -> Paragraph(
            "No pudimos cargar tus solicitudes. Vuelve a intentarlo más tarde."
        )

        solicitudes.isEmpty() -> {
            Paragraph(
                "Aquí aparecen las solicitudes que ya enviaste. Todavía no has " +
                        "enviado ninguna."
            )
            Spacer(Modifier.height(PassionTheme.spacing.s3))
            // Lo que la persona tiene a medio armar no está aquí sino en el
            // carrito. Sin este aviso, ver la lista vacía con artículos ya
            // elegidos parece que se perdieron.
            if (state.cart.items.isNotEmpty()) {
                Paragraph(
                    "Tienes ${state.unidades} artículo" +
                            (if (state.unidades == 1) "" else "s") +
                            " en tu solicitud sin enviar."
                )
                Spacer(Modifier.height(PassionTheme.spacing.s3))
                Button(onClick = onGoToCart, shape = MaterialTheme.shapes.extraLarge) {
                    Text("Ver mi solicitud", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(PassionTheme.spacing.s2))
            }
            Button(onClick = onGoToCatalog, shape = MaterialTheme.shapes.extraLarge) {
                Text("Ver el catálogo", style = MaterialTheme.typography.labelLarge)
            }
        }

        else -> {
            // El fallo del cobro se enseña arriba y una sola vez: es de la pantalla, no
            // de una tarjeta, y repetirlo en cada una haría pensar que falló en todas.
            state.errorPago?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = PassionTheme.spacing.s2),
                )
            }
            solicitudes.forEach {
                Tarjeta(
                    solicitud = it,
                    pagando = state.pagando == it.id,
                    onPagar = { onPagar(it.id) },
                )
            }
        }
    }
}

@Composable
private fun Tarjeta(
    solicitud: Solicitud,
    pagando: Boolean = false,
    onPagar: () -> Unit = {},
) {
    val forma = MaterialTheme.shapes.medium

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s4)
            .clip(forma)
            .background(PassionTheme.semantics.overlayWeak)
            .padding(PassionTheme.spacing.s4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Folio #${solicitud.folio}",
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Distintivo(solicitud)
        }

        Text(
            text = "${solicitud.fecha} · ${solicitud.unidades} artículo" +
                if (solicitud.unidades == 1) "" else "s",
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = PassionTheme.spacing.s1),
        )

        Text(
            text = "Total estimado: ${formatPrice(solicitud.total)}",
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        )
        // El total ya lleva el envío dentro, así que se dice cuánto es: una cifra que
        // no cuadra con la suma de los artículos invita a pensar que está mal.
        if (solicitud.tieneEnvio) {
            Text(
                text = "Incluye envío" +
                    solicitud.envioDescripcion.takeIf { it.isNotBlank() }
                        ?.let { " · $it" }.orEmpty() +
                    " · ${formatPrice(solicitud.envioCosto)}",
                color = PassionTheme.semantics.onBackgroundSubtle,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Pago(solicitud, pagando, onPagar)

        Guia(solicitud)

        // El motivo solo existe cuando no se pudo atender, y es justo lo que la persona
        // necesita saber para decidir si vuelve a intentarlo.
        if (solicitud.motivoRechazo.isNotBlank()) {
            Text(
                text = solicitud.motivoRechazo,
                color = PassionTheme.semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            )
        }
    }
}

/**
 * En qué va el pago, y el botón para hacerlo.
 *
 * Se enseña siempre que haya algo que decir, incluso «sin pagar»: el hueco en blanco
 * deja a la persona sin saber si la tienda espera su dinero o su paciencia.
 *
 * El importe que se enseña cuando ya está pagado es **el que cobró Mercado Pago**, no el
 * total calculado. Si no coinciden, eso es justo lo que hay que poder ver.
 */
@Composable
private fun Pago(solicitud: Solicitud, pagando: Boolean, onPagar: () -> Unit) {
    val pago = solicitud.pago

    Text(
        text = when (pago) {
            EstadoPago.Aprobado ->
                "Pagado · ${formatPrice(solicitud.pagoTotal.takeIf { it > 0.0 } ?: solicitud.total)}" +
                    etiquetaDePago(
                        marca = solicitud.pagoMarca,
                        metodo = solicitud.pagoMetodo,
                        ultimos4 = solicitud.pagoUltimos4,
                    ).takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            // «En proceso» y no «pendiente»: en Mercado Pago esto es también el estado
            // de un pago en efectivo con ficha sin pagar, y el de una tarjeta en
            // revisión. Lo que las une es que todavía no se sabe.
            EstadoPago.Pendiente -> "Pago en proceso"
            EstadoPago.Rechazado -> "El pago no se completó"
            EstadoPago.Reembolsado -> "Pago reembolsado"
            EstadoPago.Cancelado -> "Pago cancelado"
            EstadoPago.Ninguno -> "Sin pagar"
        },
        color = if (pago == EstadoPago.Aprobado) {
            PassionTheme.semantics.onBackgroundStrong
        } else {
            PassionTheme.semantics.onBackgroundMuted
        },
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (pago == EstadoPago.Aprobado) FontWeight.SemiBold else null,
        modifier = Modifier.padding(top = PassionTheme.spacing.s2),
    )

    // La referencia es su comprobante: es el número que sirve para reclamar en Mercado
    // Pago si algo no cuadra, y ahí no vale el folio nuestro.
    if (pago == EstadoPago.Aprobado && solicitud.pagoReferencia.isNotBlank()) {
        var copiado by remember(solicitud.id) { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ref. ${solicitud.pagoReferencia}",
                color = PassionTheme.semantics.onBackgroundSubtle,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .weight(1f)
                    .clickable { copiado = copyToClipboard(solicitud.pagoReferencia) }
                    .padding(vertical = PassionTheme.spacing.s1),
            )
            Text(
                text = if (copiado) "copiado" else "toca para copiar",
                color = PassionTheme.semantics.onBackgroundSubtle,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = PassionTheme.spacing.s2),
            )
        }
    }

    if (!solicitud.puedePagar) return

    Spacer(Modifier.height(PassionTheme.spacing.s2))
    Button(
        onClick = onPagar,
        enabled = !pagando,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            text = when {
                pagando -> "Abriendo…"
                // Reintentar no es lo mismo que pagar: quien ya vio un rechazo necesita
                // saber que esto es otro intento, no un segundo cargo.
                pago == EstadoPago.Rechazado || pago == EstadoPago.Cancelado ->
                    "Intentar el pago otra vez"
                pago == EstadoPago.Pendiente -> "Continuar el pago"
                else -> "Pagar ${formatPrice(solicitud.total)}"
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * El número de guía, cuando el pedido ya es un paquete.
 *
 * Es el dato que la gente viene a buscar aquí, así que se enseña entero y se copia de un
 * toque: un número de rastreo se usa para pegarlo en otro sitio, y leerlo a mano de una
 * pantalla para teclearlo en otra es donde se cuelan los dígitos cambiados.
 *
 * Sin guía no se inventa nada ni se deja el hueco en blanco: «Pendiente de envío» dice
 * dónde está el pedido, que es la pregunta de fondo.
 */
@Composable
private fun Guia(solicitud: Solicitud) {
    if (!solicitud.tieneGuia) {
        Text(
            text = "Pendiente de envío",
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        )
        return
    }

    var copiado by remember(solicitud.id) { mutableStateOf(false) }

    Text(
        text = solicitud.guiaPaqueteria.ifBlank { "Guía" }.replaceFirstChar { it.uppercase() },
        color = PassionTheme.semantics.onBackgroundSubtle,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = PassionTheme.spacing.s3),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = solicitud.guiaNumero,
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { copiado = copyToClipboard(solicitud.guiaNumero) }
                .padding(vertical = PassionTheme.spacing.s1),
        )
        Text(
            // El aviso sustituye a la invitación, no se añade: dos textos a la vez
            // obligan a leer cuál es cuál.
            text = if (copiado) "copiado" else "toca para copiar",
            color = PassionTheme.semantics.onBackgroundSubtle,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = PassionTheme.spacing.s2),
        )
    }

    if (solicitud.guiaRastreoUrl.isNotBlank()) {
        Text(
            text = "Rastrear envío",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(top = PassionTheme.spacing.s1)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { openInNewTab(solicitud.guiaRastreoUrl) }
                .padding(vertical = PassionTheme.spacing.s1),
        )
    }
}

@Composable
private fun Distintivo(solicitud: Solicitud) {
    val semantics = PassionTheme.semantics
    val fondo = when (solicitud.estado) {
        "atendida" -> MaterialTheme.colorScheme.primaryContainer
        "rechazada" -> MaterialTheme.colorScheme.errorContainer
        else -> semantics.overlayWeak
    }
    val texto = when (solicitud.estado) {
        "atendida" -> MaterialTheme.colorScheme.onPrimaryContainer
        "rechazada" -> MaterialTheme.colorScheme.onErrorContainer
        else -> semantics.onBackgroundMuted
    }

    Text(
        text = solicitud.estadoLegible,
        color = texto,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(fondo)
            .padding(
                horizontal = PassionTheme.spacing.s3,
                vertical = PassionTheme.spacing.s1,
            ),
    )
}
