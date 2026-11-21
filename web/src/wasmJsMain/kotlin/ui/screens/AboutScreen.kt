package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import network.WhatsAppConfig
import ui.components.copyToClipboard
import ui.components.openInNewTab
import ui.content.DocumentoScreen
import ui.content.Textos
import ui.theme.PassionTheme

/** Debajo de este ancho los datos de contacto se apilan. */
private val TWO_COLUMN_MIN_WIDTH = 560.dp

/** Teléfono de la tienda tal y como se muestra; el enlace usa el número configurado. */
private const val TELEFONO = "55 1387 8451"

private const val CORREO = "contacto@passionagogo.com"

/** Cuánto se queda el aviso de copiado antes de volver a mostrar el correo. */
private const val AVISO_MS = 2_000L

/**
 * Nosotros.
 *
 * El texto vive en `contenido/nosotros.md`; ver [TermsScreen] para cómo se
 * genera. Lo único que no sale de ahí son los datos de contacto del final: en
 * la página web son texto, pero aquí son botones —el correo se copia, el
 * teléfono abre WhatsApp—, así que en el Markdown van marcados `{solo-web}` y
 * aquí los pone [ContactRow].
 */
@Composable
fun AboutScreen() = DocumentoScreen(Textos.nosotros) { ContactRow() }

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
 * El correo se copia al portapapeles y el teléfono abre WhatsApp con la duda ya
 * escrita, igual que «Me interesa» en la ficha de producto. Si el número no está
 * configurado se queda como texto: mejor un dato inerte que un enlace que no
 * lleva a ninguna parte.
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
 * El aviso dice lo que de verdad pasó: si el navegador bloquea el portapapeles se
 * avisa del fallo en lugar de fingir que se copió, para que nadie se vaya con un
 * pegado vacío.
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
