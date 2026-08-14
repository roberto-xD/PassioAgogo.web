package ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import passioagogo.resources.Res
import passioagogo.resources.alex_brush_regular
import passioagogo.resources.emoji_passion
import passioagogo.resources.poppins_bold
import passioagogo.resources.poppins_medium
import passioagogo.resources.poppins_regular
import passioagogo.resources.poppins_semibold

/**
 * Estilos propios que Material no tiene ranura donde alojar.
 *
 * [scriptAccent] usa Alex Brush y está reservado al logotipo y a acentos de titular:
 * no debe emplearse para texto corrido.
 */
@Immutable
data class PassionTypeTokens(
    val scriptAccent: TextStyle,
)

/**
 * Construye la tipografía de la marca.
 *
 * `Font(...)` de Compose Resources es una función composable —carga el recurso de forma
 * asíncrona—, por eso la escala se arma aquí dentro y no en un objeto estático.
 */
@Composable
fun rememberPassionTypography(): Pair<Typography, PassionTypeTokens> {
    // El recorte de emoji va al final de la familia: Skia recorre la lista glifo a
    // glifo, así que Poppins se usa para todo lo que sabe dibujar y solo cae en la otra
    // fuente para los emojis, que Poppins no tiene. En Compose Web no hay fuentes del
    // sistema a las que recurrir: lo que no esté empaquetado no se pinta.
    val poppins = FontFamily(
        Font(Res.font.poppins_regular, FontWeight.Normal),
        Font(Res.font.poppins_medium, FontWeight.Medium),
        Font(Res.font.poppins_semibold, FontWeight.SemiBold),
        Font(Res.font.poppins_bold, FontWeight.Bold),
        Font(Res.font.emoji_passion, FontWeight.Normal),
    )
    val alexBrush = FontFamily(Font(Res.font.alex_brush_regular, FontWeight.Normal))

    return remember(poppins, alexBrush) {
        Typography(
            displayLarge = TextStyle(
                fontFamily = poppins, fontWeight = FontWeight.Bold, fontSize = 72.sp,
            ),
            displayMedium = TextStyle(
                fontFamily = poppins, fontWeight = FontWeight.Bold,
                fontSize = 48.sp, lineHeight = 55.2.sp,
            ),
            displaySmall = TextStyle(
                fontFamily = poppins, fontWeight = FontWeight.SemiBold,
                fontSize = 38.sp, lineHeight = 43.7.sp,
            ),
            headlineLarge = TextStyle(
                fontFamily = poppins, fontWeight = FontWeight.SemiBold,
                fontSize = 38.sp, lineHeight = 43.7.sp,
            ),
            headlineMedium = TextStyle(
                fontFamily = poppins, fontWeight = FontWeight.SemiBold, fontSize = 30.sp,
            ),
            headlineSmall = TextStyle(
                fontFamily = poppins, fontWeight = FontWeight.Medium, fontSize = 24.sp,
            ),
            titleLarge = TextStyle(
                fontFamily = poppins, fontWeight = FontWeight.Medium, fontSize = 24.sp,
            ),
            titleMedium = TextStyle(
                fontFamily = poppins, fontWeight = FontWeight.Medium, fontSize = 20.sp,
            ),
            titleSmall = TextStyle(
                fontFamily = poppins, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, lineHeight = 24.8.sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = poppins, fontSize = 18.sp, lineHeight = 30.6.sp,
            ),
            bodyMedium = TextStyle(
                fontFamily = poppins, fontSize = 16.sp, lineHeight = 24.8.sp,
            ),
            bodySmall = TextStyle(
                fontFamily = poppins, fontSize = 14.sp, lineHeight = 21.7.sp,
            ),
            labelLarge = TextStyle(
                fontFamily = poppins, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, lineHeight = 21.7.sp,
            ),
            labelMedium = TextStyle(
                fontFamily = poppins, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, letterSpacing = 1.68.sp,
            ),
            labelSmall = TextStyle(
                fontFamily = poppins, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, letterSpacing = 1.68.sp,
            ),
        ) to PassionTypeTokens(
            scriptAccent = TextStyle(fontFamily = alexBrush, fontSize = 52.sp),
        )
    }
}
