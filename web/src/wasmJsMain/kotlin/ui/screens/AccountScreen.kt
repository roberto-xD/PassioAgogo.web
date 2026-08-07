package ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.SectionTitle
import ui.theme.PassionTheme
import viewmodel.AuthUiState

@Composable
fun AccountScreen(
    state: AuthUiState,
    onSignOut: () -> Unit,
    onGoToLogin: () -> Unit,
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

        val profile = state.profile
        if (profile != null) {
            SectionTitle("Perfil")
            Paragraph("Nombre: ${profile.nombre ?: "—"}")
            Paragraph("Tipo de cuenta: ${etiquetaRol(profile.rol)}")
        } else {
            Paragraph(
                "No pudimos cargar los datos de tu perfil. Vuelve a intentarlo más tarde."
            )
        }

        Spacer(Modifier.height(PassionTheme.spacing.s5))
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

private fun etiquetaRol(rol: String?): String = when (rol) {
    "admin" -> "Administración"
    "vendedor" -> "Ventas"
    "cliente" -> "Cliente"
    else -> "—"
}
