package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ui.navigation.Screen
import ui.theme.PassionTheme

private val PrimaryItems = listOf(
    Screen.Home,
    Screen.Catalog,
    Screen.About,
//    Screen.Video,
    Screen.Podcast,
    Screen.Events,
    Screen.Care,
    Screen.Contact,
    Screen.Help,
)

@Composable
fun NavBar(current: Screen, isAuthenticated: Boolean, onNavigate: (Screen) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PassionTheme.semantics.barSurface)
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = PassionTheme.spacing.s5,
                vertical = PassionTheme.spacing.s3,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s6),
    ) {
        Text(
            text = "Passion à gogo",
            color = MaterialTheme.colorScheme.primary,
            style = PassionTheme.type.scriptAccent,
            fontSize = 28.sp,
            modifier = Modifier.clickable { onNavigate(Screen.Home) },
        )
        Spacer(Modifier.width(PassionTheme.spacing.s2))
        PrimaryItems.forEach { screen ->
            NavItem(
                label = screen.title,
                selected = screen == current,
                onClick = { onNavigate(screen) },
            )
        }

        // Último elemento: acceso o cuenta, según haya sesión iniciada.
//        val accountScreen = if (isAuthenticated) Screen.Account else Screen.Login
//        NavItem(
//            label = accountScreen.title,
//            selected = current == Screen.Login || current == Screen.Account,
//            onClick = { onNavigate(accountScreen) },
//        )
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) PassionTheme.semantics.onBackgroundStrong
        else PassionTheme.semantics.onBackgroundMuted,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = PassionTheme.spacing.s1),
    )
}
