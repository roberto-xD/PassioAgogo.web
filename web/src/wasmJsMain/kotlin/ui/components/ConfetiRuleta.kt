package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.theme.RuletaColors

/**
 * Un papelillo, colocado en proporción al área que lo contiene.
 *
 * Las posiciones van en fracciones y no en dp —como sí hace el banner, que tiene un
 * ancho de referencia fijo— porque aquí el área es la sección entera y cambia de tamaño
 * con la ventana. Con medidas fijas, el confeti se apelotonaba en una esquina.
 */
private data class Papel(
    val fx: Float,
    val fy: Float,
    val width: Dp,
    val height: Dp,
    val color: Color,
    val rotation: Float = 0f,
    val corner: Dp? = null,
)

private val Papeles = listOf(
    Papel(0.04f, 0.07f, 12.dp, 12.dp, RuletaColors.Yellow),
    Papel(0.30f, 0.04f, 14.dp, 5.dp, RuletaColors.Pink, rotation = 30f),
    Papel(0.93f, 0.09f, 11.dp, 11.dp, RuletaColors.Blue, rotation = 20f, corner = 3.dp),
    Papel(0.12f, 0.82f, 10.dp, 10.dp, RuletaColors.Teal),
    Papel(0.52f, 0.93f, 9.dp, 9.dp, RuletaColors.White),
    Papel(0.70f, 0.88f, 14.dp, 5.dp, RuletaColors.Orange, rotation = -25f),
    Papel(0.96f, 0.70f, 10.dp, 10.dp, RuletaColors.Violet, rotation = 40f, corner = 3.dp),
    Papel(0.28f, 0.28f, 8.dp, 8.dp, RuletaColors.Yellow),
)

/**
 * El confeti de fondo de la sección de la ruleta.
 *
 * Decorativo y nada más: no lleva descripción porque no hay nada que describir, y va
 * por detrás del contenido para no comerse ningún toque.
 */
@Composable
fun ConfetiRuleta(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val ancho = maxWidth
        val alto = maxHeight

        Papeles.forEach { p ->
            Box(
                Modifier
                    .offset(x = ancho * p.fx, y = alto * p.fy)
                    .size(p.width, p.height)
                    .graphicsLayer { rotationZ = p.rotation }
                    .clip(RoundedCornerShape(p.corner ?: (minOf(p.width, p.height) / 2)))
                    .background(p.color),
            )
        }
    }
}
