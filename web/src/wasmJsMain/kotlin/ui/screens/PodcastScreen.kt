package ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import network.MediaConfig
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.SpotifyContent
import ui.components.SpotifyEmbed
import ui.components.openInNewTab

@Composable
fun PodcastScreen() {
    ContentScreen(title = "Podcast") {
        Paragraph("Escucha nuestro podcast: historias, novedades y lo que suena en Passio Agogo.")
        Spacer(Modifier.height(12.dp))

        if (!MediaConfig.isPodcastConfigured) {
            Paragraph(
                "El podcast aún no está disponible: falta configurar el show de Spotify."
            )
            return@ContentScreen
        }

        SpotifyEmbed(
            content = SpotifyContent.Show,
            id = MediaConfig.PODCAST_SHOW_ID,
            modifier = Modifier
                .fillMaxWidth()
                .height(352.dp),
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { openInNewTab(MediaConfig.podcastShowUrl) },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1DB954),
                contentColor = Color.Black,
            ),
        ) {
            Text("Abrir en Spotify", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "En el reproductor se escuchan avances de ~30 segundos. Abre el podcast " +
                "en Spotify para escucharlo completo y seguirlo.",
            color = Color(0xFF8F84B8),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
