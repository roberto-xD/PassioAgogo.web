package ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ui.theme.PassionTheme

/** Lado de la pastilla que contiene la lupa. */
private val BADGE_SIZE = 34.dp

/**
 * Distintivo de «esto se puede acercar».
 *
 * Los gestos son invisibles: sin una pista, el doble toque para acercar lo descubre muy
 * poca gente. Va en la esquina inferior derecha, lejos de los puntos del carrusel.
 *
 * La lupa se dibuja a mano en lugar de usar un emoji o un icono de fuente: así se tiñe con
 * el color que le toque y no depende de que la fuente empaquetada traiga ese símbolo.
 */
@Composable
fun ZoomHint(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(PassionTheme.spacing.s3)
            .size(BADGE_SIZE)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .semantics { contentDescription = "Doble toque para acercar la imagen" },
        contentAlignment = Alignment.Center,
    ) {
        MagnifierPlus(color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Lupa con un signo de más dentro: el símbolo habitual de «acercar». */
@Composable
private fun MagnifierPlus(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val trazo = size.minDimension * 0.09f
        val radio = size.minDimension * 0.33f
        val centro = Offset(size.width * 0.42f, size.height * 0.42f)

        drawCircle(
            color = color,
            radius = radio,
            center = centro,
            style = Stroke(width = trazo),
        )

        // El mango sale del borde inferior derecho, en diagonal.
        val borde = radio * 0.7071f // radio · cos(45°): el punto del círculo a 45 grados
        drawLine(
            color = color,
            start = Offset(centro.x + borde, centro.y + borde),
            end = Offset(size.width * 0.94f, size.height * 0.94f),
            strokeWidth = trazo,
            cap = StrokeCap.Round,
        )

        val brazo = radio * 0.45f
        drawLine(
            color = color,
            start = Offset(centro.x - brazo, centro.y),
            end = Offset(centro.x + brazo, centro.y),
            strokeWidth = trazo,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centro.x, centro.y - brazo),
            end = Offset(centro.x, centro.y + brazo),
            strokeWidth = trazo,
            cap = StrokeCap.Round,
        )
    }
}
