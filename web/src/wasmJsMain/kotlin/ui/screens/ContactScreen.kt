package ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ui.components.ContentScreen
import ui.components.FormField
import ui.components.Paragraph
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
        Spacer(Modifier.height(12.dp))

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
                color = Color(0xFFFF8A9B),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6C5CE7),
                contentColor = Color.White,
                disabledContainerColor = Color(0x336C5CE7),
                disabledContentColor = Color(0x99FFFFFF),
            ),
        ) {
            if (state.isSending) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Enviando…", modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Text("Enviar mensaje", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Este sitio está protegido por Cloudflare Turnstile para evitar el envío " +
                "automatizado de mensajes.",
            color = Color(0xFF8F84B8),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SentConfirmation(onReset: () -> Unit) {
    Text(
        text = "¡Mensaje enviado!",
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Paragraph("Gracias por escribirnos. Te responderemos al correo que nos dejaste.")
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onReset,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6C5CE7),
            contentColor = Color.White,
        ),
    ) {
        Text("Enviar otro mensaje")
    }
}
