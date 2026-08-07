package ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.SectionTitle
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
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onGoToLogin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C5CE7),
                    contentColor = Color.White,
                ),
            ) {
                Text("Iniciar sesión", fontWeight = FontWeight.SemiBold)
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

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSignOut,
            enabled = !state.isSubmitting,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0x33FFFFFF),
                contentColor = Color.White,
            ),
        ) {
            Text("Cerrar sesión", fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun etiquetaRol(rol: String?): String = when (rol) {
    "admin" -> "Administración"
    "vendedor" -> "Ventas"
    "cliente" -> "Cliente"
    else -> "—"
}
