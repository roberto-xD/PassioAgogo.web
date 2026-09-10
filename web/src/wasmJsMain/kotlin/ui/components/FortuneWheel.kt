package ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import models.WheelPrize
import ui.theme.PassionTheme
import kotlin.math.floor

/** Vueltas completas antes de empezar a frenar. Suficientes para no adivinar el destino. */
private const val VUELTAS = 5

/** Cuánto dura el frenado. Por debajo de esto no da tiempo a disfrutarlo. */
private const val FRENADO_MS = 3_500

/** Duración de cada vuelta mientras se espera la respuesta del servidor. */
private const val VUELTA_LIBRE_MS = 900

/** Dónde apunta la aguja, en grados: arriba del todo. */
private const val ANGULO_AGUJA = 270f

/**
 * Ruleta de premios con giro animado.
 *
 * **El premio no se decide aquí.** Llega ya decidido del servidor en [landingIndex] y
 * esta ruleta solo se encarga de frenar en el sector que corresponde. Si el sorteo
 * ocurriera en el navegador, bastaría recargar para volver a tirar.
 *
 * Mientras la respuesta viaja, la ruleta gira a velocidad constante: arrancar en el acto
 * y frenar cuando se sabe el destino se siente natural, mientras que esperar quieto a que
 * conteste el servidor se lee como que el botón no funcionó.
 *
 * @param landingIndex sector donde frenar, o `null` si aún no se sabe.
 * @param onLanded se llama una vez cuando la ruleta termina de frenar.
 */
@Composable
fun FortuneWheel(
    prizes: List<WheelPrize>,
    spinning: Boolean,
    landingIndex: Int?,
    onSpin: () -> Unit,
    onLanded: () -> Unit,
    canSpin: Boolean,
    modifier: Modifier = Modifier,
) {
    if (prizes.isEmpty()) return

    val rotation = remember { Animatable(0f) }
    val avisarFrenado by rememberUpdatedState(onLanded)
    val sector = 360f / prizes.size

    LaunchedEffect(spinning, landingIndex) {
        if (!spinning) return@LaunchedEffect

        if (landingIndex == null) {
            // Giro libre, sin destino todavía. El efecto se reinicia en cuanto llega
            // `landingIndex`, cancelando este bucle a media vuelta.
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(VUELTA_LIBRE_MS, easing = LinearEasing),
                )
            }
        }

        // Ángulo que deja el centro del sector debajo de la aguja.
        val centroSector = landingIndex * sector + sector / 2f
        val alineado = ((ANGULO_AGUJA - centroSector) % 360f + 360f) % 360f
        // Se parte de la vuelta en curso para que el frenado siempre vaya hacia delante.
        val vueltaActual = floor(rotation.value / 360f) * 360f
        val objetivo = vueltaActual + VUELTAS * 360f + alineado

        rotation.animateTo(
            targetValue = objetivo,
            // Arranca a toda velocidad y desacelera: es como frena una ruleta de verdad,
            // y además disimula que el bucle anterior se cortó a mitad.
            animationSpec = tween(FRENADO_MS, easing = LinearOutSlowInEasing),
        )
        avisarFrenado()
    }

    BoxWithConstraints(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        val measurer = rememberTextMeasurer()
        val colorPar = MaterialTheme.colorScheme.primary
        val colorImpar = MaterialTheme.colorScheme.primaryContainer
        val textoPar = MaterialTheme.colorScheme.onPrimary
        val textoImpar = MaterialTheme.colorScheme.onPrimaryContainer
        val borde = MaterialTheme.colorScheme.surface

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(PassionTheme.spacing.s4)
                .graphicsLayer { rotationZ = rotation.value },
        ) {
            val radio = size.minDimension / 2f
            val esquina = Offset(center.x - radio, center.y - radio)
            val lado = Size(radio * 2f, radio * 2f)

            prizes.forEachIndexed { indice, premio ->
                drawArc(
                    color = if (indice % 2 == 0) colorPar else colorImpar,
                    startAngle = indice * sector,
                    sweepAngle = sector,
                    useCenter = true,
                    topLeft = esquina,
                    size = lado,
                )
                // Separador entre sectores: sin él, dos sectores del mismo color
                // consecutivos —número impar de premios— se leen como uno solo.
                drawArc(
                    color = borde,
                    startAngle = indice * sector,
                    sweepAngle = sector,
                    useCenter = true,
                    topLeft = esquina,
                    size = lado,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            prizes.forEachIndexed { indice, premio ->
                val layout = measurer.measure(
                    text = AnnotatedString(premio.rotulo),
                    style = TextStyle(
                        color = if (indice % 2 == 0) textoPar else textoImpar,
                        fontSize = (radio * 0.13f).toSp(),
                        fontWeight = FontWeight.Bold,
                    ),
                )

                // El texto se pone radial: se gira el lienzo hasta que el sector queda
                // arriba y se dibuja centrado sobre el radio.
                val centroSector = indice * sector + sector / 2f
                val giroBase = centroSector - ANGULO_AGUJA
                // Los sectores de la mitad de abajo quedarían con el rótulo boca abajo.
                // Se les da media vuelta más y se dibuja al otro lado del centro, que es
                // donde ha ido a parar su sector.
                val normalizado = ((giroBase % 360f) + 360f) % 360f
                val invertido = normalizado > 90f && normalizado < 270f

                rotate(degrees = if (invertido) giroBase + 180f else giroBase, pivot = center) {
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x = center.x - layout.size.width / 2f,
                            y = if (invertido) {
                                center.y + radio * 0.88f - layout.size.height
                            } else {
                                center.y - radio * 0.88f
                            },
                        ),
                    )
                }
            }

            // Aro exterior: le da borde a la ruleta y remata los sectores.
            drawCircle(
                color = colorPar,
                radius = radio - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 6.dp.toPx()),
            )
        }

        // La aguja no gira: marca el sector premiado desde fuera.
        Canvas(modifier = Modifier.fillMaxSize()) {
            aguja(color = colorPar, borde = borde)
        }

        Button(
            onClick = onSpin,
            enabled = canSpin,
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.size(96.dp),
        ) {
            Text(
                text = if (spinning) "…" else "GIRAR",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Triángulo apuntando hacia abajo, centrado en el borde superior. */
private fun DrawScope.aguja(color: Color, borde: Color) {
    val ancho = size.minDimension * 0.075f
    val alto = size.minDimension * 0.085f
    val cima = Offset(center.x, 0f)

    val camino = Path().apply {
        moveTo(cima.x - ancho / 2f, cima.y)
        lineTo(cima.x + ancho / 2f, cima.y)
        lineTo(cima.x, cima.y + alto)
        close()
    }
    drawPath(path = camino, color = borde, style = Stroke(width = 6f))
    drawPath(path = camino, color = color)
}
