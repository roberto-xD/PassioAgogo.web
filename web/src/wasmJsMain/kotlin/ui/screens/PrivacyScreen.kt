package ui.screens

import androidx.compose.runtime.Composable
import ui.content.DocumentoScreen
import ui.content.Textos

/**
 * Aviso de privacidad.
 *
 * El texto vive en `contenido/privacidad.md`; ver [TermsScreen] para cómo se
 * genera. Describe el tratamiento real: cuentas, dirección cifrada, pago por
 * Mercado Pago, datos que recibe la paquetería, formulario de contacto y
 * ruleta. **Si se añade un servicio de terceros o un dato nuevo, el .md tiene
 * que actualizarse.**
 */
@Composable
fun PrivacyScreen() = DocumentoScreen(Textos.privacidad)
