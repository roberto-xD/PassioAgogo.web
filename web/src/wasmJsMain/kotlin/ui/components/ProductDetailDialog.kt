package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import network.WhatsAppConfig
import ui.pgmodels.PGDataCard
import ui.theme.PassionTheme

/** Pausa entre imágenes del pase automático. */
private const val AUTO_ADVANCE_MS = 4_000L

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

    val pagerState = rememberPagerState(pageCount = { images.size })
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val scope = rememberCoroutineScope()

    // Se incrementa cada vez que la persona navega a mano. Al ser clave del efecto, la
    // cuenta atrás vuelve a empezar: si no, tras avanzar con las flechas podía quedar
    // muy poco tiempo del ciclo en curso y la imagen saltaba sola casi de inmediato.
    var manualNavigation by remember { mutableStateOf(0) }

    // El efecto se rearranca al cambiar la pausa o al navegar a mano, nunca durante su
    // propia animación: así no puede cancelarse a sí mismo a media transición, y al
    // retirar el cursor siempre vuelve a arrancar con el ciclo completo.
    LaunchedEffect(images.size, isHovered, manualNavigation) {
        if (images.size < 2 || isHovered) return@LaunchedEffect
        while (true) {
            delay(AUTO_ADVANCE_MS)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % images.size)
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .hoverable(hoverInteraction),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            AsyncImage(
                model = images[page],
                contentDescription = product.productTittle.ifBlank { "Imagen del producto" },
                // Sin recortar: en una ficha interesa ver el artículo completo.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (images.size > 1) {
            CarouselArrow(
                symbol = "‹",
                modifier = Modifier.align(Alignment.CenterStart),
                onClick = {
                    manualNavigation++
                    scope.launch {
                        val previous = (pagerState.currentPage - 1 + images.size) % images.size
                        pagerState.animateScrollToPage(previous)
                    }
                },
            )
            CarouselArrow(
                symbol = "›",
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = {
                    manualNavigation++
                    scope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage + 1) % images.size)
                    }
                },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(PassionTheme.spacing.s3),
                horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s1),
            ) {
                repeat(images.size) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (active) 9.dp else 7.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(
                                Color.White.copy(alpha = if (active) 0.95f else 0.45f)
                            )
                            .clickable {
                                manualNavigation++
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                    )
                }
            }
        }
    }
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
