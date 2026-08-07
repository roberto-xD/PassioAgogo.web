package ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Radios de esquina del sistema de diseño. */
object PassionRadius {
    val Sm = 6.dp
    val Md = 12.dp
    val Lg = 20.dp
    val Xl = 28.dp
    val Pill = 999.dp
}

val PassionShapes = Shapes(
    extraSmall = RoundedCornerShape(PassionRadius.Sm),
    small = RoundedCornerShape(PassionRadius.Md),
    medium = RoundedCornerShape(PassionRadius.Lg),
    large = RoundedCornerShape(PassionRadius.Xl),
    extraLarge = RoundedCornerShape(PassionRadius.Pill),
)

/** Escala de espaciado (múltiplos de 4dp). */
object PassionSpacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
    val s16 = 64.dp
    val s20 = 80.dp
    val s24 = 96.dp
}
