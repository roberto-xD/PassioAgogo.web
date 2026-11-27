package ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import models.SpinOutcome
import models.WheelTerm
import network.WhatsAppConfig
import ui.components.BottomSheet
import ui.components.ConfetiRuleta
import ui.components.FortuneWheel
import ui.components.copyToClipboard
import ui.components.openInNewTab
import ui.theme.PassionColors
import ui.theme.PassionTheme
import ui.theme.RuletaColors
import viewmodel.WheelUiState

/** Ancho máximo de la ruleta. Más grande no se lee mejor y deja el botón lejos. */
private val WHEEL_MAX_WIDTH = 420.dp

/** Tope del contenido, igual que el banner: más ancho y el texto se lee peor. */
private val CONTENT_MAX_WIDTH = 1200.dp

/** A partir de aquí caben el texto y la ruleta uno al lado del otro. */
private val SIDE_BY_SIDE_MIN_WIDTH = 900.dp

/** Cuánto se queda el aviso de copiado antes de volver a mostrar el código. */
private const val AVISO_COPIADO_MS = 2_000L

/**
 * Ruleta de descuentos.
 *
 * El premio lo decide la Edge Function `spin` y llega con un código verificable; la
 * ruleta solo frena donde toca. La sección se enciende y apaga desde `site_settings`.
 *
 * Viste la misma piel que el banner de la portada —fondo morado oscuro, confeti, el
 * mismo rótulo y la misma ruleta— porque es el mismo sitio: se entra pulsando el banner
 * y aterrizar en una pantalla clara rompía la continuidad. **Solo cambia el aspecto**:
 * el giro, el sorteo y el canje siguen exactamente como estaban.
 */
@Composable
fun WheelScreen(
    state: WheelUiState,
    onSpin: () -> Unit,
    onLanded: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RuletaColors.Fondo)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Decorativo y por detrás: no debe comerse ningún toque de la ruleta.
            ConfetiRuleta(modifier = Modifier.matchParentSize())

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(
                        horizontal = PassionTheme.spacing.s6,
                        vertical = PassionTheme.spacing.s10,
                    ),
            ) {
                when {
                    state.isLoading -> Aviso("Preparando la ruleta…")

                    !state.visible -> Aviso(
                        "La ruleta no está disponible en este momento. Vuelve a " +
                            "intentarlo pronto."
                    )

                    state.prizes.size < 2 -> Aviso(
                        "Estamos preparando los premios. Vuelve pronto."
                    )

                    else -> Ruleta(state, onSpin, onLanded)
                }
            }
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        text = texto,
        color = PassionColors.Purple100,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Ruleta(state: WheelUiState, onSpin: () -> Unit, onLanded: () -> Unit) {
    // Revelado el premio, el escaparate sobra: lo que importa es el código. La ruleta
    // se retira —ya cumplió, y dejarla invitaría a pulsarla con el giro gastado— y el
    // resultado se queda solo en el centro.
    if (state.revealed) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.outcome?.let { premio -> ResultadoDelGiro(premio) }
            if (state.terms.isNotEmpty()) {
                BasesDeLaPromocion(state.terms)
            }
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val enFila = maxWidth >= SIDE_BY_SIDE_MIN_WIDTH

        if (enFila) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Copy(state, enFila = true)
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    LaRueda(state, onSpin, onLanded)
                }
            }
        } else {
            // El texto alineado a la izquierda y la ruleta centrada: apilarlo todo
            // centrado deja el párrafo con los dos bordes irregulares y se lee peor.
            Column(modifier = Modifier.fillMaxWidth()) {
                Copy(state, enFila = false)
                Spacer(Modifier.height(PassionTheme.spacing.s6))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    LaRueda(state, onSpin, onLanded)
                }
            }
        }
    }
}

/**
 * El texto de la izquierda: distintivo, rótulo, trato y bases.
 *
 * El mismo copy que ya había, con la piel del banner. El rótulo se parte en dos piezas
 * —«Gira y gana» recto y «tu premio» en script— igual que en la portada.
 */
@Composable
private fun ColumnScope.Copy(state: WheelUiState, enFila: Boolean) {
    Text(
        text = "RULETA DE PREMIOS",
        color = RuletaColors.Fondo,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(RuletaColors.Yellow)
            .padding(
                horizontal = PassionTheme.spacing.s3,
                vertical = PassionTheme.spacing.s1,
            ),
    )

    Spacer(Modifier.height(PassionTheme.spacing.s4))

    val tituloSize = if (enFila) 48.sp else 34.sp
    val scriptSize = if (enFila) 40.sp else 32.sp

    if (enFila) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s3),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "Gira y gana",
                color = RuletaColors.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                fontSize = tituloSize,
            )
            Text(
                text = "tu premio",
                color = RuletaColors.PinkScript,
                style = PassionTheme.type.scriptAccent,
                fontSize = scriptSize,
            )
        }
    } else {
        Text(
            text = "Gira y gana",
            color = RuletaColors.White,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            fontSize = tituloSize,
        )
        Text(
            text = "tu premio",
            color = RuletaColors.PinkScript,
            style = PassionTheme.type.scriptAccent,
            fontSize = scriptSize,
        )
    }

    Spacer(Modifier.height(PassionTheme.spacing.s4))
    Text(
        text = "Un giro por persona. Lo que salga es tuyo: te damos un código para que " +
            "lo reclames cuando compres.",
        color = PassionColors.Purple100,
        style = MaterialTheme.typography.bodyMedium,
    )

    Spacer(Modifier.height(PassionTheme.spacing.s4))
    Text(
        text = "Toca GIRAR para descubrir tu premio.",
        color = RuletaColors.Yellow,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )

    state.errorMessage?.let { mensaje ->
        Spacer(Modifier.height(PassionTheme.spacing.s3))
        Text(
            text = mensaje,
            // Rosa y no el rojo de Material: sobre morado oscuro ese rojo se apaga, y
            // el rosa de esta paleta se lee sin romper la piel de la sección.
            color = RuletaColors.PinkScript,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (state.terms.isNotEmpty()) {
        Spacer(Modifier.height(PassionTheme.spacing.s3))
        BasesDeLaPromocion(state.terms)
    }
}

@Composable
private fun LaRueda(state: WheelUiState, onSpin: () -> Unit, onLanded: () -> Unit) {
    AnimatedVisibility(
        visible = !state.revealed,
        exit = fadeOut() + shrinkVertically(),
    ) {
        FortuneWheel(
            prizes = state.prizes,
            spinning = state.spinning,
            landingIndex = state.landingIndex,
            onSpin = onSpin,
            onLanded = onLanded,
            canSpin = state.canSpin,
            // El tope va antes de rellenar: al revés, `fillMaxWidth` fija el ancho del
            // padre como mínimo y `widthIn` ya no puede recortarlo.
            modifier = Modifier
                .widthIn(max = WHEEL_MAX_WIDTH)
                .fillMaxWidth(),
        )
    }
}

/**
 * Enlace a las bases y la hoja que las muestra.
 *
 * Van en una hoja inferior y no en una pantalla aparte para no sacar a nadie de la
 * ruleta: quien quiera leer la letra pequeña antes de girar vuelve de un toque. La hoja
 * conserva su fondo claro: es un documento para leer, no parte del escaparate.
 */
@Composable
private fun BasesDeLaPromocion(terms: List<WheelTerm>) {
    var abierto by remember { mutableStateOf(false) }

    Text(
        text = "Bases de la promoción",
        color = RuletaColors.Yellow,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable { abierto = true }
            .padding(vertical = PassionTheme.spacing.s1),
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

/**
 * El premio, en una tarjeta clara sobre el morado.
 *
 * Clara a propósito: es lo único que hay que leer con calma y lo que hay que copiar,
 * así que se separa del escaparate en lugar de fundirse con él.
 */
@Composable
private fun ResultadoDelGiro(premio: SpinOutcome) {
    val shape = MaterialTheme.shapes.medium

    Column(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
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
