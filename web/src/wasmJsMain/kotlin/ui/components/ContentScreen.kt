package ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.theme.PassionTheme

/** Layout base para las pantallas de contenido: columna centrada, ancho máximo y scroll. */
@Composable
fun ContentScreen(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(
                    horizontal = PassionTheme.spacing.s6,
                    vertical = PassionTheme.spacing.s8,
                ),
        ) {
            Text(
                text = title,
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = PassionTheme.spacing.s5),
            )
            content()
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        color = PassionTheme.semantics.onBackgroundStrong,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = PassionTheme.spacing.s5, bottom = PassionTheme.spacing.s1),
    )
}

@Composable
fun Paragraph(text: String) {
    Text(
        text = text,
        color = PassionTheme.semantics.onBackgroundMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = PassionTheme.spacing.s2),
    )
}
