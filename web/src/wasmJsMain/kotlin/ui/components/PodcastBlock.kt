package ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import network.MediaConfig
import ui.theme.PassionColors
import ui.theme.PassionTheme

/** Alto del reproductor de Spotify; el que recomienda su propio incrustado. */
private val REPRODUCTOR_ALTO = 352.dp

/** Tope de ancho: un reproductor a todo lo ancho de una pantalla grande queda ridículo. */
private val REPRODUCTOR_ANCHO_MAX = 680.dp

/**
 * El podcast, para colgarlo de otra pantalla.
 *
 * Dejó de tener pantalla y entrada propias en la barra: con diez entradas nadie llegaba
 * al final de la barra, y un podcast que hay que ir a buscar no lo escucha nadie. Ahora
 * vive al pie de Inicio, debajo del carrusel, donde se topa con él quien ya está
 * mirando.
 *
 * Si el show no está configurado no se dibuja nada: en Inicio, un aviso de que falta
 * configurar algo sería ruido para quien viene a comprar.
 */
@Composable
fun PodcastBlock(modifier: Modifier = Modifier) {
    if (!MediaConfig.isPodcastConfigured) return

    Column(
        modifier = modifier.widthIn(max = REPRODUCTOR_ANCHO_MAX),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Relatos eróticos",
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PassionTheme.spacing.s2))
        Text(
            text = "Escucha el podcast de Yezidy: Relatos eróticos 2 y deja volar tu " +
                "imaginación.",
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(PassionTheme.spacing.s4))
        SpotifyEmbed(
            content = SpotifyContent.Show,
            id = MediaConfig.PODCAST_SHOW_ID,
            modifier = Modifier
                .fillMaxWidth()
                .height(REPRODUCTOR_ALTO),
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
            textAlign = TextAlign.Center,
        )
    }
}
