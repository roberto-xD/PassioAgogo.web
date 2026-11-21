package ui.screens

import androidx.compose.runtime.Composable
import ui.content.DocumentoScreen
import ui.content.Textos

/**
 * Cancelaciones, devoluciones y garantías.
 *
 * El texto vive en `contenido/devoluciones-y-garantias.md`; ver [TermsScreen]
 * para cómo se genera. Tiene pantalla y página propias, separadas de los
 * términos, porque es lo que alguien busca con una duda concreta —si un
 * juguete no enciende, si una prenda no es de su talla— y dentro de un
 * documento de veintidós secciones no lo encontraría.
 */
@Composable
fun DevolucionesScreen() = DocumentoScreen(Textos.devolucionesYGarantias)
