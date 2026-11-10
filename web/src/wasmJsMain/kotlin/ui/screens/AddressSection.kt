package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import network.Direccion
import ui.components.FormField
import ui.components.Paragraph
import ui.components.PassionButton
import ui.components.SectionTitle
import ui.theme.PassionTheme
import viewmodel.AddressUiState
import viewmodel.DesbloqueoUi

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
    /** Hay sesión pero no hay clave en esta pestaña: ver [Desbloqueo]. */
    desbloqueo: DesbloqueoUi = DesbloqueoUi(),
    onDesbloquear: (String) -> Unit = {},
) {
    titulo?.let { SectionTitle(it) }

    if (state.cargando) {
        Paragraph("Cargando…")
        return
    }

    // Sesión abierta y sin clave en esta pestaña. Se le pide la contraseña **aquí**, no
    // se le manda a cerrar sesión y volver a entrar para acabar escribiendo la misma.
    //
    // Y el formulario no se enseña debajo a propósito: sin clave no hay forma de
    // guardar lo que escriba, y dejarle llenar siete campos para negarse al final es
    // justo lo que esta pantalla hacía antes.
    if (desbloqueo.haceFalta) {
        Desbloqueo(desbloqueo, onDesbloquear)
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
            state.cpResuelto -> lugarLegible(d.ciudad, d.estado)
            // No pudimos preguntar. El código puede estar bien, así que no se le dice
            // que está mal: se le dice que lo escriba él y se sigue adelante.
            state.cpSinServicio ->
                "No pudimos verificarlo ahora. Escribe ciudad y estado a mano."
            d.cp.length == 5 -> "No reconocimos este código. Escribe ciudad y estado a mano."
            else -> null
        },
    )

    // La colonia va pegada al código postal porque sale de él. Con lista, se elige de
    // un desplegable: así no hay forma de escribir una que no exista en ese código.
    if (state.colonias.isNotEmpty()) {
        ColoniaDesplegable(
            colonias = state.colonias,
            elegida = d.colonia,
            onElegir = { v -> onEditar { it.copy(colonia = v) } },
        )
    } else {
        // Sin lista —código no reconocido, o la consulta no contestó— se escribe. Un
        // desplegable vacío no dejaría completar la dirección.
        FormField(d.colonia, { v -> onEditar { it.copy(colonia = v) } }, "Colonia")
    }

    // Ciudad y estado se enseñan siempre, pero apagados cuando los resolvió el
    // catálogo: son exactamente lo que la paquetería espera leer, y un «CDMX» escrito
    // a mano rompe la guía sin avisar. Verlos apagados dice que ya están puestos;
    // esconderlos haría dudar de si se mandan.
    FormField(
        value = d.ciudad,
        onValueChange = { v -> onEditar { it.copy(ciudad = v) } },
        label = "Ciudad",
        enabled = !state.cpResuelto,
    )
    FormField(
        value = d.estado,
        onValueChange = { v -> onEditar { it.copy(estado = v) } },
        label = "Estado",
        enabled = !state.cpResuelto,
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

/**
 * «Ciudad de México, Ciudad de Mexico» no informa de nada: dice dos veces lo mismo, y la
 * segunda con una falta. Pasa en la capital, donde ciudad y estado se llaman igual.
 */
private fun lugarLegible(ciudad: String, estado: String): String {
    fun normal(s: String) = s.trim().lowercase()
        .let { t -> buildString { t.forEach { append(DESACENTUADO[it] ?: it) } } }

    return when {
        ciudad.isBlank() -> estado
        estado.isBlank() || normal(ciudad) == normal(estado) -> ciudad
        else -> "$ciudad, $estado"
    }
}

private val DESACENTUADO = mapOf(
    'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u',
)

/**
 * Las colonias del código postal, en un desplegable.
 *
 * Elegir en vez de escribir: de un código postal cuelgan unas pocas colonias concretas,
 * y teclearla a mano solo abre la puerta a escribirla distinto de como la conoce la
 * paquetería. El campo se ve como los demás pero no se escribe en él; se toca y se
 * elige.
 */
@Composable
private fun ColoniaDesplegable(
    colonias: List<String>,
    elegida: String,
    onElegir: (String) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // El campo apagado no recibe pulsaciones, así que las recoge la caja de
        // alrededor. Es lo que permite que se lea como un campo y actúe como un botón.
        FormField(
            value = elegida,
            onValueChange = {},
            label = if (elegida.isBlank()) "Colonia — toca para elegir" else "Colonia",
            enabled = false,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.small)
                .clickable { abierto = true },
        )

        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            colonias.forEach { colonia ->
                DropdownMenuItem(
                    text = { Text(colonia) },
                    onClick = {
                        onElegir(colonia)
                        abierto = false
                    },
                )
            }
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
        listOf(d.cp, lugarLegible(d.ciudad, d.estado)).filter { it.isNotBlank() }
            .joinToString(" "),
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

/**
 * Pedir la contraseña para recuperar la clave de cifrado.
 *
 * La clave vive en `sessionStorage` y muere con la pestaña, mientras la sesión sigue
 * viva en disco. Por eso esto aparece más de lo que parece: al abrir la tienda en una
 * pestaña nueva, y al entrar por el enlace de confirmación del correo, donde la
 * contraseña nunca llegó a pasar por aquí.
 *
 * Se explica por qué se pide. Un campo de contraseña que aparece solo, en mitad de una
 * pantalla donde ya habías iniciado sesión, es exactamente lo que enseñamos a la gente
 * a no rellenar.
 */
@Composable
private fun Desbloqueo(estado: DesbloqueoUi, onDesbloquear: (String) -> Unit) {
    var password by remember { mutableStateOf("") }

    Paragraph(
        "Tu dirección se guarda cifrada con una llave que sale de tu contraseña, y esa " +
            "llave no se queda guardada en ningún sitio. Escríbela otra vez para " +
            "abrirla en esta pestaña."
    )

    FormField(
        value = password,
        onValueChange = { password = it },
        label = "Tu contraseña",
        keyboardType = KeyboardType.Password,
        isPassword = true,
        enabled = !estado.trabajando,
        supportingText = estado.error,
    )

    Spacer(Modifier.height(PassionTheme.spacing.s3))
    PassionButton(
        texto = if (estado.trabajando) "Comprobando…" else "Desbloquear mi dirección",
        onClick = { onDesbloquear(password) },
        enabled = !estado.trabajando && password.isNotBlank(),
    )
}
