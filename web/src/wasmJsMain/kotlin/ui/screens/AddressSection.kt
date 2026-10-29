package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import ui.Chip
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
    /** Nulo cuando el bloque ya va dentro de algo con su propio título, como el carrito. */
    titulo: String? = "Dirección de envío",
    /** Borrar la dirección es cosa de «Mi cuenta»: en el carrito solo se captura. */
    conBorrar: Boolean = true,
) {
    titulo?.let { SectionTitle(it) }

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

    // El código postal va antes que la colonia porque la decide: de él cuelgan ciudad,
    // estado y la lista de colonias que se ofrece debajo.
    FormField(
        value = d.cp,
        onValueChange = { v ->
            val soloDigitos = v.filter { c -> c.isDigit() }.take(5)
            onEditar { it.copy(cp = soloDigitos) }
        },
        label = "Código postal",
        keyboardType = KeyboardType.Number,
        supportingText = when {
            state.buscandoCp -> "Buscando…"
            state.cpResuelto -> "${d.ciudad}, ${d.estado}"
            d.cp.length == 5 -> "No reconocimos este código. Escribe ciudad y estado a mano."
            else -> null
        },
    )

    // Resueltos por el catálogo de Envia, no se piden: son exactamente lo que la
    // paquetería espera leer, y un «CDMX» escrito a mano rompe la guía sin avisar.
    if (!state.cpResuelto) {
        FormField(d.ciudad, { v -> onEditar { it.copy(ciudad = v) } }, "Ciudad")
        FormField(d.estado, { v -> onEditar { it.copy(estado = v) } }, "Estado")
    }

    FormField(d.colonia, { v -> onEditar { it.copy(colonia = v) } }, "Colonia")
    // Se ofrecen, no se imponen: el campo sigue siendo de texto por si la colonia de
    // alguien no está en la lista, que pasa con los fraccionamientos nuevos.
    if (state.colonias.isNotEmpty()) ColoniaSugerencias(
        colonias = state.colonias,
        elegida = d.colonia,
        onElegir = { v -> onEditar { it.copy(colonia = v) } },
    )

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
        if (state.hayGuardada && conBorrar) {
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

/** Las colonias del código postal, para elegir con un toque en vez de teclearlas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColoniaSugerencias(
    colonias: List<String>,
    elegida: String,
    onElegir: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
        verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s2),
    ) {
        colonias.forEach { colonia ->
            Chip(
                label = colonia,
                selected = colonia.equals(elegida, ignoreCase = true),
                secondLevel = true,
                onClick = { onElegir(colonia) },
            )
        }
    }
}

/**
 * La dirección guardada, de solo lectura.
 *
 * Se parte en dos líneas en lugar de usar [Direccion.resumen] porque aquí se va a
 * comprobar antes de enviar un pedido: un renglón corrido invita a darlo por bueno de un
 * vistazo, y lo que se quiere es que se lea.
 */
@Composable
fun DireccionTexto(d: Direccion) {
    val calle = listOf(d.calle, d.interior.takeIf { it.isNotBlank() }?.let { "int. $it" })
        .filterNotNull()
        .filter { it.isNotBlank() }
        .joinToString(", ")
    val lugar = listOf(
        d.colonia,
        listOf(d.cp, d.ciudad).filter { it.isNotBlank() }.joinToString(" "),
        d.estado,
    ).filter { it.isNotBlank() }.joinToString(", ")

    listOf(calle, lugar).filter { it.isNotBlank() }.forEach { linea ->
        Text(
            text = linea,
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s1),
        )
    }
    if (d.referencias.isNotBlank()) {
        Text(
            text = d.referencias,
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = PassionTheme.spacing.s1),
        )
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
