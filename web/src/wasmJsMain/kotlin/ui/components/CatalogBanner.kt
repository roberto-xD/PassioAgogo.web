package ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import network.MediaConfig
import ui.theme.BannerColors
import ui.theme.PassionColors
import ui.theme.PassionTheme

/** Ancho de referencia del panel morado en el diseño, para situar el confeti. */
private const val PANEL_REFERENCE_WIDTH = 740f

/** Por debajo de este ancho los paneles se apilan (el diseño solo define 1200×320). */
private val CompactMaxWidth = 720.dp

private data class Sparkle(
    val x: Float, // px sobre el panel de 740 de ancho
    val y: Dp,
    val width: Dp,
    val height: Dp,
    val color: Color,
    val rotation: Float = 0f,
    val corner: Dp? = null,
)

private val Sparkles = listOf(
    Sparkle(660f, 28.dp, 14.dp, 14.dp, BannerColors.Yellow),
    Sparkle(690f, 250.dp, 16.dp, 5.dp, BannerColors.PinkScript, rotation = 30f),
    Sparkle(620f, 140.dp, 10.dp, 10.dp, PassionColors.Purple100, rotation = 20f, corner = 3.dp),
    Sparkle(24f, 24.dp, 8.dp, 8.dp, BannerColors.White),
    Sparkle(560f, 292.dp, 8.dp, 8.dp, BannerColors.Yellow),
)

/** Confeti con x relativa al ancho del panel (diseño: 740). */
private fun DrawScope.drawSparkles() {
    Sparkles.forEach { s ->
        val w = s.width.toPx()
        val h = s.height.toPx()
        val topLeft = Offset(size.width * (s.x / PANEL_REFERENCE_WIDTH), s.y.toPx())
        val corner = s.corner?.toPx() ?: (minOf(w, h) / 2f)
        rotate(s.rotation, pivot = topLeft + Offset(w / 2f, h / 2f)) {
            drawRoundRect(s.color, topLeft, Size(w, h), CornerRadius(corner))
        }
    }
}

/**
 * Banner de catálogo (1200×320): panel morado con texto y llamada a la acción a la
 * izquierda, logotipo sobre blanco a la derecha. Todo el banner es un enlace.
 *
 * Sustituye al encabezado suelto que tenía la portada —logotipo, titular, párrafo y
 * botón, cada uno flotando sobre el fondo— y lo recoge en una pieza con su propio
 * marco, hermana del banner de la ruleta que va justo debajo.
 */
@Composable
fun CatalogBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ctaScale by animateFloatAsState(if (hovered) 1.04f else 1f, tween(120), label = "ctaScale")

    // El tope va en una caja interior, no sobre el `modifier` que llega.
    //
    // Si quien llama pasa `fillMaxWidth` —y la portada lo hace—, ese modificador fija
    // el ancho del padre como mínimo y el `widthIn` de detrás ya no puede recortarlo:
    // el banner salía a todo lo ancho mientras el de la ruleta se quedaba en sus 1200,
    // y los dos juntos no cuadraban. Con la caja exterior centrando, da igual lo que
    // llegue de fuera.
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
    BoxWithConstraints(
        modifier = Modifier
            .widthIn(max = 1200.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "Ir al catálogo" },
    ) {
        if (maxWidth >= CompactMaxWidth) {
            // Panel morado 740 / blanco 460, misma altura (mín. 320)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                PurplePane(compact = false, ctaScale = ctaScale, modifier = Modifier.weight(740f))
                LogoPane(Modifier.weight(460f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                LogoPane(Modifier.fillMaxWidth())
                PurplePane(compact = true, ctaScale = ctaScale, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    }
}

@Composable
private fun PurplePane(compact: Boolean, ctaScale: Float, modifier: Modifier) {
    // Del tema ya montado: declararlas otra vez con `Font(...)` dispararía una segunda
    // descarga de la misma tipografía.
    val poppins = MaterialTheme.typography.bodyMedium.fontFamily ?: FontFamily.Default
    val titleSize = if (compact) 30.sp else 40.sp

    Column(
        modifier = modifier
            .heightIn(min = if (compact) 0.dp else 320.dp)
            .background(PassionColors.Purple600)
            .drawBehind { drawSparkles() }
            .padding(
                horizontal = if (compact) 24.dp else 56.dp,
                vertical = if (compact) 40.dp else 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        BasicText(
            text = "CATÁLOGO Y PROMOCIONES",
            style = TextStyle(
                fontFamily = poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 12.sp else 13.sp,
                letterSpacing = 0.14.em,
                color = PassionColors.Purple100,
            ),
        )
        BasicText(
            text = "Tu catálogo, en un solo lugar",
            style = TextStyle(
                fontFamily = poppins,
                fontWeight = FontWeight.Bold,
                fontSize = titleSize,
                lineHeight = titleSize * 1.15f,
                color = BannerColors.White,
            ),
            modifier = Modifier.widthIn(max = 600.dp),
        )
        BasicText(
            text = "Explora nuestro catálogo con calma y descubre las promociones " +
                "vigentes, reunidas en una sola experiencia.",
            style = TextStyle(
                fontFamily = poppins,
                fontSize = 17.sp,
                lineHeight = 26.35.sp,
                color = PassionColors.Purple100,
            ),
            modifier = Modifier.widthIn(max = 540.dp),
        )
        PillCta(
            text = "Ver catálogo",
            fontFamily = poppins,
            fontSize = 17.sp,
            background = BannerColors.Pink,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
            glow = 6.dp,
            modifier = Modifier
                .padding(top = 8.dp)
                .graphicsLayer {
                    scaleX = ctaScale
                    scaleY = ctaScale
                },
        )
    }
}

/**
 * El logotipo sobre blanco.
 *
 * La imagen es la remota del bucket, la misma que usaba la portada, y no un recurso
 * empaquetado: aquí no existe ese recurso. Sin logotipo configurado se cae al nombre en
 * la tipografía de la marca, para que el panel no quede en blanco.
 */
@Composable
private fun LogoPane(modifier: Modifier) {
    Box(
        modifier = modifier
            .background(BannerColors.White)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (MediaConfig.isLogoConfigured) {
            AsyncImage(
                model = MediaConfig.logoUrl,
                contentDescription = "Passion à gogo, tienda para adultos",
                contentScale = ContentScale.Fit,
                // Sin `aspectRatio` fijo: el recorte real del bucket no tiene por qué
                // ser el del diseño, y forzarlo lo deformaría.
                modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth(),
            )
        } else {
            BasicText(
                text = "Passion à gogo",
                style = PassionTheme.type.scriptAccent.copy(
                    color = PassionColors.Purple600,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
