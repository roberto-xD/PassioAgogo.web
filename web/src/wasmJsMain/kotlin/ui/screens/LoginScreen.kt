package ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ui.components.ContentScreen
import ui.components.FormField
import ui.components.Paragraph
import ui.theme.PassionTheme
import viewmodel.AuthMode
import viewmodel.AuthUiState

@Composable
fun LoginScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNombreChange: (String) -> Unit,
    onSwitchMode: (AuthMode) -> Unit,
    onSubmit: () -> Unit,
) {
    val isSignUp = state.mode == AuthMode.SignUp
    val semantics = PassionTheme.semantics

    ContentScreen(title = if (isSignUp) "Crear cuenta" else "Iniciar sesión") {
        Paragraph(
            if (isSignUp) "Regístrate para gestionar tus datos y tus pedidos."
            else "Accede con tu correo y contraseña."
        )
        Spacer(Modifier.height(PassionTheme.spacing.s2))

        if (isSignUp) {
            FormField(
                value = state.nombre,
                onValueChange = onNombreChange,
                label = "Nombre",
            )
        }
        FormField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Correo electrónico",
            keyboardType = KeyboardType.Email,
        )
        FormField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = "Contraseña",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            supportingText = if (isSignUp) "Mínimo 6 caracteres." else null,
        )

        state.errorMessage?.let {
            Text(
                text = it,
                color = semantics.danger,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            )
        }
        state.infoMessage?.let {
            Text(
                text = it,
                color = semantics.success,
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
            if (state.isSubmitting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Un momento…",
                        modifier = Modifier.padding(start = PassionTheme.spacing.s2),
                    )
                }
            } else {
                Text(
                    text = if (isSignUp) "Crear cuenta" else "Entrar",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(Modifier.height(PassionTheme.spacing.s5))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isSignUp) "¿Ya tienes cuenta?" else "¿Aún no tienes cuenta?",
                color = semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = if (isSignUp) "Inicia sesión" else "Crear una",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(start = PassionTheme.spacing.s1)
                    .clickable {
                        onSwitchMode(if (isSignUp) AuthMode.SignIn else AuthMode.SignUp)
                    },
            )
        }
    }
}
