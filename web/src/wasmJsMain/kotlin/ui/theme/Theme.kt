package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode {
    /** Fuerza el tema claro. */
    Light,

    /** Fuerza el tema oscuro. */
    Dark,

    /** Sigue la preferencia del sistema operativo del visitante. */
    System,
}

/**
 * Configuración del tema.
 *
 * Cambia [MODE] para alternar entre claro, oscuro o seguir al sistema: es el único punto
 * donde se decide, y afecta a toda la app.
 */
object ThemeConfig {
    val MODE: ThemeMode = ThemeMode.Light
}

private val LocalPassionSemantics = staticCompositionLocalOf { LightSemantics }

private val LocalPassionType = staticCompositionLocalOf<PassionTypeTokens> {
    error("PassionTypeTokens no disponible: falta envolver la UI en PassionAGogoTheme()")
}

/**
 * Acceso a los tokens propios del tema, en paralelo a `MaterialTheme`:
 * `PassionTheme.semantics.offer`, `PassionTheme.spacing.s4`, …
 */
object PassionTheme {
    val semantics: PassionSemantics
        @Composable @ReadOnlyComposable get() = LocalPassionSemantics.current

    val type: PassionTypeTokens
        @Composable @ReadOnlyComposable get() = LocalPassionType.current

    val spacing: PassionSpacing get() = PassionSpacing

    val radius: PassionRadius get() = PassionRadius
}

@Composable
fun PassionAGogoTheme(
    mode: ThemeMode = ThemeConfig.MODE,
    content: @Composable () -> Unit,
) {
    val useDark = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }

    val (typography, typeTokens) = rememberPassionTypography()

    CompositionLocalProvider(
        LocalPassionSemantics provides if (useDark) DarkSemantics else LightSemantics,
        LocalPassionType provides typeTokens,
    ) {
        MaterialTheme(
            colorScheme = if (useDark) PassionDarkColorScheme else PassionLightColorScheme,
            shapes = PassionShapes,
            typography = typography,
            content = content,
        )
    }
}
