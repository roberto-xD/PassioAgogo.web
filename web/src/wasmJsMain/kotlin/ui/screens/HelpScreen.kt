package ui.screens

import androidx.compose.runtime.Composable
import ui.content.DocumentoScreen
import ui.content.Textos

/**
 * Preguntas frecuentes.
 *
 * El texto vive en `contenido/preguntas-frecuentes.md`; ver [TermsScreen] para
 * cómo se genera. Su página web además emite datos estructurados `FAQPage`:
 * cada `##` del Markdown es una pregunta, así que conviene que los encabezados
 * sigan estando redactados como tales.
 */
@Composable
fun HelpScreen() = DocumentoScreen(Textos.preguntasFrecuentes)
