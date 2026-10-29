package ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ui.theme.PassionTheme

/** Cuánto tarda la hoja en subir. Más lento se siente pesado; más rápido, un salto. */
private const val ENTRADA_MS = 220

/** Tope de ancho: en pantalla grande una hoja de borde a borde deja renglones ilegibles. */
private val SHEET_MAX_WIDTH = 720.dp

/** Alto máximo de la hoja, en fracción de la ventana: siempre se ve algo de fondo detrás. */
private const val ALTO_MAXIMO = 0.85f

/**
 * Hoja inferior: contenido que sube desde abajo sobre la pantalla actual.
 *
 * Se apoya en [Dialog] en vez de en `ModalBottomSheet` porque este proyecto ya tiene
 * verificado ese camino en wasm (los diálogos de detalle) y aquí no hace falta arrastrar
 * la hoja con el dedo: basta con abrirla y cerrarla.
 *
 * Se cierra al pulsar fuera, en el aspa o con la tecla de escape. El contenido se desplaza
 * solo, así que puede ser tan largo como haga falta.
 */
@Composable
fun BottomSheet(
    title: String,
    onDismiss: () -> Unit,
    /**
     * La hoja ocupa siempre el alto máximo.
     *
     * Es lo que quieren las bases de la ruleta, que son once cláusulas y llenan la
     * pantalla. Para un texto corto deja un palmo de blanco debajo y se lee como una
     * hoja a medio cargar: esos pasan `false` y la hoja mide lo que su contenido, sin
     * pasar nunca del máximo.
     */
    altoFijo: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = PassionTheme.spacing.s5,
        topEnd = PassionTheme.spacing.s5,
    )

    // Arranca abajo del todo y sube en el primer fotograma: así la hoja se ve llegar en
    // lugar de aparecer de golpe, que es lo que la distingue de un diálogo cualquiera.
    var dentro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { dentro = true }
    val fuera by animateFloatAsState(
        targetValue = if (dentro) 0f else 1f,
        animationSpec = tween(ENTRADA_MS),
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // Pulsar fuera cierra. Sin indicación: no es un botón, no debe parpadear.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Sin alto fijo la hoja crece con su texto, pero nunca más allá del tope:
            // una hoja más alta que la pantalla no se podría cerrar tocando fuera.
            val alto = if (altoFijo) {
                Modifier.fillMaxHeight(ALTO_MAXIMO)
            } else {
                Modifier.heightIn(max = maxHeight * ALTO_MAXIMO)
            }

            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .widthIn(max = SHEET_MAX_WIDTH)
                    .fillMaxWidth()
                    .then(alto)
                    .graphicsLayer { translationY = fuera * size.height }
                    // Traga la pulsación: dentro de la hoja tocar no cierra nada.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = PassionTheme.spacing.s3),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Agarradera: aunque no se arrastre, es lo que hace que se lea
                        // como una hoja y no como un panel pegado abajo.
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = PassionTheme.spacing.s6,
                                end = PassionTheme.spacing.s3,
                                top = PassionTheme.spacing.s4,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            color = PassionTheme.semantics.onBackgroundStrong,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "×",  // U+00D7: Poppins no trae U+2715 y saldria un cuadro
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = PassionTheme.spacing.s6,
                                vertical = PassionTheme.spacing.s4,
                            ),
                        content = content,
                    )
                }
            }
        }
    }
}
