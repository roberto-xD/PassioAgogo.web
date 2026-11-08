package ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


@Immutable
data class PassionTonos(
    val marcaSuave: Color,       // purple-50: fondos tintados
    val marcaLinea: Color,       // purple-100: línea de la línea de tiempo
    val marcaHover: Color,       // purple-700: hover del botón primario / enlaces
    val marcaFuerte: Color,      // purple-700: precios
    val marcaProfunda: Color,    // purple-800: titular de tarjeta tintada
    val textoPlaceholder: Color, // purple-300
    val acentoSuave: Color,      // pink-50
    val acentoHover: Color,      // pink-600
    val avisoSuave: Color,       // warning-50
    val textoSobreAviso: Color,  // neutral-700
    val pieFondo: Color,
    val pieTexto: Color,
    val sombra: Color,
)

internal val TonosClaros = PassionTonos(
    marcaSuave = PassionColors.Purple50,
    marcaLinea = PassionColors.Purple100,
    marcaHover = PassionColors.Purple700,
    marcaFuerte = PassionColors.Purple700,
    marcaProfunda = PassionColors.Purple800,
    textoPlaceholder = PassionColors.Purple300,
    acentoSuave = PassionColors.Pink50,
    acentoHover = PassionColors.Pink600,
    avisoSuave = PassionColors.WarningBg,
    textoSobreAviso = PassionColors.Neutral700,
    pieFondo = PassionColors.Purple900,
    pieTexto = PassionColors.Purple100,
    sombra = PassionColors.Purple900,
)

internal val TonosOscuros = PassionTonos(
    marcaSuave = PassionColors.Purple900,
    marcaLinea = PassionColors.Purple800,
    marcaHover = PassionColors.Purple300,
    marcaFuerte = PassionColors.Purple200,
    marcaProfunda = PassionColors.Purple100,
    textoPlaceholder = PassionColors.Purple400,
    acentoSuave = PassionColors.Purple800,
    acentoHover = PassionColors.Pink200,
    avisoSuave = PassionColors.Neutral800,
    textoSobreAviso = PassionColors.Neutral300,
    pieFondo = PassionColors.Purple900,
    pieTexto = PassionColors.Purple100,
    sombra = Color.Black,
)

val PassionTheme.tonos: PassionTonos
    @Composable @ReadOnlyComposable
    get() = if (semantics == DarkSemantics) TonosOscuros else TonosClaros

/** `--shadow-sm` / `--shadow-md` del sistema: sombras cálidas y muy suaves. */
@Composable
fun Modifier.sombraPassion(elevacion: Int = 1, forma: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium): Modifier {
    val color = PassionTheme.tonos.sombra.copy(alpha = if (elevacion > 1) 0.18f else 0.10f)
    return shadow((if (elevacion > 1) 8 else 2).dp, forma, clip = false, ambientColor = color, spotColor = color)
}