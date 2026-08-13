package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ui.theme.PassionTheme

/** Por debajo de este ancho no caben imagen e información una al lado de la otra. */
private val SIDE_BY_SIDE_MIN_WIDTH = 720.dp

/**
 * Armazón común de los diálogos de detalle (galería y producto).
 *
 * Resuelve lo que ambos comparten: ocupar un porcentaje de la ventana en lugar del ancho
 * por defecto de la plataforma, colocar el panel visual **al lado** de la información para
 * que se lea sin desplazarse, apilarlos en ventanas estrechas donde dos columnas quedarían
 * ilegibles, y el botón de cierre siempre visible.
 *
 * @param imagePane recibe el modificador con el tamaño que le corresponde en cada
 *   disposición, porque no es el mismo en horizontal que en vertical.
 */
@Composable
fun DetailDialogShell(
    onDismiss: () -> Unit,
    imagePane: @Composable (Modifier) -> Unit,
    infoPane: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 1100.dp)
                .fillMaxHeight(0.88f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    if (maxWidth >= SIDE_BY_SIDE_MIN_WIDTH) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            imagePane(
                                Modifier
                                    .weight(1.5f)
                                    .fillMaxHeight()
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(PassionTheme.spacing.s6),
                                content = infoPane,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            imagePane(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp, max = 420.dp)
                            )
                            Column(
                                modifier = Modifier.padding(PassionTheme.spacing.s6),
                                content = infoPane,
                            )
                        }
                    }
                }

                // Botón de cierre, siempre visible sobre la esquina superior derecha.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(PassionTheme.spacing.s3)
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "×",  // U+00D7: Poppins no trae U+2715 y saldria un cuadro vacio
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
