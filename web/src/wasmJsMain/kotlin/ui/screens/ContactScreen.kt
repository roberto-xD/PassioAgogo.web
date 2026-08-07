package ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ui.components.ContentScreen
import ui.components.FormField
import ui.components.Paragraph
import ui.theme.PassionTheme
import viewmodel.ContactUiState
import viewmodel.MAX_MENSAJE

@Composable
fun ContactScreen(
    state: ContactUiState,
    onNombreChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onMensajeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
) {
    ContentScreen(title = "Contacto") {
        if (state.sent) {
            SentConfirmation(onReset)
            return@ContentScreen
        }

        Paragraph(
            "¿Tienes dudas, sugerencias o quieres vender con nosotros? Escríbenos y te " +
                "respondemos lo antes posible."
        )
        Spacer(Modifier.height(PassionTheme.spacing.s3))

        FormField(
            value = state.nombre,
            onValueChange = onNombreChange,
            label = "Nombre",
        )
        FormField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Correo electrónico",
            keyboardType = KeyboardType.Email,
        )
        FormField(
            value = state.mensaje,
            onValueChange = onMensajeChange,
            label = "Mensaje",
            singleLine = false,
            modifier = Modifier.heightIn(min = 140.dp),
            supportingText = "${state.mensaje.length} / $MAX_MENSAJE",
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                color = PassionTheme.semantics.danger,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            )
        }

        Spacer(Modifier.height(PassionTheme.spacing.s4))
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            if (state.isSending) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Enviando…",
                        modifier = Modifier.padding(start = PassionTheme.spacing.s2),
                    )
                }
            } else {
                Text("Enviar mensaje", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(PassionTheme.spacing.s4))
        Text(
            text = "Este sitio está protegido por Cloudflare Turnstile para evitar el envío " +
                "automatizado de mensajes.",
            color = PassionTheme.semantics.onBackgroundSubtle,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SentConfirmation(onReset: () -> Unit) {
    Text(
        text = "¡Mensaje enviado!",
        color = PassionTheme.semantics.onBackgroundStrong,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Paragraph("Gracias por escribirnos. Te responderemos al correo que nos dejaste.")
    Spacer(Modifier.height(PassionTheme.spacing.s3))
    Button(onClick = onReset, shape = MaterialTheme.shapes.extraLarge) {
        Text("Enviar otro mensaje")
    }
}
