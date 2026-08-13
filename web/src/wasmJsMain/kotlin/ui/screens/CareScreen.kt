package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import models.GuideBlock
import models.GuideItem
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.theme.PassionTheme
import viewmodel.GuidesUiState

/**
 * Uso y cuidados.
 *
 * Las guías se pliegan y despliegan en lugar de mostrarse enteras: con varios tipos de
 * producto, el texto completo obligaría a desplazarse mucho para encontrar el que
 * interesa. Plegadas, el índice cabe de un vistazo.
 */
@Composable
fun CareScreen(state: GuidesUiState) {
    ContentScreen(title = "Uso y cuidados") {
        Paragraph(
            "Cómo usar, limpiar y conservar cada tipo de producto. Son recomendaciones " +
                "generales de higiene y conservación: cuando las instrucciones del " +
                "fabricante digan otra cosa, hazles caso a ellas."
        )

        when {
            state.isLoading -> Paragraph("Cargando guías…")

            state.errorMessage != null -> Paragraph(
                "No pudimos cargar las guías. Inténtalo de nuevo en un momento."
            )

            state.guides.isEmpty() -> Paragraph(
                "Estamos preparando esta sección. Mientras tanto, escríbenos y te " +
                    "orientamos con lo que necesites."
            )

            else -> Column(
                modifier = Modifier.padding(top = PassionTheme.spacing.s3),
                verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s3),
            ) {
                state.guides.forEach { guide -> GuideCard(guide) }
            }
        }

        Text(
            text = "Esta información es orientativa y no sustituye el consejo de un " +
                "profesional sanitario. Ante molestias, irritación o reacción, suspende " +
                "el uso y consulta.",
            color = PassionTheme.semantics.onBackgroundSubtle,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = PassionTheme.spacing.s4),
        )
    }
}

@Composable
private fun GuideCard(guide: GuideItem) {
    var expanded by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            // El área pulsable es la tarjeta entera: un objetivo pequeño en el móvil
            // haría que abrir una guía costase varios intentos.
            .clickable { expanded = !expanded }
            .padding(PassionTheme.spacing.s4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (guide.emoji.isNotBlank()) {
                Text(
                    text = guide.emoji,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = PassionTheme.spacing.s2),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = guide.titulo,
                    color = PassionTheme.semantics.onBackgroundStrong,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (guide.resumen.isNotBlank() && !expanded) {
                    Text(
                        text = guide.resumen,
                        color = PassionTheme.semantics.onBackgroundSubtle,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = PassionTheme.spacing.s1),
                    )
                }
            }
            Text(
                text = if (expanded) "−" else "+",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = PassionTheme.spacing.s2),
            )
        }

        if (expanded) {
            guide.blocks.forEach { block -> GuideBlockView(block) }

            if (guide.advertencias.isNotBlank()) {
                Text(
                    text = guide.advertencias,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = PassionTheme.spacing.s4)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(PassionTheme.spacing.s3),
                )
            }
        }
    }
}

@Composable
private fun GuideBlockView(block: GuideBlock) {
    Column(modifier = Modifier.padding(top = PassionTheme.spacing.s4)) {
        Text(
            text = block.label.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = block.text,
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s1),
        )
    }
}
