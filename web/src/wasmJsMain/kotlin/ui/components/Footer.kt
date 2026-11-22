package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import ui.navigation.Screen
import ui.theme.PassionTheme

private val LegalItems =
    listOf(Screen.Terms, Screen.Privacy, Screen.Returns, Screen.Help, Screen.Contact)

/**
 * Los perfiles de la marca.
 *
 * Van en el pie, enlazados, además de en el `sameAs` de los datos estructurados
 * del HTML: es lo que le dice a un buscador que esta web y esas cuentas son la
 * misma marca. Hasta ahora el sitio no enlazaba a ninguna.
 *
 * La de Facebook es larga porque la página no tiene nombre de usuario:
 * `facebook.com/passioagogo` no existe. Esta es la dirección a la que el propio
 * Facebook redirige desde el enlace con el id numérico. **Si se cambia aquí, hay
 * que cambiarla también en `tools/contenido/build.py` y en `index.html`.**
 */
private val Redes = listOf(
    "Instagram" to "https://www.instagram.com/passioagogo",
    "Facebook" to "https://www.facebook.com/people/Passion-%C3%A0-gogo/61559870680376/",
)

@Composable
fun Footer(onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PassionTheme.semantics.barSurface)
            .padding(
                horizontal = PassionTheme.spacing.s5,
                vertical = PassionTheme.spacing.s4,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s5),
        ) {
            LegalItems.forEach { screen ->
                Text(
                    text = screen.title,
                    color = PassionTheme.semantics.onBackgroundMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onNavigate(screen) },
                )
            }
            Redes.forEach { (nombre, url) ->
                Text(
                    text = nombre,
                    color = PassionTheme.semantics.onBackgroundMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { openInNewTab(url) },
                )
            }
        }
        Text(
            text = "© 2026 Passion à gogo",
            color = PassionTheme.semantics.onBackgroundSubtle,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s3),
        )
    }
}
