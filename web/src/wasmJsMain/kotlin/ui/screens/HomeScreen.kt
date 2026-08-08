package ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import network.MediaConfig
import ui.theme.PassionTheme

@Composable
fun HomeScreen(onExploreCatalog: () -> Unit) {
    val semantics = PassionTheme.semantics

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PassionTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(PassionTheme.spacing.s12))

        if (MediaConfig.isLogoConfigured) {
            AsyncImage(
                model = MediaConfig.logoUrl,
                contentDescription = "Passion à gogo · tienda para adultos",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
            )
        } else {
            // Respaldo si el logotipo no está disponible: la tipografía script de la marca.
            Text(
                text = "Passion à gogo",
                color = MaterialTheme.colorScheme.primary,
                style = PassionTheme.type.scriptAccent,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(PassionTheme.spacing.s6))
        Text(
            text = "Tu catálogo, en un solo lugar",
            color = semantics.onBackgroundStrong,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PassionTheme.spacing.s6))
        Text(
            text = "Explora nuestro catálogo con calma y descubre las promociones vigentes, " +
                "reunidas en una sola experiencia.",
            color = semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(PassionTheme.spacing.s8))
        Button(onClick = onExploreCatalog, shape = MaterialTheme.shapes.extraLarge) {
            Text("Ver catálogo", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(PassionTheme.spacing.s12))
    }
}
