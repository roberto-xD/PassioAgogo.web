package ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ui.theme.RuletaColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class SegmentIcon { Percent, Gift }

private data class WheelSegment(val fill: Color, val icon: SegmentIcon, val iconColor: Color)

/** Seis gajos, en sentido horario empezando arriba (como en el diseño). */
private val Segments = listOf(
    WheelSegment(RuletaColors.Pink, SegmentIcon.Percent, RuletaColors.White),
    WheelSegment(RuletaColors.Yellow, SegmentIcon.Gift, RuletaColors.Fondo),
    WheelSegment(RuletaColors.Teal, SegmentIcon.Percent, RuletaColors.Fondo),
    WheelSegment(RuletaColors.Blue, SegmentIcon.Gift, RuletaColors.White),
    WheelSegment(RuletaColors.Orange, SegmentIcon.Percent, RuletaColors.Fondo),
    WheelSegment(RuletaColors.Violet, SegmentIcon.Gift, RuletaColors.White),
)

/** Icono «gift» (24×24, trazo) en path data SVG. */
private val GiftPathData = listOf(
    "M4 8h16a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z",
    "M12 8v13",
    "M19 12v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7",
    "M7.5 8a2.5 2.5 0 0 1 0-5A4.8 8 0 0 1 12 8a4.8 8 0 0 1 4.5-5 2.5 2.5 0 0 1 0 5",
)

private const val SPIN_DURATION_MS = 24_000

/**
 * Ruleta completa: disco giratorio + centro con logo + puntero fijo.
 *
 * @param size diámetro del disco.
 * @param hubSize diámetro del círculo central blanco.
 * @param hubRing grosor del aro oscuro alrededor del centro.
 * @param pointerWidth tamaño del puntero; sobresale [pointerOverlap] por arriba.
 * @param hubImageUrl imagen cuadrada para el centro —el abanico de la marca—. Vacío
 *   deja el círculo blanco solo, que es como se ve mientras no haya un recorte
 *   cuadrado en el bucket: el logotipo completo metido en un círculo de 64 dp no se
 *   reconocería. Ver `MediaConfig.RULETA_HUB_PATH`.
 */
@Composable
internal fun PrizeWheel(
    size: Dp,
    hubSize: Dp,
    hubRing: Dp,
    pointerWidth: Dp,
    pointerHeight: Dp,
    pointerOverlap: Dp,
    spinEnabled: Boolean,
    hubImageUrl: String = "",
    modifier: Modifier = Modifier,
) {
    // `requiredSize` y no `size`: la ruleta tiene que mantenerse **cuadrada** aunque no
    // quepa, porque el banner la recorta a propósito.
    //
    // Con `size`, el alto que le deja el banner manda: en escritorio son 380 dp de
    // ruleta a partir de los 60 del borde superior dentro de un banner de 320, así que
    // la caja se medía 380 × 260. El disco se dibuja sobre `minDimension`, de modo que
    // encogía a 260 y se apoyaba arriba a la izquierda, mientras el centro y el puntero
    // seguían alineándose al ancho completo: los tres dejaban de compartir eje. En
    // móvil no pasaba porque 40 + 250 sí cabe en 300, que es lo que hacía que el fallo
    // pareciera cosa del escritorio.
    Box(modifier.requiredSize(size)) {
        WheelDisc(
            spinEnabled = spinEnabled,
            modifier = Modifier.requiredSize(size),
        )
        // Centro: aro oscuro + círculo blanco con el logo (no gira)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(hubSize + hubRing * 2)
                .clip(CircleShape)
                .background(RuletaColors.Fondo)
                .padding(hubRing)
                .clip(CircleShape)
                .background(RuletaColors.White),
            contentAlignment = Alignment.Center,
        ) {
            if (hubImageUrl.isNotBlank()) {
                AsyncImage(
                    model = hubImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(0.62f),
                )
            }
        }
        WheelPointer(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = -pointerOverlap)
                .size(pointerWidth, pointerHeight),
        )
    }
}

@Composable
private fun WheelDisc(spinEnabled: Boolean, modifier: Modifier = Modifier) {
    val rotation = if (spinEnabled) {
        val transition = rememberInfiniteTransition(label = "ruleta")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(SPIN_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "giro",
        )
    } else {
        null
    }

    val textMeasurer = rememberTextMeasurer()
    // Las familias se toman del tema ya montado y no se vuelven a declarar con
    // `Font(...)`: eso dispararía una segunda descarga de la misma tipografía.
    val poppins = MaterialTheme.typography.bodyMedium.fontFamily

    Spacer(
        modifier = modifier
            // Solo la capa rota: el contenido dibujado se cachea y no se repinta por frame.
            .graphicsLayer { rotationZ = rotation?.value ?: 0f }
            .drawWithCache {
                val outerR = this.size.minDimension / 2f
                val center = Offset(outerR, outerR)
                val segmentR = outerR - 6.dp.toPx()
                val iconRadius = segmentR * 0.68f
                val iconSizePx = outerR * 0.28f
                val dotRadius = outerR * 0.024f
                val dotOrbit = outerR - 8.dp.toPx()

                val giftPaths = GiftPathData.map { PathParser().parsePathString(it).toPath() }
                val giftStroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

                val percentLayouts = Segments.map { segment ->
                    textMeasurer.measure(
                        text = "%",
                        style = TextStyle(
                            color = segment.iconColor,
                            fontSize = (outerR * 0.26f).toSp(),
                            fontFamily = poppins,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                onDrawBehind {
                    drawCircle(RuletaColors.White, radius = outerR, center = center)

                    Segments.forEachIndexed { index, segment ->
                        val startFromTop = index * 60f
                        drawArc(
                            color = segment.fill,
                            startAngle = startFromTop - 90f,
                            sweepAngle = 60f,
                            useCenter = true,
                            topLeft = Offset(center.x - segmentR, center.y - segmentR),
                            size = Size(segmentR * 2, segmentR * 2),
                        )

                        val midDeg = startFromTop + 30f
                        val rad = (midDeg - 90f) * PI.toFloat() / 180f
                        val pos = Offset(
                            center.x + iconRadius * cos(rad),
                            center.y + iconRadius * sin(rad),
                        )

                        rotate(degrees = midDeg, pivot = pos) {
                            when (segment.icon) {
                                SegmentIcon.Percent -> {
                                    val layout = percentLayouts[index]
                                    drawText(
                                        textLayoutResult = layout,
                                        topLeft = Offset(
                                            pos.x - layout.size.width / 2f,
                                            pos.y - layout.size.height / 2f,
                                        ),
                                    )
                                }

                                SegmentIcon.Gift -> withTransform({
                                    translate(pos.x - iconSizePx / 2f, pos.y - iconSizePx / 2f)
                                    scale(iconSizePx / 24f, iconSizePx / 24f, pivot = Offset.Zero)
                                }) {
                                    giftPaths.forEach { drawPath(it, segment.iconColor, style = giftStroke) }
                                }
                            }
                        }
                    }

                    // 12 bombillas blancas en el borde
                    repeat(12) { i ->
                        val rad = (i * 30f) * PI.toFloat() / 180f
                        drawCircle(
                            color = RuletaColors.White,
                            radius = dotRadius,
                            center = Offset(
                                center.x + dotOrbit * cos(rad),
                                center.y + dotOrbit * sin(rad),
                            ),
                        )
                    }
                }
            },
    )
}

/** Puntero en forma de gota (viewBox 40×48) con punto rosa. */
@Composable
private fun WheelPointer(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sx = size.width / 40f
        val sy = size.height / 48f
        val drop = Path().apply {
            moveTo(20f * sx, 46f * sy)
            lineTo(4f * sx, 10f * sy)
            // Semicírculo superior de (4,10) a (36,10): centro (20,10), r=16
            arcTo(
                rect = Rect(center = Offset(20f * sx, 10f * sy), radius = 16f * sx),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            close()
        }
        drawPath(drop, RuletaColors.White)
        drawCircle(RuletaColors.Pink, radius = 6f * sx, center = Offset(20f * sx, 16f * sy))
    }
}
