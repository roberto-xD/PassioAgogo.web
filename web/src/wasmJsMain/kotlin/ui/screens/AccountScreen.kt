package ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import ui.components.ContentScreen
import ui.components.FormField
import ui.components.Paragraph
import ui.components.SectionTitle
import ui.theme.PassionTheme
import viewmodel.AuthUiState

@Composable
fun AccountScreen(
    state: AuthUiState,
    onSignOut: () -> Unit,
    onGoToLogin: () -> Unit,
    onGoToRequests: () -> Unit = {},
    onNombreChange: (String) -> Unit = {},
    onTelefonoChange: (String) -> Unit = {},
    onSavePerfil: () -> Unit = {},
) {
    ContentScreen(title = "Mi cuenta") {
        if (!state.isAuthenticated) {
            Paragraph("No has iniciado sesión.")
            Spacer(Modifier.height(PassionTheme.spacing.s3))
            Button(onClick = onGoToLogin, shape = MaterialTheme.shapes.extraLarge) {
                Text("Iniciar sesión", style = MaterialTheme.typography.labelLarge)
            }
            return@ContentScreen
        }

        SectionTitle("Datos de acceso")
        Paragraph("Correo: ${state.accountEmail.ifBlank { "—" }}")

        if (state.profile == null) {
            Paragraph(
                "No pudimos cargar los datos de tu perfil. Vuelve a intentarlo más tarde."
            )
        } else {
            SectionTitle("Tus datos")
            Paragraph("Así te llamamos y por aquí te contactamos cuando pides algo.")

            FormField(
                value = state.perfilNombre,
                onValueChange = onNombreChange,
                label = "Nombre",
            )
            FormField(
                value = state.perfilTelefono,
                onValueChange = onTelefonoChange,
                label = "Teléfono",
                keyboardType = KeyboardType.Phone,
                supportingText = "Lo usamos para cerrar tus pedidos. Puedes dejarlo vacío.",
            )

            // El aviso va antes del botón: después quedaría debajo del siguiente bloque
            // y se leería como si perteneciera a otra cosa.
            state.perfilError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = PassionTheme.spacing.s2),
                )
            }
            state.perfilMensaje?.let {
                Text(
                    text = it,
                    color = PassionTheme.semantics.onBackgroundMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = PassionTheme.spacing.s2),
                )
            }

            Spacer(Modifier.height(PassionTheme.spacing.s3))
            Button(
                onClick = onSavePerfil,
                // Sin cambios no hay nada que guardar, y pulsarlo igual haría dudar de
                // si se guardó algo.
                enabled = state.perfilModificado && !state.guardandoPerfil,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = if (state.guardandoPerfil) "Guardando…" else "Guardar cambios",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(Modifier.height(PassionTheme.spacing.s6))
        Button(onClick = onGoToRequests, shape = MaterialTheme.shapes.extraLarge) {
            Text("Historial de solicitudes", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(PassionTheme.spacing.s3))
        Button(
            onClick = onSignOut,
            enabled = !state.isSubmitting,
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text("Cerrar sesión", style = MaterialTheme.typography.labelLarge)
        }
    }
}
