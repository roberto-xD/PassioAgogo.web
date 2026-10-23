package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import network.Direccion
import ui.components.FormField
import ui.components.Paragraph
import ui.components.SectionTitle
import ui.theme.PassionTheme
import viewmodel.AddressUiState

/**
 * Captura de la dirección, cifrada antes de salir del navegador.
 *
 * Se le dice a la persona que va cifrada y qué significa eso, porque es la diferencia
 * entre pedirle su domicilio a secas y pedírselo explicando quién podrá verlo.
 */
@Composable
fun DireccionSection(
    state: AddressUiState,
    onEditar: ((Direccion) -> Direccion) -> Unit,
    onGuardar: () -> Unit,
    onBorrar: () -> Unit,
) {
    SectionTitle("Dirección de envío")

    if (state.cargando) {
        Paragraph("Cargando…")
        return
    }

    Paragraph(
        "Se guarda cifrada: solo tú y la tienda pueden leerla. Ni siquiera es legible " +
            "directamente en nuestra base de datos."
    )

    // Sobre guardado que esta pestaña no puede abrir. No se distingue el motivo porque
    // distinguirlo no ayuda a quien la escribió y sí a quien prueba contraseñas.
    if (state.bloqueada) {
        Aviso(
            "No podemos abrir tu dirección guardada en esta sesión. Si cambiaste tu " +
                "contraseña, la clave anterior ya no sirve: escríbela de nuevo y se " +
                "volverá a cifrar.",
        )
    }

    val d = state.direccion
    FormField(d.calle, { v -> onEditar { it.copy(calle = v) } }, "Calle y número")
    FormField(d.interior, { v -> onEditar { it.copy(interior = v) } }, "Interior (opcional)")
    FormField(d.colonia, { v -> onEditar { it.copy(colonia = v) } }, "Colonia")
    FormField(
        value = d.cp,
        onValueChange = { v ->
            val soloDigitos = v.filter { c -> c.isDigit() }.take(5)
            onEditar { it.copy(cp = soloDigitos) }
        },
        label = "Código postal",
        keyboardType = KeyboardType.Number,
    )
    FormField(d.ciudad, { v -> onEditar { it.copy(ciudad = v) } }, "Ciudad")
    FormField(d.estado, { v -> onEditar { it.copy(estado = v) } }, "Estado")
    FormField(
        value = d.referencias,
        onValueChange = { v -> onEditar { it.copy(referencias = v) } },
        label = "Referencias",
        singleLine = false,
        supportingText = "Entre qué calles, color de la fachada, portón… lo que ayude a dar con ella.",
    )

    state.error?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        )
    }
    state.mensaje?.let {
        Text(
            text = it,
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        )
    }

    Spacer(Modifier.height(PassionTheme.spacing.s3))
    Row {
        Button(
            onClick = onGuardar,
            enabled = state.puedeGuardar,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(
                text = if (state.guardando) "Cifrando…" else "Guardar dirección",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (state.hayGuardada) {
            Spacer(Modifier.height(0.dp))
            Button(
                onClick = onBorrar,
                enabled = !state.guardando,
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.padding(start = PassionTheme.spacing.s2),
            ) {
                Text("Eliminar", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PassionTheme.spacing.s3)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(PassionTheme.spacing.s4),
    ) {
        Text(
            text = texto,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
