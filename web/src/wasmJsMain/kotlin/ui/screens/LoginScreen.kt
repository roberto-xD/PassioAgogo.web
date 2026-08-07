package ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ui.components.ContentScreen
import ui.components.FormField
import ui.components.Paragraph
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

    ContentScreen(title = if (isSignUp) "Crear cuenta" else "Iniciar sesión") {
        Paragraph(
            if (isSignUp) "Regístrate para gestionar tus datos y tus pedidos."
            else "Accede con tu correo y contraseña."
        )
        Spacer(Modifier.height(8.dp))

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
                color = Color(0xFFFF8A9B),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        state.infoMessage?.let {
            Text(
                text = it,
                color = Color(0xFF7BE0A5),
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
            if (state.isSubmitting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Un momento…", modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Text(
                    text = if (isSignUp) "Crear cuenta" else "Entrar",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isSignUp) "¿Ya tienes cuenta?" else "¿Aún no tienes cuenta?",
                color = Color(0xFFC9BEF0),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = if (isSignUp) "Inicia sesión" else "Crear una",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable {
                        onSwitchMode(if (isSignUp) AuthMode.SignIn else AuthMode.SignUp)
                    },
            )
        }
    }
}
