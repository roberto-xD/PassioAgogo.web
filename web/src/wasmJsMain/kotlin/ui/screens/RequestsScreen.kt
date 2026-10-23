package ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import models.Solicitud
import models.formatPrice
import ui.components.Paragraph
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

        else -> solicitudes.forEach { Tarjeta(it) }
    }
}

@Composable
private fun Tarjeta(solicitud: Solicitud) {
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
