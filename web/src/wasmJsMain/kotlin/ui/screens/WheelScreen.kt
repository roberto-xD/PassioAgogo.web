package ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import models.SpinOutcome
import models.WheelTerm
import network.WhatsAppConfig
import ui.components.BottomSheet
import ui.components.ContentScreen
import ui.components.FortuneWheel
import ui.components.Paragraph
import ui.components.copyToClipboard
import ui.components.openInNewTab
import ui.theme.PassionTheme
import viewmodel.WheelUiState

/** Ancho máximo de la ruleta. Más grande no se lee mejor y deja el botón lejos. */
private val WHEEL_MAX_WIDTH = 420.dp

/** Cuánto se queda el aviso de copiado antes de volver a mostrar el código. */
private const val AVISO_COPIADO_MS = 2_000L

/**
 * Ruleta de descuentos.
 *
 * El premio lo decide la Edge Function `spin` y llega con un código verificable; la
 * ruleta solo frena donde toca. La sección se enciende y apaga desde `site_settings`.
 */
@Composable
fun WheelScreen(
    state: WheelUiState,
    onSpin: () -> Unit,
    onLanded: () -> Unit,
) {
    ContentScreen(title = "Gira y gana") {
        when {
            state.isLoading -> Paragraph("Preparando la ruleta…")

            !state.visible -> Paragraph(
                "La ruleta no está disponible en este momento. Vuelve a intentarlo pronto."
            )

            state.prizes.size < 2 -> Paragraph(
                "Estamos preparando los premios. Vuelve pronto."
            )

            else -> {
                // Antes de girar se explica el trato; después manda el premio, y repetir
                // «un giro por persona» junto al resultado solo quita sitio.
                if (!state.revealed) {
                    Paragraph(
                        "Un giro por persona. Lo que salga es tuyo: te damos un código " +
                            "para que lo reclames cuando compres."
                    )
                }

                // La ruleta se retira al revelar el premio: ya cumplió, y dejarla
                // invitaría a volver a pulsarla cuando el giro está gastado.
                AnimatedVisibility(
                    visible = !state.revealed,
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FortuneWheel(
                            prizes = state.prizes,
                            spinning = state.spinning,
                            landingIndex = state.landingIndex,
                            onSpin = onSpin,
                            onLanded = onLanded,
                            canSpin = state.canSpin,
                            // El tope va antes de rellenar: al revés, `fillMaxWidth` fija
                            // el ancho del padre como mínimo y `widthIn` ya no puede
                            // recortarlo.
                            modifier = Modifier
                                .widthIn(max = WHEEL_MAX_WIDTH)
                                .fillMaxWidth(),
                        )
                    }
                }

                state.errorMessage?.let { mensaje ->
                    Text(
                        text = mensaje,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = PassionTheme.spacing.s4),
                    )
                }

                // El resultado no aparece hasta que la ruleta frena, aunque el servidor
                // haya contestado hace rato: enseñarlo antes destriparía el giro.
                if (state.revealed) {
                    state.outcome?.let { premio -> ResultadoDelGiro(premio) }
                }

                if (state.terms.isNotEmpty()) {
                    BasesDeLaPromocion(state.terms)
                }
            }
        }
    }
}

/**
 * Enlace a las bases y la hoja que las muestra.
 *
 * Van en una hoja inferior y no en una pantalla aparte para no sacar a nadie de la
 * ruleta: quien quiera leer la letra pequeña antes de girar vuelve de un toque.
 */
@Composable
private fun BasesDeLaPromocion(terms: List<WheelTerm>) {
    var abierto by remember { mutableStateOf(false) }

    Text(
        text = "Bases de la promoción",
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s5)
            .clickable { abierto = true }
            .padding(vertical = PassionTheme.spacing.s2),
    )

    if (abierto) {
        BottomSheet(title = "Bases de la promoción", onDismiss = { abierto = false }) {
            terms.forEachIndexed { indice, clausula ->
                Text(
                    text = clausula.titulo,
                    color = PassionTheme.semantics.onBackgroundStrong,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        // La primera va pegada al título de la hoja; las demás necesitan
                        // aire por encima para que se vean como cláusulas separadas.
                        top = if (indice == 0) 0.dp else PassionTheme.spacing.s5,
                        bottom = PassionTheme.spacing.s1,
                    ),
                )
                Text(
                    text = clausula.cuerpo,
                    color = PassionTheme.semantics.onBackgroundMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ResultadoDelGiro(premio: SpinOutcome) {
    val shape = MaterialTheme.shapes.medium

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s5)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(PassionTheme.spacing.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when {
                premio.yaGiro -> "Ya habías girado"
                premio.otorgaPremio -> "¡Ganaste!"
                else -> "Esta vez no"
            },
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = premio.rotulo,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        )

        if (premio.detalle.isNotBlank()) {
            Text(
                text = premio.detalle,
                color = PassionTheme.semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            )
        }

        if (premio.otorgaPremio && premio.codigo.isNotBlank()) {
            CodigoDelPremio(premio.codigo)

            if (WhatsAppConfig.isConfigured) {
                Button(
                    onClick = {
                        openInNewTab(WhatsAppConfig.enlaceParaPremio(premio.rotulo, premio.codigo))
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.padding(top = PassionTheme.spacing.s4),
                ) {
                    Text("Reclamar por WhatsApp", style = MaterialTheme.typography.labelLarge)
                }
            }

            Text(
                text = "Guarda tu código: es lo que verificamos al entregarte el premio.",
                color = PassionTheme.semantics.onBackgroundSubtle,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = PassionTheme.spacing.s3),
            )
        }
    }
}

/** El código, pulsable para copiarlo. Dictarlo a mano invita a equivocarse. */
@Composable
private fun CodigoDelPremio(codigo: String) {
    var aviso by remember { mutableStateOf("") }

    LaunchedEffect(aviso) {
        if (aviso.isNotBlank()) {
            delay(AVISO_COPIADO_MS)
            aviso = ""
        }
    }

    Text(
        text = aviso.ifBlank { codigo },
        color = PassionTheme.semantics.onBackgroundStrong,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(top = PassionTheme.spacing.s4)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(PassionTheme.semantics.overlayWeak)
            .clickable {
                aviso = if (copyToClipboard(codigo)) "¡Copiado!" else "No se pudo copiar"
            }
            .padding(
                horizontal = PassionTheme.spacing.s4,
                vertical = PassionTheme.spacing.s2,
            ),
    )
}
