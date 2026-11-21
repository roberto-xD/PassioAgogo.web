package ui.screens

import androidx.compose.runtime.Composable
import ui.content.DocumentoScreen
import ui.content.Textos

/**
 * Términos y condiciones.
 *
 * El texto vive en `contenido/terminos.md` y llega aquí a través de
 * `tools/contenido/build.py`, que con el mismo origen genera la página de
 * `sitio/terminos/`. Para cambiar una palabra: edita el .md y vuelve a correr
 * el script.
 *
 * **Debe revisarlo un profesional legal antes de publicarlo**; el README de
 * `contenido/` lista los puntos concretos.
 */
@Composable
fun TermsScreen() = DocumentoScreen(Textos.terminos)
