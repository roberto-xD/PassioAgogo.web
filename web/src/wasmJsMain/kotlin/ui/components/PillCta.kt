package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.theme.BannerColors

/**
 * La llamada a la acción de los banners: una píldora con halo.
 *
 * **No es un botón.** No recibe `onClick` a propósito: en estos banners lo pulsable es
 * el banner entero, y poner aquí otro destino crearía dos zonas de toque superpuestas
 * que hacen lo mismo. Es la parte que *parece* botón de algo que ya lo es.
 *
 * El halo se dibuja con [drawBehind] y no con una sombra: así no ocupa sitio en el
 * layout y no desplaza al texto de arriba.
 *
 * @param conFlecha añade la flecha «→» detrás del texto. La lleva la ruleta, donde el
 *   gesto es «ir a por el premio»; el catálogo no, que es una invitación más tranquila.
 */
@Composable
fun PillCta(
    text: String,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 17.sp,
    background: Color = BannerColors.Pink,
    contentPadding: PaddingValues = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
    glow: Dp = 6.dp,
    glowColor: Color = BannerColors.Pink.copy(alpha = 0.25f),
    gap: Dp = 10.dp,
    conFlecha: Boolean = false,
    arrowSize: Dp = 18.dp,
    contentColor: Color = BannerColors.White,
) {
    Row(
        modifier = modifier
            .drawBehind {
                if (glow > 0.dp) {
                    val g = glow.toPx()
                    drawRoundRect(
                        color = glowColor,
                        topLeft = Offset(-g, -g),
                        size = Size(size.width + 2 * g, size.height + 2 * g),
                        cornerRadius = CornerRadius(size.height / 2 + g),
                    )
                }
            }
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            // 44 dp es el objetivo táctil mínimo recomendado.
            .heightIn(min = 44.dp)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                color = contentColor,
            ),
        )
        if (conFlecha) {
            ArrowIcon(Modifier.size(arrowSize), contentColor)
        }
    }
}

/** Flecha «→» (24×24, trazo 2.2). */
@Composable
private fun ArrowIcon(modifier: Modifier, color: Color) {
    androidx.compose.foundation.Canvas(modifier) {
        val k = size.width / 24f
        val stroke = 2.2f * k
        drawLine(color, Offset(5f * k, 12f * k), Offset(19f * k, 12f * k), stroke, StrokeCap.Round)
        drawLine(color, Offset(13f * k, 6f * k), Offset(19f * k, 12f * k), stroke, StrokeCap.Round)
        drawLine(color, Offset(19f * k, 12f * k), Offset(13f * k, 18f * k), stroke, StrokeCap.Round)
    }
}
