package ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import network.WhatsAppConfig
import ui.components.AttributeChips
import ui.components.VariantPicker
import ui.components.ZoomableImageCarousel
import ui.components.openInNewTab
import ui.pgmodels.PGDataCard
import ui.pgmodels.PGVariant
import ui.theme.PassionTheme
import viewmodel.CatalogUiState

/** Por debajo de esto no caben la tira, la imagen y la información una al lado de otra. */
private val SIDE_BY_SIDE_MIN_WIDTH = 900.dp

/** Ancho de la tira de miniaturas en la disposición ancha. */
private val THUMB_RAIL_WIDTH = 96.dp

/** Lado de cada miniatura. Cuadradas: el recorte iguala fotos de distinta proporción. */
private val THUMB_SIZE = 88.dp

/** Tope del contenido. Más ancho y la descripción se lee en renglones interminables. */
private val CONTENT_MAX_WIDTH = 1200.dp

/**
 * Ficha de producto de una sección, con ruta propia
 * (`#/seccion/lenceria/conjunto-de-encaje`).
 *
 * Es la alternativa al diálogo del catálogo general, no su sustituta: los productos de
 * una sección se presentan como escaparate —tira de miniaturas, imagen grande y una
 * tarjeta de información aparte— mientras el resto del catálogo conserva su diálogo.
 *
 * El botón de compra todavía no compra: abre WhatsApp con el mensaje redactado, igual
 * que «Me interesa» en el resto del sitio. Decirlo debajo del botón evita prometer un
 * carrito que no existe.
 *
 * @param product lo resuelve quien llama, a partir del slug de la URL. Se busca sobre
 *   toda la sección y no sobre lo que hay filtrado en pantalla, porque esta ficha llega
 *   por enlace compartido tanto como por un toque en la rejilla.
 */
@Composable
fun SectionProductScreen(
    state: CatalogUiState,
    product: PGDataCard?,
    onBack: () -> Unit,
    onAddToCart: (PGDataCard, PGVariant?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = CONTENT_MAX_WIDTH)
                .fillMaxWidth()
                .padding(PassionTheme.spacing.s5),
        ) {
            Text(
                text = "‹ Volver a ${state.currentSection?.nombre ?: "la sección"}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable(onClick = onBack)
                    .padding(
                        horizontal = PassionTheme.spacing.s2,
                        vertical = PassionTheme.spacing.s1,
                    ),
            )

            when {
                state.isLoading -> Aviso("Cargando producto…")

                product == null -> Aviso(
                    "No encontramos este producto. Puede que ya no esté disponible."
                )

                else -> ProductoEnEscaparate(product, onAddToCart)
            }
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        text = texto,
        color = PassionTheme.semantics.onBackgroundMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = PassionTheme.spacing.s6),
    )
}

@Composable
private fun ProductoEnEscaparate(
    product: PGDataCard,
    onAddToCart: (PGDataCard, PGVariant?) -> Unit,
) {
    val images = product.images.ifEmpty {
        listOfNotNull(product.urlImage.takeIf { it.isNotBlank() })
    }
    // El estado del carrusel se iza aquí porque lo gobiernan dos mandos: el propio
    // carrusel y la tira de miniaturas, que tienen que mostrar siempre lo mismo.
    val pagerState = rememberPagerState(pageCount = { images.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    var saltos by remember { mutableStateOf(0) }

    val irA: (Int) -> Unit = { indice ->
        saltos++
        scope.launch { pagerState.animateScrollToPage(indice) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val enFila = maxWidth >= SIDE_BY_SIDE_MIN_WIDTH

        if (enFila) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PassionTheme.spacing.s4),
                horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s4),
            ) {
                TiraVertical(
                    images = images,
                    actual = pagerState.currentPage,
                    onSelect = irA,
                    modifier = Modifier.width(THUMB_RAIL_WIDTH),
                )
                PanelDeImagen(
                    images = images,
                    product = product,
                    pagerState = pagerState,
                    saltos = saltos,
                    modifier = Modifier.weight(1.4f),
                )
                TarjetaDeInformacion(
                    product = product,
                    onAddToCart = onAddToCart,
                    modifier = Modifier.weight(1f).widthIn(min = 300.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PassionTheme.spacing.s4),
                verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s4),
            ) {
                PanelDeImagen(
                    images = images,
                    product = product,
                    pagerState = pagerState,
                    saltos = saltos,
                    modifier = Modifier.fillMaxWidth(),
                )
                // En vertical la tira se tumba: una columna de miniaturas al lado
                // dejaría la imagen principal en nada.
                TiraHorizontal(
                    images = images,
                    actual = pagerState.currentPage,
                    onSelect = irA,
                )
                TarjetaDeInformacion(product, onAddToCart, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PanelDeImagen(
    images: List<String>,
    product: PGDataCard,
    pagerState: androidx.compose.foundation.pager.PagerState,
    saltos: Int,
    modifier: Modifier,
) {
    val forma = MaterialTheme.shapes.medium

    Box(
        modifier = modifier
            .aspectRatio(0.86f)
            .clip(forma)
            .background(PassionTheme.semantics.imagePlaceholder),
        contentAlignment = Alignment.Center,
    ) {
        if (images.isEmpty()) {
            Text(
                text = product.productStore.ifBlank { "Passion à gogo" },
                color = PassionTheme.semantics.onImagePlaceholder,
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            ZoomableImageCarousel(
                images = images,
                contentDescription = product.productTittle.ifBlank { "Imagen del producto" },
                modifier = Modifier.fillMaxSize(),
                pagerState = pagerState,
                externalNavigation = saltos,
                // El panel es parte de la página, no un visor a oscuras: el negro del
                // diálogo abriría un agujero en medio del escaparate.
                background = PassionTheme.semantics.imagePlaceholder,
            )
        }
    }
}

@Composable
private fun TiraVertical(
    images: List<String>,
    actual: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    if (images.size < 2) return
    // Sin scroll propio: la pantalla entera ya se desplaza, y anidar dos desplazamientos
    // verticales hace que Compose mida con altura infinita y reviente. La tira crece
    // hacia abajo y se recorre con el scroll de la página.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
    ) {
        images.forEachIndexed { indice, url ->
            Miniatura(url, indice == actual) { onSelect(indice) }
        }
    }
}

@Composable
private fun TiraHorizontal(images: List<String>, actual: Int, onSelect: (Int) -> Unit) {
    if (images.size < 2) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
    ) {
        images.forEachIndexed { indice, url ->
            Miniatura(url, indice == actual) { onSelect(indice) }
        }
    }
}

/** La seleccionada lleva borde: sin él no se sabe cuál de las fotos se está viendo. */
@Composable
private fun Miniatura(url: String, seleccionada: Boolean, onClick: () -> Unit) {
    val forma = MaterialTheme.shapes.small
    Box(
        modifier = Modifier
            .size(THUMB_SIZE)
            .clip(forma)
            .background(PassionTheme.semantics.imagePlaceholder)
            .border(
                width = if (seleccionada) 2.dp else 1.dp,
                color = if (seleccionada) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = forma,
            )
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = rememberAsyncImagePainter(url),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TarjetaDeInformacion(
    product: PGDataCard,
    onAddToCart: (PGDataCard, PGVariant?) -> Unit,
    modifier: Modifier,
) {
    val forma = MaterialTheme.shapes.medium
    val semantics = PassionTheme.semantics

    // La presentación elegida: arranca en la del precio anunciado, que es la más
    // barata. `remember(product)` la reinicia al abrir la ficha de otro producto.
    var elegida by remember(product) {
        mutableStateOf(
            product.variants.firstOrNull { it.id == product.variantId }
                ?: product.variants.firstOrNull()
        )
    }

    Column(
        modifier = modifier
            .clip(forma)
            .background(semantics.overlayWeak)
            .padding(PassionTheme.spacing.s5),
    ) {
        if (product.hasOffert || product.sobrePedido) {
            Row(horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2)) {
                if (product.hasOffert) {
                    Distintivo("Promoción", semantics.offer, semantics.onOffer)
                }
                if (product.sobrePedido) {
                    Distintivo(
                        "Sobre pedido",
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        if (product.productCategory.isNotBlank()) {
            Text(
                text = product.productCategory,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = PassionTheme.spacing.s3),
            )
        }

        Text(
            text = product.productTittle,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = PassionTheme.spacing.s1),
        )

        VariantPicker(
            variants = product.variants,
            selectedId = elegida?.id.orEmpty(),
            onSelect = { elegida = it },
            modifier = Modifier.padding(top = PassionTheme.spacing.s4),
        )

        Precio(product, elegida)

        // Agregar manda; «Me interesa» se queda al lado para quien prefiera escribir
        // antes de pedir nada.
        if (product.variantId.isNotBlank()) {
            Button(
                onClick = { onAddToCart(product, elegida) },
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(top = PassionTheme.spacing.s6),
            ) {
                Text("Agregar a mi solicitud", style = MaterialTheme.typography.labelLarge)
            }
        }

        Button(
            onClick = { openInNewTab(WhatsAppConfig.enlaceParaProducto(product.productTittle)) },
            enabled = WhatsAppConfig.isConfigured,
            shape = MaterialTheme.shapes.extraLarge,
            colors = if (product.variantId.isNotBlank()) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(
                    top = if (product.variantId.isNotBlank()) PassionTheme.spacing.s2
                    else PassionTheme.spacing.s6
                ),
        ) {
            Text("Me interesa", style = MaterialTheme.typography.labelLarge)
        }

        Text(
            text = if (WhatsAppConfig.isConfigured) {
                "Se abrirá WhatsApp con un mensaje listo para enviarnos. Podrás " +
                    "revisarlo antes de mandarlo."
            } else {
                "El contacto por WhatsApp aún no está configurado."
            },
            color = semantics.onBackgroundSubtle,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        )

        val texto = product.productDetail.ifBlank { product.productDescription }
        if (texto.isNotBlank()) {
            Text(
                text = texto,
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = PassionTheme.spacing.s5),
            )
        }

        if (product.attributes.isNotEmpty()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = PassionTheme.spacing.s5),
            )
            AttributeChips(chips = product.attributes)
        }
    }
}

@Composable
private fun Precio(product: PGDataCard, elegida: PGVariant?) {
    val precioFinal = elegida?.precioFinal ?: product.productRealPrice
    val precioLista = elegida?.precioLista ?: product.productRealPrice
    val enOferta = elegida?.hasOffert ?: product.hasOffert
    if (precioFinal.isBlank()) return

    Row(
        modifier = Modifier.padding(top = PassionTheme.spacing.s3),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = precioFinal,
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (enOferta) {
            // El precio tachado al lado: sin él, un descuento es solo un número bajo.
            Text(
                text = precioLista,
                color = PassionTheme.semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = TextDecoration.LineThrough,
                modifier = Modifier.padding(
                    start = PassionTheme.spacing.s2,
                    bottom = PassionTheme.spacing.s1,
                ),
            )
        }
    }
}

@Composable
private fun Distintivo(text: String, container: Color, content: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(
                horizontal = PassionTheme.spacing.s3,
                vertical = PassionTheme.spacing.s1,
            ),
    )
}
