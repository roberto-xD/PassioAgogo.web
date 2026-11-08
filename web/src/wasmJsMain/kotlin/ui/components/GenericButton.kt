package ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ui.theme.PassionTheme
import ui.theme.tonos

enum class ButtonVariant { Primario, Secundario, Fantasma, Acento }
enum class ButtonSize(val relleno: PaddingValues) {
    Sm(PaddingValues(horizontal = 16.dp, vertical = 8.dp)),
    Md(PaddingValues(horizontal = 22.dp, vertical = 11.dp)),
    Lg(PaddingValues(horizontal = 28.dp, vertical = 14.dp)),
}

private val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private data class Cuarteto(val a: Color, val b: Color, val c: Color, val d: Color)

@Composable
fun PassionButton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variante: ButtonVariant = ButtonVariant.Primario,
    tamano: ButtonSize = ButtonSize.Md,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val tonos = PassionTheme.tonos
    val forma = MaterialTheme.shapes.extraLarge // píldora (PassionRadius.Pill)
    val interaccion = remember { MutableInteractionSource() }
    val hover by interaccion.collectIsHoveredAsState()
    val presionado by interaccion.collectIsPressedAsState()
    val activo = enabled && (hover || presionado)

    val (fondo, fondoActivo, colorTexto, borde) = when (variante) {
        ButtonVariant.Primario -> Cuarteto(cs.primary, tonos.marcaHover, cs.onPrimary, Color.Transparent)
        ButtonVariant.Secundario -> Cuarteto(cs.surface, tonos.marcaSuave, cs.primary, cs.outline)
        ButtonVariant.Fantasma -> Cuarteto(Color.Transparent, tonos.marcaSuave, cs.primary, Color.Transparent)
        ButtonVariant.Acento -> Cuarteto(cs.secondary, tonos.acentoHover, cs.onSecondary, Color.Transparent)
    }
    val fondoAnimado by animateColorAsState(
        targetValue = when {
            !enabled -> cs.outlineVariant
            activo -> fondoActivo
            else -> fondo
        },
        animationSpec = tween(120, easing = EaseOut),
    )
    val elevar by animateDpAsState(if (activo) (-1).dp else 0.dp, tween(120, easing = EaseOut))
    val brillo = activo && variante == ButtonVariant.Primario
    val colorBrillo = cs.primary.copy(alpha = 0.14f)

    val estilo = if (tamano == ButtonSize.Sm) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall

    Box(
        modifier
            .graphicsLayer { translationY = elevar.toPx() }
            // --shadow-glow: anillo de 4dp por fuera del botón
            .drawBehind {
                if (brillo) {
                    val r = 4.dp.toPx()
                    drawRoundRect(
                        color = colorBrillo,
                        topLeft = Offset(-r, -r),
                        size = Size(size.width + 2 * r, size.height + 2 * r),
                        cornerRadius = CornerRadius(size.height / 2 + r),
                    )
                }
            }
            .clip(forma)
            .background(fondoAnimado)
            .border(1.dp, if (enabled) borde else Color.Transparent, forma)
            .clickable(
                interactionSource = interaccion,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(tamano.relleno),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            style = estilo.copy(lineHeight = estilo.fontSize * 1.2f),
            color = if (enabled) colorTexto else PassionTheme.semantics.onBackgroundSubtle,
            maxLines = 1,
        )
    }
}