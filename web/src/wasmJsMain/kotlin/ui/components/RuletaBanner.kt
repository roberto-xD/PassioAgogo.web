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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ui.theme.PassionColors
import ui.theme.PassionTheme
import ui.theme.BannerColors

/** Ancho a partir del cual se usa la variante de escritorio. */
private val DesktopBreakpoint = 720.dp

/**
 * Banner promocional «Gira y gana»: enlace a la ruleta de premios.
 *
 * Elige automáticamente la variante de escritorio (1200×320) o móvil (390×300) según el
 * ancho disponible.
 *
 * Es el acceso a la ruleta desde la portada, en el sitio de la entrada que tenía en la
 * barra. Quien decide si se enseña es la misma bandera `ruleta_visible` de siempre: el
 * banner no se pinta solo, lo coloca Inicio cuando la promoción está encendida.
 *
 * @param onClick acción al pulsar el banner (navegar a la ruleta).
 * @param spinEnabled si la ruleta gira. Inicio lo apaga cuando el sistema pide menos
 *   movimiento; una animación de veinticuatro segundos en bucle es justo lo que esa
 *   preferencia existe para evitar.
 * @param hubImageUrl imagen cuadrada para el centro de la ruleta. Vacío = círculo
 *   blanco; ver [PrizeWheel].
 */
@Composable
fun RuletaBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    spinEnabled: Boolean = true,
    hubImageUrl: String = "",
) {
    // Centrado: el contenido se topa con `maxWidth` (1200 dp) antes que con el ancho
    // de la página, y un `Box` alinea al inicio por omisión, así que toda la holgura
    // se iba al lado derecho. En una ventana de 1400 eso son 24 dp a la izquierda y
    // 172 a la derecha, que es justo lo que se veía: pegado al principio y despegado
    // del final.
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        val spec = if (maxWidth >= DesktopBreakpoint) BannerSpec.Desktop else BannerSpec.Mobile
        RuletaBannerContent(
            spec = spec,
            onClick = onClick,
            spinEnabled = spinEnabled,
            hubImageUrl = hubImageUrl,
        )
    }
}

@Composable
internal fun RuletaBannerContent(
    spec: BannerSpec,
    onClick: () -> Unit,
    spinEnabled: Boolean,
    hubImageUrl: String = "",
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ctaScale by animateFloatAsState(
        targetValue = if (hovered) 1.04f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "ctaScale",
    )
    val shape = RoundedCornerShape(spec.corner)

    BoxWithConstraints(
        modifier = Modifier
            .widthIn(max = spec.maxWidth)
            .fillMaxWidth()
            .height(spec.height)
            .clip(shape)
            .background(BannerColors.Fondo)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Ir a la ruleta de premios"
            },
    ) {
        val bannerWidth = maxWidth

        // Confeti decorativo (posiciones relativas al ancho de referencia del diseño)
        spec.confetti.forEach { c ->
            Box(
                Modifier
                    .offset(x = bannerWidth * (c.x / spec.referenceWidth), y = c.y)
                    .size(c.width, c.height)
                    .graphicsLayer { rotationZ = c.rotation }
                    .clip(RoundedCornerShape(c.corner ?: (minOf(c.width, c.height) / 2)))
                    .background(c.color),
            )
        }

        // Ruleta: anclada al borde derecho y recortada por el banner
        PrizeWheel(
            size = spec.wheelSize,
            hubSize = spec.hubSize,
            hubRing = spec.hubRing,
            pointerWidth = spec.pointerWidth,
            pointerHeight = spec.pointerHeight,
            pointerOverlap = spec.pointerOverlap,
            spinEnabled = spinEnabled,
            hubImageUrl = hubImageUrl,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = spec.wheelEndOverflow, y = spec.wheelTop),
        )

        BannerCopy(spec = spec, ctaScale = ctaScale)
    }
}

@Composable
private fun BannerCopy(spec: BannerSpec, ctaScale: Float) {
    // Las familias salen del tema ya montado: volver a declararlas con `Font(...)`
    // dispararía una segunda descarga de la misma tipografía.
    val poppins = MaterialTheme.typography.bodyMedium.fontFamily ?: FontFamily.Default
    val alexBrush = PassionTheme.type.scriptAccent.fontFamily ?: FontFamily.Default
    val tightLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

    val titleStyle = TextStyle(
        fontFamily = poppins,
        fontWeight = FontWeight.Bold,
        fontSize = spec.titleSize,
        lineHeight = spec.titleSize * 1.05f,
        lineHeightStyle = tightLineHeight,
        color = BannerColors.White,
    )
    val scriptStyle = TextStyle(
        fontFamily = alexBrush,
        fontSize = spec.scriptSize,
        lineHeight = spec.scriptSize,
        lineHeightStyle = tightLineHeight,
        color = BannerColors.PinkScript,
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = spec.contentStart)
            .width(spec.contentWidth),
        verticalArrangement = Arrangement.spacedBy(spec.contentGap, Alignment.CenterVertically),
    ) {
        Badge(spec, poppins)

        if (spec.inlineTitle) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BasicText("Gira y gana", style = titleStyle, modifier = Modifier.alignByBaseline())
                BasicText("tu premio", style = scriptStyle, modifier = Modifier.alignByBaseline())
            }
        } else {
            BasicText("Gira y gana", style = titleStyle)
            BasicText("tu premio", style = scriptStyle)
        }

        BasicText(
            text = spec.body,
            style = TextStyle(
                fontFamily = poppins,
                fontSize = spec.bodySize,
                lineHeight = spec.bodySize * spec.bodyLineHeight,
                color = PassionColors.Purple100,
            ),
            modifier = Modifier.widthIn(max = 460.dp),
        )

        PillCta(
            text = spec.cta,
            fontFamily = poppins,
            fontSize = spec.ctaSize,
            background = BannerColors.Pink,
            contentPadding = spec.ctaPadding,
            glow = spec.ctaGlow,
            gap = spec.ctaGap,
            conFlecha = true,
            arrowSize = spec.arrowSize,
            modifier = Modifier
                .padding(top = spec.ctaExtraTop)
                .graphicsLayer {
                    scaleX = ctaScale
                    scaleY = ctaScale
                },
        )
    }
}

@Composable
private fun Badge(spec: BannerSpec, fontFamily: FontFamily) {
    BasicText(
        text = spec.badge.uppercase(),
        style = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = spec.badgeSize,
            letterSpacing = 0.14.em,
            color = BannerColors.Fondo,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(BannerColors.Yellow)
            .padding(spec.badgePadding),
    )
}

internal data class Confetti(
    val x: Float,
    val y: Dp,
    val width: Dp,
    val height: Dp,
    val color: Color,
    val rotation: Float = 0f,
    /** null = píldora/círculo; si no, radio fijo. */
    val corner: Dp? = null,
)

/** Medidas de cada variante, tomadas 1:1 del diseño. */
internal data class BannerSpec(
    val referenceWidth: Float,
    val maxWidth: Dp,
    val height: Dp,
    val corner: Dp,
    // Ruleta
    val wheelSize: Dp,
    val wheelTop: Dp,
    val wheelEndOverflow: Dp,
    val hubSize: Dp,
    val hubRing: Dp,
    val pointerWidth: Dp,
    val pointerHeight: Dp,
    val pointerOverlap: Dp,
    // Texto
    val contentStart: Dp,
    val contentWidth: Dp,
    val contentGap: Dp,
    val badge: String,
    val badgeSize: TextUnit,
    val badgePadding: PaddingValues,
    val inlineTitle: Boolean,
    val titleSize: TextUnit,
    val scriptSize: TextUnit,
    val body: String,
    val bodySize: TextUnit,
    val bodyLineHeight: Float,
    val cta: String,
    val ctaSize: TextUnit,
    val ctaPadding: PaddingValues,
    val ctaGap: Dp,
    val ctaGlow: Dp,
    val ctaExtraTop: Dp,
    val arrowSize: Dp,
    val confetti: List<Confetti>,
) {
    companion object {
        val Desktop = BannerSpec(
            referenceWidth = 1200f,
            maxWidth = 1200.dp,
            height = 320.dp,
            corner = 24.dp,
            wheelSize = 380.dp,
            wheelTop = 60.dp,
            wheelEndOverflow = 20.dp, // 840 + 380 − 1200
            hubSize = 88.dp,
            hubRing = 6.dp,
            pointerWidth = 40.dp,
            pointerHeight = 48.dp,
            pointerOverlap = 14.dp,
            contentStart = 64.dp,
            contentWidth = 600.dp,
            contentGap = 12.dp,
            badge = "Nuevo · Ruleta de premios",
            badgeSize = 13.sp,
            badgePadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            inlineTitle = true,
            titleSize = 60.sp,
            scriptSize = 50.sp,
            body = "Llévate un código de descuento o un regalo para tu próxima compra.",
            bodySize = 18.sp,
            bodyLineHeight = 1.5f,
            cta = "Girar la ruleta",
            ctaSize = 17.sp,
            ctaPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
            ctaGap = 10.dp,
            ctaGlow = 6.dp,
            ctaExtraTop = 8.dp,
            arrowSize = 18.dp,
            confetti = listOf(
                Confetti(700f, 30.dp, 14.dp, 14.dp, BannerColors.Yellow),
                Confetti(640f, 250.dp, 10.dp, 10.dp, BannerColors.Teal),
                Confetti(720f, 60.dp, 18.dp, 6.dp, BannerColors.Pink, rotation = 30f),
                Confetti(790f, 150.dp, 12.dp, 12.dp, BannerColors.Blue, rotation = 20f, corner = 3.dp),
                Confetti(1150f, 24.dp, 12.dp, 12.dp, BannerColors.Yellow, rotation = 40f, corner = 3.dp),
                Confetti(760f, 280.dp, 16.dp, 5.dp, BannerColors.Orange, rotation = -25f),
                Confetti(660f, 292.dp, 8.dp, 8.dp, BannerColors.White),
                Confetti(690f, 180.dp, 8.dp, 8.dp, BannerColors.Yellow),
            ),
        )

        val Mobile = BannerSpec(
            referenceWidth = 390f,
            maxWidth = 720.dp,
            height = 300.dp,
            corner = 20.dp,
            wheelSize = 250.dp,
            wheelTop = 40.dp,
            wheelEndOverflow = 96.dp, // 236 + 250 − 390
            hubSize = 64.dp,
            hubRing = 5.dp,
            pointerWidth = 34.dp,
            pointerHeight = 40.dp,
            pointerOverlap = 12.dp,
            contentStart = 22.dp,
            contentWidth = 200.dp,
            contentGap = 8.dp,
            badge = "Nuevo · Ruleta",
            badgeSize = 10.sp,
            badgePadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            inlineTitle = false,
            titleSize = 34.sp,
            scriptSize = 38.sp,
            body = "Descuentos o un regalo para tu próxima compra.",
            bodySize = 13.sp,
            bodyLineHeight = 1.45f,
            cta = "Girar",
            ctaSize = 15.sp,
            ctaPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ctaGap = 8.dp,
            ctaGlow = 0.dp,
            ctaExtraTop = 4.dp,
            arrowSize = 16.dp,
            confetti = listOf(
                Confetti(18f, 18.dp, 10.dp, 10.dp, BannerColors.Yellow),
                Confetti(200f, 268.dp, 8.dp, 8.dp, BannerColors.Teal),
                Confetti(232f, 30.dp, 12.dp, 4.dp, BannerColors.Pink, rotation = 30f),
                Confetti(190f, 140.dp, 8.dp, 8.dp, BannerColors.Blue, rotation = 20f, corner = 3.dp),
                Confetti(160f, 22.dp, 6.dp, 6.dp, BannerColors.White),
            ),
        )
    }
}
