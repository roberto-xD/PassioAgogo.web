package ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import ui.theme.PassionTheme

/**
 * Campo de texto de los formularios de la app.
 *
 * Los colores salen del esquema del tema: no hace falta declararlos aquí, y así el campo
 * se adapta solo a los modos claro y oscuro.
 */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    /**
     * Dejar que el teclado corrija lo que se escribe. **Apagado por omisión.**
     *
     * En el navegador móvil, la corrección automática y la barra de sugerencias
     * rompen el campo: al terminar una palabra se borra sola. Es un fallo conocido de
     * Compose en web (CMP-8585), no nuestro, y lo único que está en nuestra mano es no
     * pedirle al teclado que corrija. Aquí además se pierde poco: esto son nombres,
     * calles, colonias y códigos, justo lo que un corrector estropea aunque funcione.
     */
    autoCorregir: Boolean = false,
    /**
     * Apagado se sigue leyendo, pero no se escribe.
     *
     * Para datos que los pone el sistema y no la persona —la ciudad que resuelve el
     * código postal—. Se deja a la vista en vez de esconderlo: que un campo del
     * formulario desaparezca hace dudar de si se mandó.
     */
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = singleLine,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation =
            if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            autoCorrectEnabled = autoCorregir,
        ),
        shape = MaterialTheme.shapes.small,
        // Material apaga los campos al 38 % de opacidad, que es para lo que no se puede
        // usar todavía. Aquí es al revés: el dato está puesto y es correcto, solo que
        // no lo escribe la persona. Se lee como cualquier otro y lo único que baja de
        // tono es el borde, que es lo que dice que no se toca.
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledLabelColor = PassionTheme.semantics.onBackgroundMuted,
            disabledSupportingTextColor = PassionTheme.semantics.onBackgroundMuted,
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s2),
    )
}
