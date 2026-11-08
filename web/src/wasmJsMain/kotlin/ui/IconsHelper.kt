package ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun iconoDeTrazo(nombre: String, grosor: Float, vararg trazos: String): ImageVector =
    ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f).apply {
        trazos.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black), // Icon() lo tiñe con `tint`
                strokeLineWidth = grosor,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object PassionIconos {
    val Paquete: ImageVector by lazy {
        iconoDeTrazo(
            "Paquete", 1.6f,
            "M21 8 12 3 3 8v8l9 5 9-5V8z",
            "M3 8l9 5 9-5",
            "M12 13v8",
        )
    }
}