package ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Paleta de marca. Las rampas y los alias semánticos provienen del sistema de diseño de
 * Passion Agogo; el esquema oscuro se deriva de las mismas rampas.
 */
object PassionColors {
    // Rampa morada
    val Purple50 = Color(0xFFFBF4FC)
    val Purple100 = Color(0xFFF4E3F8)
    val Purple200 = Color(0xFFE6C2EF)
    val Purple300 = Color(0xFFD394E4)
    val Purple400 = Color(0xFFB85CD2)
    val Purple500 = Color(0xFF9E35C0)
    val Purple600 = Color(0xFF8E22BB) // primary de marca
    val Purple700 = Color(0xFF711C97)
    val Purple800 = Color(0xFF551774)
    val Purple900 = Color(0xFF360F4D)

    // Rosa / acento
    val Pink50 = Color(0xFFFFF0F6)
    val Pink100 = Color(0xFFFFD9E9)
    val Pink200 = Color(0xFFFFB0D2)
    val Pink300 = Color(0xFFFF7FB4)
    val Pink400 = Color(0xFFF94F97)
    val Pink500 = Color(0xFFE8317E)
    val Pink600 = Color(0xFFC41F66)
    val Pink700 = Color(0xFF9C1852)

    // Neutros cálidos
    val Neutral0 = Color(0xFFFFFDFE)
    val Neutral50 = Color(0xFFFBF6FA)
    val Neutral100 = Color(0xFFF3EAF1)
    val Neutral200 = Color(0xFFE4D5E2)
    val Neutral300 = Color(0xFFC9B3C7)
    val Neutral400 = Color(0xFF9E8298)
    val Neutral500 = Color(0xFF786274)
    val Neutral600 = Color(0xFF5B4A58)
    val Neutral700 = Color(0xFF40323D)
    val Neutral800 = Color(0xFF2B1F29)
    val Neutral900 = Color(0xFF1A1218)

    // Semánticos
    val Success = Color(0xFF3E9B6B)
    val SuccessBg = Color(0xFFE8F7EF)
    val SuccessOnDark = Color(0xFF7BE0A5) // versión clara, legible sobre fondo oscuro
    val Warning = Color(0xFFD98A2B)
    val WarningBg = Color(0xFFFBF0DF)
    val Error = Color(0xFFD6455F)
    val ErrorBg = Color(0xFFFCE7EA)
    val ErrorOnDark = Color(0xFFFF8A9B)

    // Alias semánticos (tema claro)
    val SurfacePage = Neutral50
    val SurfaceCard = Neutral0
    val SurfaceSunken = Neutral100
    val SurfaceInverse = Purple900
    val TextHeading = Neutral900
    val TextBody = Neutral600
    val TextMuted = Neutral400
    val TextOnBrand = Neutral0
    val BorderSubtle = Neutral200
    val BorderDefault = Neutral300
    val BorderFocus = Purple500

    /** Color corporativo de Spotify: es de un tercero, no cambia con el tema. */
    val SpotifyGreen = Color(0xFF1DB954)
}

/**
 * Colores que Material 3 no modela y que la app sí necesita: el degradado del fondo, la
 * superficie de las barras, el acento de oferta, etc. Se exponen por
 * [ui.theme.PassionTheme] para no repartir literales por las pantallas.
 */
@Immutable
data class PassionSemantics(
    val gradientTop: Color,
    val gradientBottom: Color,
    val barSurface: Color,
    val onBackgroundStrong: Color,
    val onBackgroundMuted: Color,
    val onBackgroundSubtle: Color,
    val offer: Color,
    val onOffer: Color,
    val success: Color,
    val danger: Color,
    val overlayWeak: Color,
    val imagePlaceholder: Color,
    val onImagePlaceholder: Color,
)

val LightSemantics = PassionSemantics(
    gradientTop = PassionColors.Neutral0,
    gradientBottom = PassionColors.Purple50,
    barSurface = PassionColors.Neutral100,
    onBackgroundStrong = PassionColors.TextHeading,
    onBackgroundMuted = PassionColors.TextBody,
    onBackgroundSubtle = PassionColors.TextMuted,
    offer = PassionColors.Pink500,
    onOffer = PassionColors.Neutral0,
    success = PassionColors.Success,
    danger = PassionColors.Error,
    overlayWeak = Color(0x0D1A1218),
    imagePlaceholder = PassionColors.Purple100,
    onImagePlaceholder = PassionColors.Purple600,
)

val DarkSemantics = PassionSemantics(
    gradientTop = PassionColors.Neutral900,
    gradientBottom = PassionColors.Purple900,
    barSurface = Color(0x33000000),
    onBackgroundStrong = PassionColors.Neutral0,
    onBackgroundMuted = PassionColors.Purple200,
    onBackgroundSubtle = PassionColors.Neutral400,
    offer = PassionColors.Pink400,
    onOffer = PassionColors.Neutral900,
    success = PassionColors.SuccessOnDark,
    danger = PassionColors.ErrorOnDark,
    overlayWeak = Color(0x33FFFFFF),
    imagePlaceholder = PassionColors.Purple800,
    onImagePlaceholder = PassionColors.Purple200,
)

val PassionLightColorScheme = lightColorScheme(
    primary = PassionColors.Purple600,
    onPrimary = PassionColors.TextOnBrand,
    primaryContainer = PassionColors.Purple100,
    onPrimaryContainer = PassionColors.Purple800,
    secondary = PassionColors.Pink500,
    onSecondary = Color.White,
    secondaryContainer = PassionColors.Pink100,
    onSecondaryContainer = PassionColors.Pink700,
    background = PassionColors.SurfacePage,
    onBackground = PassionColors.TextHeading,
    surface = PassionColors.SurfaceCard,
    onSurface = PassionColors.TextHeading,
    surfaceVariant = PassionColors.SurfaceSunken,
    onSurfaceVariant = PassionColors.TextBody,
    outline = PassionColors.BorderDefault,
    outlineVariant = PassionColors.BorderSubtle,
    error = PassionColors.Error,
    onError = PassionColors.Neutral0,
    errorContainer = PassionColors.ErrorBg,
    onErrorContainer = PassionColors.Pink700,
)

/** Derivado de las mismas rampas, con los tonos claros donde Material los espera. */
val PassionDarkColorScheme = darkColorScheme(
    primary = PassionColors.Purple400,
    onPrimary = PassionColors.Purple900,
    primaryContainer = PassionColors.Purple700,
    onPrimaryContainer = PassionColors.Purple100,
    secondary = PassionColors.Pink300,
    onSecondary = PassionColors.Pink700,
    secondaryContainer = PassionColors.Pink700,
    onSecondaryContainer = PassionColors.Pink100,
    background = PassionColors.Neutral900,
    onBackground = PassionColors.Neutral0,
    surface = PassionColors.Neutral800,
    onSurface = PassionColors.Neutral0,
    surfaceVariant = PassionColors.Neutral700,
    onSurfaceVariant = PassionColors.Neutral300,
    outline = PassionColors.Neutral500,
    outlineVariant = PassionColors.Neutral700,
    error = PassionColors.ErrorOnDark,
    onError = PassionColors.Neutral900,
    errorContainer = PassionColors.Pink700,
    onErrorContainer = PassionColors.ErrorBg,
)
