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
import androidx.compose.ui.unit.dp
import network.MediaConfig
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.SpotifyContent
import ui.components.SpotifyEmbed
import ui.components.openInNewTab
import ui.theme.PassionColors
import ui.theme.PassionTheme

@Composable
fun PodcastScreen() {
    ContentScreen(title = "Relatos eróticos") {
        Paragraph("Escucha el podcast de Yezidy: Relatos eróticos 2 y deja volar tu imaginación.")
        Spacer(Modifier.height(PassionTheme.spacing.s3))

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

        Spacer(Modifier.height(PassionTheme.spacing.s4))
        Button(
            onClick = { openInNewTab(MediaConfig.podcastShowUrl) },
            shape = MaterialTheme.shapes.extraLarge,
            // Color corporativo de Spotify: se mantiene fijo en ambos temas.
            colors = ButtonDefaults.buttonColors(
                containerColor = PassionColors.SpotifyGreen,
                contentColor = Color.Black,
            ),
        ) {
            Text("Abrir en Spotify", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(PassionTheme.spacing.s3))
        Text(
            text = "En el reproductor se escuchan avances de ~30 segundos. Abre el podcast " +
                "en Spotify para escucharlo completo y seguirlo.",
            color = PassionTheme.semantics.onBackgroundSubtle,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
