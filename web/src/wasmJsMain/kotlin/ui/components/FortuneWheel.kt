package ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import ui.theme.RuletaColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Vueltas completas antes de empezar a frenar. Suficientes para no adivinar el destino. */
private const val VUELTAS = 5

/** Cuánto dura el frenado. Por debajo de esto no da tiempo a disfrutarlo. */
private const val FRENADO_MS = 3_500

/** Duración de cada vuelta mientras se espera la respuesta del servidor. */
private const val VUELTA_LIBRE_MS = 900

/** Dónde apunta la aguja, en grados: arriba del todo. */
private const val ANGULO_AGUJA = 270f

/** Parte del radio que ocupa el aro blanco del borde. */
private const val ANILLO_BLANCO = 0.075f

/** Bombillas repartidas por el aro. */
private const val BOMBILLAS = 12

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

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(PassionTheme.spacing.s4)
                .graphicsLayer { rotationZ = rotation.value },
        ) {
            val radio = size.minDimension / 2f
            // El aro blanco se come parte del radio: los gajos se dibujan por dentro
            // para que quede como un marco y no como un borde pegado.
            val anillo = radio * ANILLO_BLANCO
            val radioGajo = radio - anillo
            val esquina = Offset(center.x - radioGajo, center.y - radioGajo)
            val lado = Size(radioGajo * 2f, radioGajo * 2f)

            prizes.forEachIndexed { indice, _ ->
                drawArc(
                    color = RuletaColors.Gajos[indice % RuletaColors.Gajos.size],
                    startAngle = indice * sector,
                    sweepAngle = sector,
                    useCenter = true,
                    topLeft = esquina,
                    size = lado,
                )
            }

            // El aro blanco, por encima del canto de los gajos.
            drawCircle(
                color = RuletaColors.White,
                radius = radio - anillo / 2f,
                center = center,
                style = Stroke(width = anillo),
            )

            // Las bombillas van justo en la junta: medio círculo cae sobre el aro
            // blanco y desaparece, y el medio que se ve recorta el gajo. Así se leen
            // como muescas del aro y no como lunares sueltos.
            repeat(BOMBILLAS) { i ->
                val angulo = i * (360f / BOMBILLAS) * PI.toFloat() / 180f
                drawCircle(
                    color = RuletaColors.White,
                    radius = radio * 0.022f,
                    center = Offset(
                        center.x + radioGajo * cos(angulo),
                        center.y + radioGajo * sin(angulo),
                    ),
                )
            }

            prizes.forEachIndexed { indice, premio ->
                val layout = measurer.measure(
                    text = AnnotatedString(premio.rotulo),
                    style = TextStyle(
                        color = RuletaColors.TextoGajos[indice % RuletaColors.TextoGajos.size],
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
                                center.y + radioGajo * 0.84f - layout.size.height
                            } else {
                                center.y - radioGajo * 0.84f
                            },
                        ),
                    )
                }
            }
        }

        // Aro exterior, apenas más claro que el fondo: separa la ruleta de la sección
        // sin meter otro color. No gira, así que va fuera del lienzo de arriba.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radio = size.minDimension / 2f
            drawCircle(
                color = RuletaColors.FondoAro,
                radius = radio - 2.dp.toPx(),
                center = center,
                style = Stroke(width = 4.dp.toPx()),
            )
        }

        // El puntero no gira: marca el sector premiado desde fuera. Es el mismo del
        // banner, para que la ruleta de la portada y esta sean la misma ruleta.
        WheelPointer(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-6).dp)
                .size(34.dp, 40.dp),
        )

        Button(
            onClick = onSpin,
            enabled = canSpin,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = RuletaColors.White,
                contentColor = RuletaColors.Fondo,
                disabledContainerColor = RuletaColors.White,
                disabledContentColor = RuletaColors.Fondo.copy(alpha = 0.4f),
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .size(96.dp)
                .border(4.dp, RuletaColors.Yellow, CircleShape),
        ) {
            Text(
                text = if (spinning) "…" else "GIRAR",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
