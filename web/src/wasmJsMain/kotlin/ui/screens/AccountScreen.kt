package ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import ui.components.ContentScreen
import ui.components.FormField
import ui.components.Paragraph
import ui.components.SectionTitle
import ui.theme.PassionTheme
import network.Direccion
import ui.Chip
import ui.screens.DireccionSection
import viewmodel.AddressUiState
import viewmodel.AuthUiState
import viewmodel.CartUiState

@Composable
fun AccountScreen(
    authState: AuthUiState,
    cartState: CartUiState,
    onSignOut: () -> Unit,
    onGoToLogin: () -> Unit,
    onNombreChange: (String) -> Unit = {},
    onTelefonoChange: (String) -> Unit = {},
    onSavePerfil: () -> Unit = {},
    direccion: AddressUiState = AddressUiState(),
    onEditarDireccion: ((Direccion) -> Direccion) -> Unit = {},
    onGuardarDireccion: () -> Unit = {},
    onBorrarDireccion: () -> Unit = {},
    onLoad: () -> Unit,
    onGoToCatalog: () -> Unit,
    onGoToCart: () -> Unit = {},
) {
    val currentContent = remember { mutableStateOf("Main") }

    // El historial se pide al abrir su pestaña, no en cada composición: `loadSolicitudes`
    // cambia el estado, lo que vuelve a componer, lo que volvería a pedirlo. Que la clave
    // sea la pestaña además refresca la lista al volver a ella, que es cuando interesa.
    LaunchedEffect(currentContent.value, authState.isAuthenticated) {
        if (authState.isAuthenticated && currentContent.value == "History") onLoad()
    }

    ContentScreen(title = "Mi cuenta") {
        if (!authState.isAuthenticated) {
            Paragraph("No has iniciado sesión.")
            Spacer(Modifier.height(PassionTheme.spacing.s3))
            Button(onClick = onGoToLogin, shape = MaterialTheme.shapes.extraLarge) {
                Text("Iniciar sesión", style = MaterialTheme.typography.labelLarge)
            }
            return@ContentScreen
        }
        AccountChips(
            selected = currentContent.value,
            onSelect = {
                currentContent.value = it
            }
        )
        when (currentContent.value) {
            "Main" -> AccountMainContent(
                authState = authState,
                direccion = direccion,
                onEditarDireccion = onEditarDireccion,
                onGuardarDireccion = onGuardarDireccion,
                onBorrarDireccion = onBorrarDireccion,
                onNombreChange = onNombreChange,
                onTelefonoChange = onTelefonoChange,
                onSavePerfil = onSavePerfil,
                onSignOut = onSignOut,
            )
            "History" -> AccountHistoryContent(
                state = cartState,
                onGoToCart = onGoToCart,
                onGoToCatalog = onGoToCatalog,
            )
        }
    }
}
@Composable
fun AccountMainContent(
    authState: AuthUiState,
    direccion: AddressUiState = AddressUiState(),
    onEditarDireccion: ((Direccion) -> Direccion) -> Unit = {},
    onGuardarDireccion: () -> Unit = {},
    onBorrarDireccion: () -> Unit = {},
    onNombreChange: (String) -> Unit,
    onTelefonoChange: (String) -> Unit,
    onSavePerfil: () -> Unit,
    onSignOut: () -> Unit,
){
    SectionTitle("Datos de acceso")
    Paragraph("Correo: ${authState.accountEmail.ifBlank { "—" }}")

    if (authState.profile == null) {
        Paragraph(
            "No pudimos cargar los datos de tu perfil. Vuelve a intentarlo más tarde."
        )
    } else {
        SectionTitle("Tus datos")
        Paragraph("Así te llamamos y por aquí te contactamos cuando pides algo.")

        FormField(
            value = authState.perfilNombre,
            onValueChange = onNombreChange,
            label = "Nombre",
        )
        FormField(
            value = authState.perfilTelefono,
            onValueChange = onTelefonoChange,
            label = "Teléfono",
            keyboardType = KeyboardType.Phone,
            supportingText = "Lo usamos para cerrar tus pedidos. Puedes dejarlo vacío.",
        )

        // El aviso va antes del botón: después quedaría debajo del siguiente bloque
        // y se leería como si perteneciera a otra cosa.
        authState.perfilError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            )
        }
        authState.perfilMensaje?.let {
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
            enabled = authState.perfilModificado && !authState.guardandoPerfil,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(
                text = if (authState.guardandoPerfil) "Guardando…" else "Guardar cambios",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }

    if (authState.profile != null) {
        Spacer(Modifier.height(PassionTheme.spacing.s4))
        DireccionSection(
            state = direccion,
            onEditar = onEditarDireccion,
            onGuardar = onGuardarDireccion,
            onBorrar = onBorrarDireccion,
        )
    }

    Spacer(Modifier.height(PassionTheme.spacing.s3))
    Button(
        onClick = onSignOut,
        enabled = !authState.isSubmitting,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text("Cerrar sesión", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AccountChips(
    selected: String = "Main",
    onSelect: (String) -> Unit,
){
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = PassionTheme.spacing.s4,
                vertical = PassionTheme.spacing.s2,
            ),
        horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ){
        Chip(label = "Mis Datos", selected = selected == "Main") {
            onSelect("Main")
        }
        Chip(label = "Historial de solicitudes", selected = selected == "History") {
            onSelect("History")
        }
    }
}