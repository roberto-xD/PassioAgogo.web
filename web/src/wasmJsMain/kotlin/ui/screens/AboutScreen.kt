package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import network.WhatsAppConfig
import ui.components.copyToClipboard
import ui.components.openInNewTab
import ui.content.DocumentoScreen
import ui.content.Textos
import ui.theme.PassionTheme

/** Alto reservado bajo el documento: el botón flotante más su margen. */
private val ALTO_DEL_BOTON = 88.dp

/** Debajo de este ancho los datos de contacto se apilan. */
private val TWO_COLUMN_MIN_WIDTH = 560.dp

/** Teléfono de la tienda tal y como se muestra; el enlace usa el número configurado. */
private const val TELEFONO = "55 1387 8451"

private const val CORREO = "contacto@passionagogo.com"

/** Cuánto se queda el aviso de copiado antes de volver a mostrar el correo. */
private const val AVISO_MS = 2_000L

/** Las dos caras de Nosotros. */
enum class PestanaNosotros(val rotulo: String) {
    Conocenos("Conócenos"),
    Ayuda("Ayuda"),
}

/**
 * Nosotros: quiénes somos y las preguntas frecuentes, en pestañas.
 *
 * Recoge tres entradas que antes ocupaban su propio sitio en la barra. Conócenos y
 * Ayuda son las dos pestañas; escribirnos no es una tercera porque no es un sitio al
 * que se va, es algo que se hace sin perder de vista lo que estabas leyendo: de ahí el
 * botón flotante y el diálogo.
 *
 * Las tres rutas siguen vivas y distintas —`#/nosotros`, `#/ayuda`, `#/contacto`—, cada
 * una abriendo esta pantalla en el estado que le toca. Importa: el pie y las páginas
 * estáticas de `sitio/` enlazan a las tres, y una URL que deja de responder es un
 * enlace roto en Google.
 *
 * El texto de las dos pestañas sale de `contenido/`, igual que las páginas de `sitio/`.
 *
 * @param dialogoContacto el formulario, como ranura, para que el ViewModel se quede en
 *   quien llama y esta pantalla no tenga que arrastrar seis parámetros que no usa.
 */
@Composable
fun AboutScreen(
    pestanaInicial: PestanaNosotros = PestanaNosotros.Conocenos,
    abrirContacto: Boolean = false,
    dialogoContacto: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    // Con clave: llegar por `#/ayuda` teniendo abierta `#/nosotros` tiene que cambiar de
    // pestaña. Sin ella, la pantalla ya está compuesta y el estado inicial se ignoraría.
    var pestana by remember(pestanaInicial) { mutableStateOf(pestanaInicial) }
    var contactoAbierto by remember(abrirContacto) { mutableStateOf(abrirContacto) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Pestanas(
                actual = pestana,
                onSelect = { pestana = it },
            )
            // El documento trae su propio desplazamiento, así que las pestañas se quedan
            // fijas arriba en lugar de irse con el texto.
            Box(modifier = Modifier.weight(1f)) {
                when (pestana) {
                    PestanaNosotros.Conocenos -> DocumentoScreen(Textos.nosotros) {
                        ContactRow()
                        HuecoDelBoton()
                    }
                    PestanaNosotros.Ayuda -> DocumentoScreen(Textos.preguntasFrecuentes) {
                        HuecoDelBoton()
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { contactoAbierto = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(PassionTheme.spacing.s5),
        ) {
            Text("Escríbenos", style = MaterialTheme.typography.labelLarge)
        }
    }

    if (contactoAbierto) {
        dialogoContacto { contactoAbierto = false }
    }
}

/**
 * Aire al final del documento, para que el botón flotante no se coma la última línea.
 *
 * El botón se dibuja encima del texto, así que sin esto el final de «Conócenos» —que
 * es justo donde están los datos de contacto— quedaría medio tapado por él.
 */
@Composable
private fun HuecoDelBoton() {
    Spacer(Modifier.height(ALTO_DEL_BOTON))
}

@Composable
private fun Pestanas(actual: PestanaNosotros, onSelect: (PestanaNosotros) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s5),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .background(PassionTheme.semantics.overlayWeak)
                .padding(PassionTheme.spacing.s1),
            horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s1),
        ) {
            PestanaNosotros.entries.forEach { entrada ->
                Pestana(
                    rotulo = entrada.rotulo,
                    seleccionada = entrada == actual,
                    onClick = { onSelect(entrada) },
                )
            }
        }
    }
}

@Composable
private fun Pestana(rotulo: String, seleccionada: Boolean, onClick: () -> Unit) {
    Text(
        text = rotulo,
        color = if (seleccionada) MaterialTheme.colorScheme.onPrimary
        else PassionTheme.semantics.onBackgroundMuted,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (seleccionada) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                if (seleccionada) MaterialTheme.colorScheme.primary
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = PassionTheme.spacing.s5,
                vertical = PassionTheme.spacing.s2,
            ),
    )
}

@Composable
private fun ContactRow() {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s2),
    ) {
        val enLinea = maxWidth >= TWO_COLUMN_MIN_WIDTH

        if (enLinea) {
            Row(horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2)) {
                ContactPills()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2)) {
                ContactPills()
            }
        }
    }
}

/**
 * Los tres datos de contacto.
 *
 * El correo se copia al portapapeles y el teléfono abre WhatsApp con la duda ya escrita,
 * igual que «Me interesa» en la ficha de producto. Si el número no está configurado se
 * queda como texto: mejor un dato inerte que un enlace que no lleva a ninguna parte.
 */
@Composable
private fun ContactPills() {
    EmailPill()
    ContactPill(
        texto = TELEFONO,
        onClick = if (WhatsAppConfig.isConfigured) {
            { openInNewTab(WhatsAppConfig.enlaceParaDuda()) }
        } else {
            null
        },
    )
    ContactPill("Ciudad de México")
}

/**
 * El correo, que se copia al pulsarlo.
 *
 * El aviso dice lo que de verdad pasó: si el navegador bloquea el portapapeles se avisa
 * del fallo en lugar de fingir que se copió, para que nadie se vaya con un pegado vacío.
 */
@Composable
private fun EmailPill() {
    var aviso by remember { mutableStateOf("") }

    LaunchedEffect(aviso) {
        if (aviso.isNotBlank()) {
            delay(AVISO_MS)
            aviso = ""
        }
    }

    ContactPill(
        texto = aviso.ifBlank { CORREO },
        onClick = {
            aviso = if (copyToClipboard(CORREO)) "¡Copiado!" else "No se pudo copiar"
        },
    )
}

@Composable
private fun ContactPill(texto: String, onClick: (() -> Unit)? = null) {
    val esEnlace = onClick != null
    Text(
        text = texto,
        color = if (esEnlace) MaterialTheme.colorScheme.primary
        else PassionTheme.semantics.onBackgroundMuted,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (esEnlace) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(PassionTheme.semantics.overlayWeak)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                horizontal = PassionTheme.spacing.s3,
                vertical = PassionTheme.spacing.s2,
            ),
    )
}
