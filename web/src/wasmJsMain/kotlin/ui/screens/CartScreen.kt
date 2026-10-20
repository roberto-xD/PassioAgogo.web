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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import models.formatPrice
import ui.components.ContentScreen
import ui.components.FormField
import ui.components.Paragraph
import ui.components.SectionTitle
import ui.theme.PassionTheme
import viewmodel.CartUiState

/** Lado de la miniatura de cada renglón. */
private val MINIATURA = 64.dp

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
    onSubmit: () -> Unit,
    onReset: () -> Unit,
    onGoToLogin: () -> Unit,
    onGoToCatalog: () -> Unit,
    onGoToRequests: () -> Unit,
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
        FormField(
            value = state.nombre,
            onValueChange = onNombreChange,
            label = "Nombre *",
        )
        FormField(
            value = state.telefono,
            onValueChange = onTelefonoChange,
            label = "Teléfono",
            keyboardType = KeyboardType.Phone,
            supportingText = "Por aquí te contactamos para cerrar el pedido.",
        )
        FormField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Correo",
            keyboardType = KeyboardType.Email,
        )
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

        Spacer(Modifier.height(PassionTheme.spacing.s5))
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
