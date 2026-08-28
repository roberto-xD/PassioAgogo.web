package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import network.WhatsAppConfig
import ui.pgmodels.PGDataCard
import ui.theme.PassionTheme

/**
 * Ficha ampliada de un producto.
 *
 * Reutiliza [DetailDialogShell] —el mismo armazón que la galería— y sustituye la imagen
 * única por un carrusel paginado: las fotografías pasan **de una en una**, solas, y se
 * pueden recorrer con las flechas, arrastrando o pulsando los puntos.
 *
 * La llamada a la acción abre WhatsApp con el mensaje ya redactado. No envía nada por sí
 * sola: deja la conversación preparada para que la persona pulse enviar.
 */
@Composable
fun ProductDetailDialog(product: PGDataCard, onDismiss: () -> Unit) {
    DetailDialogShell(
        onDismiss = onDismiss,
        imagePane = { modifier -> ProductImages(product, modifier) },
        infoPane = { ProductInfo(product) },
    )
}

@Composable
private fun ProductImages(product: PGDataCard, modifier: Modifier) {
    val images = product.images.ifEmpty { listOfNotNull(product.urlImage.takeIf { it.isNotBlank() }) }

    if (images.isEmpty()) {
        Box(
            modifier = modifier.background(PassionTheme.semantics.imagePlaceholder),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = product.productStore.ifBlank { "Passion à gogo" },
                color = PassionTheme.semantics.onImagePlaceholder,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        return
    }

    ZoomableImageCarousel(
        images = images,
        contentDescription = product.productTittle.ifBlank { "Imagen del producto" },
        modifier = modifier,
    )
}

@Composable
private fun ColumnScope.ProductInfo(product: PGDataCard) {
    val semantics = PassionTheme.semantics

    if (product.productBrand.isNotBlank()) {
        Text(
            text = product.productBrand.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
    Text(
        text = product.productTittle,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = PassionTheme.spacing.s1),
    )

    // Distintivos: solo aparecen los que apliquen.
    if (product.hasOffert || product.sobrePedido) {
        Row(
            modifier = Modifier.padding(top = PassionTheme.spacing.s3),
            horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
        ) {
            if (product.hasOffert) {
                Badge(
                    text = "OFERTA",
                    container = semantics.offer,
                    content = semantics.onOffer,
                )
            }
            if (product.sobrePedido) {
                Badge(
                    text = "SOBRE PEDIDO",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    if (product.productRealPrice.isNotBlank()) {
        Row(
            modifier = Modifier.padding(top = PassionTheme.spacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (product.hasOffert && product.productDiscountPrice.isNotBlank()) {
                Text(
                    text = product.productDiscountPrice,
                    color = semantics.offer,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = product.productRealPrice,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = TextDecoration.LineThrough,
                    modifier = Modifier.padding(start = PassionTheme.spacing.s2),
                )
            } else {
                Text(
                    text = product.productRealPrice,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    // Descripción larga: es lo que la tarjeta no muestra.
    if (product.productDetail.isNotBlank()) {
        Text(
            text = product.productDetail,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s4),
        )
    } else if (product.productDescription.isNotBlank()) {
        // Sin descripción larga, al menos el resumen: mejor eso que un hueco vacío.
        Text(
            text = product.productDescription,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s4),
        )
    }

    // Rasgos del producto. Van después del texto y antes de la acción: son el último
    // dato que se consulta al decidir, no un adorno de cabecera.
    if (product.attributes.isNotEmpty()) {
        Text(
            text = "Características",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = PassionTheme.spacing.s5),
        )
        AttributeChips(
            chips = product.attributes,
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        )
    }

    Spacer(Modifier.height(PassionTheme.spacing.s6))
    Button(
        onClick = { openInNewTab(WhatsAppConfig.enlaceParaProducto(product.productTittle)) },
        enabled = WhatsAppConfig.isConfigured,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text("Me interesa", style = MaterialTheme.typography.labelLarge)
    }
    Text(
        text = if (WhatsAppConfig.isConfigured) {
            "Al pulsar «Me interesa» se abrirá WhatsApp con un mensaje listo para " +
                "enviarnos. Podrás revisarlo antes de mandarlo."
        } else {
            "El contacto por WhatsApp aún no está configurado."
        },
        color = semantics.onBackgroundSubtle,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = PassionTheme.spacing.s2),
    )
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(container)
            .padding(
                horizontal = PassionTheme.spacing.s2,
                vertical = PassionTheme.spacing.s1,
            ),
    )
}
