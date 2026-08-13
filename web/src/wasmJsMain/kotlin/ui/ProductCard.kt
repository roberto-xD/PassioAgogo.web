package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import ui.pgmodels.PGDataCard
import ui.theme.PassionTheme

/**
 * Líneas fijas del título y del resumen en la tarjeta.
 *
 * El texto completo se lee en la ficha, así que aquí recortar no pierde información.
 */
private const val TEXT_LINES = 2

@Composable
fun ProductCard(product: PGDataCard, onClick: () -> Unit = {}) {
    val semantics = PassionTheme.semantics

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = PassionTheme.spacing.s2),
    ) {
        Column(Modifier.padding(PassionTheme.spacing.s3)) {
            // Imagen del producto vía Coil. Si no hay URL, se muestra un placeholder.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(semantics.imagePlaceholder),
                contentAlignment = Alignment.Center,
            ) {
                if (product.urlImage.isNotBlank()) {
                    AsyncImage(
                        model = product.urlImage,
                        contentDescription = product.productTittle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                } else {
                    Text(
                        text = product.productStore.ifBlank { "Passion à gogo" },
                        color = semantics.onImagePlaceholder,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                // Va sobre la imagen y no en la fila del precio, donde chocaría con el
                // badge de OFERTA cuando un artículo por encargo está en promoción.
                if (product.sobrePedido) {
                    Text(
                        text = "SOBRE PEDIDO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(PassionTheme.spacing.s2)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(
                                horizontal = PassionTheme.spacing.s2,
                                vertical = PassionTheme.spacing.s1,
                            ),
                    )
                }
            }

            // Título y resumen ocupan siempre dos líneas, tengan el texto que tengan.
            // `minLines` reserva el alto aunque sobre una línea o el texto falte, y
            // `maxLines` lo recorta si sobra: entre las dos, todas las tarjetas de la
            // cuadrícula miden exactamente lo mismo. La alternativa —dejar que cada
            // tarjeta crezca con su contenido— es lo que producía las filas desalineadas.
            Text(
                text = product.productTittle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = TEXT_LINES,
                maxLines = TEXT_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = PassionTheme.spacing.s3),
            )

            // Se pinta aunque venga vacío: el hueco tiene que quedarse igual, así que no
            // puede haber un `if` que lo quite de la composición.
            Text(
                text = product.productDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = TEXT_LINES,
                maxLines = TEXT_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = PassionTheme.spacing.s1),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PassionTheme.spacing.s2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (product.hasOffert && product.productDiscountPrice.isNotBlank()) {
                    // Con oferta: precio final destacado + original tachado al lado.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.productDiscountPrice,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = semantics.offer,
                        )
                        Text(
                            text = product.productRealPrice,
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = PassionTheme.spacing.s1),
                        )
                    }
                    Text(
                        text = "OFERTA",
                        style = MaterialTheme.typography.labelSmall,
                        color = semantics.onOffer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(semantics.offer)
                            .padding(
                                horizontal = PassionTheme.spacing.s2,
                                vertical = PassionTheme.spacing.s1,
                            ),
                    )
                } else {
                    // Sin precio no se pinta nada, pero el texto sigue midiendo: así la
                    // fila conserva su alto y las tarjetas de la cuadrícula no se
                    // desalinean entre sí.
                    val sinPrecio = product.productRealPrice.isBlank()
                    Text(
                        text = product.productRealPrice.ifBlank { "—" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = if (sinPrecio) Modifier.alpha(0f) else Modifier,
                    )
                }
            }
        }
    }
}
