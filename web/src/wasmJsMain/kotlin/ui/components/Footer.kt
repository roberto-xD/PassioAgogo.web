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
    listOf(Screen.Terms, Screen.Privacy, Screen.Help, Screen.Contact)

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
        }
        Text(
            text = "© 2026 Passion Agogo",
            color = PassionTheme.semantics.onBackgroundSubtle,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s3),
        )
    }
}
