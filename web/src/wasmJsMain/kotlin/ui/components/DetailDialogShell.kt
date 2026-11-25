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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ui.theme.PassionTheme

/**
 * El avisador de la aplicación, accesible desde dentro de un diálogo.
 *
 * Un diálogo se dibuja en su propia capa, por encima del contenido: un snackbar puesto
 * en la pantalla de fondo queda tapado y no se ve. Compartiendo el estado, el diálogo
 * puede pintar su propio host y el aviso aparece donde sí se mira.
 */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState?> { null }

/** Por debajo de este ancho no caben imagen e información una al lado de la otra. */
private val SIDE_BY_SIDE_MIN_WIDTH = 720.dp

/** Ancho máximo del diálogo de una sola columna; más allá el texto se lee peor. */
private val FORM_DIALOG_MAX_WIDTH = 560.dp

/**
 * Parte de la ventana que puede ocupar de alto, como mucho.
 *
 * Relativo y no un número fijo: con un tope de 620 dp, en un móvil el formulario de
 * contacto se pasaba por poco y aparecía cortado a media frase nada más abrirse. Lo que
 * sobre de aquí sigue desplazándose, pero en un teléfono normal ya no sobra nada.
 */
private const val FORM_DIALOG_MAX_HEIGHT_FRACTION = 0.9f

/**
 * Armazón de un diálogo de una sola columna, con título.
 *
 * Hermano de [DetailDialogShell]: comparte el aspecto —la misma superficie redondeada,
 * el mismo botón de cierre en la esquina, el mismo avisador repintado dentro— pero sin
 * panel de imagen, porque lo que enseña es un formulario y no una fotografía. Se alza
 * sobre el contenido en lugar de ocupar una pantalla entera, que es lo que hace el
 * formulario de contacto desde que vive dentro de Nosotros.
 *
 * El alto no se fija: crece con el contenido hasta donde cabe, y a partir de ahí se
 * desplaza. Un formulario de tres campos en un diálogo de alto fijo deja medio diálogo
 * vacío.
 */
@Composable
fun FormDialogShell(
    titulo: String,
    onDismiss: () -> Unit,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Mide la ventana para poder acotar el alto en proporción a ella.
        BoxWithConstraints {
            val altoMaximo = maxHeight * FORM_DIALOG_MAX_HEIGHT_FRACTION

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                // El tope va **antes** del porcentaje, no después: `fillMaxWidth` fija el
                // ancho mínimo además del máximo, así que un `widthIn` detrás no puede
                // recortarlo y el diálogo salía a 92 % de la ventana. Puesto delante,
                // acota la restricción que recibe el porcentaje: 92 % de 560 en una
                // pantalla ancha, y 92 % de la pantalla en un móvil, que es lo buscado.
                modifier = Modifier
                    .widthIn(max = FORM_DIALOG_MAX_WIDTH)
                    .fillMaxWidth(0.92f),
            ) {
                Box {
                    Column(
                        modifier = Modifier
                            .heightIn(max = altoMaximo)
                            .verticalScroll(rememberScrollState())
                            .padding(PassionTheme.spacing.s6),
                    ) {
                        Text(
                            text = titulo,
                            color = PassionTheme.semantics.onBackgroundStrong,
                            style = MaterialTheme.typography.headlineSmall,
                            // Hueco a la derecha para no quedar debajo del cierre.
                            modifier = Modifier.padding(
                                end = PassionTheme.spacing.s8,
                                bottom = PassionTheme.spacing.s3,
                            ),
                        )
                        contenido()
                    }

                    LocalSnackbarHostState.current?.let { host ->
                        SnackbarHost(
                            hostState = host,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(PassionTheme.spacing.s4),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(PassionTheme.spacing.s3)
                            .size(36.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            )
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            // U+00D7: Poppins no trae U+2715 y saldria un cuadro vacio
                            text = "×",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

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

                // El aviso de la aplicación, repintado dentro del diálogo porque en la
                // pantalla de fondo quedaría debajo de esta capa.
                LocalSnackbarHostState.current?.let { host ->
                    SnackbarHost(
                        hostState = host,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(PassionTheme.spacing.s4),
                    )
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
