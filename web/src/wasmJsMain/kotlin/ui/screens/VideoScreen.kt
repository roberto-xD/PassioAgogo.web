package ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import network.MediaConfig
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.VideoPlayer

@Composable
fun VideoScreen() {
    ContentScreen(title = "Video") {
        Paragraph("Conoce Passion à gogo en menos de un minuto.")
        Spacer(Modifier.height(12.dp))

        if (MediaConfig.isConfigured) {
            VideoPlayer(
                url = MediaConfig.presentacionUrl,
                posterUrl = MediaConfig.presentacionPosterUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
        } else {
            Paragraph(
                "El video aún no está disponible: falta configurar el proyecto de Supabase " +
                    "o subir el archivo al bucket de Storage."
            )
        }
    }
}
