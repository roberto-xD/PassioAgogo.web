package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ui.theme.PassionTheme
import ui.theme.sombraPassion
import ui.theme.tonos

data class PasoPedido(val titulo: String, val texto: String)

@Composable
private fun Modifier.tarjeta(conSombra: Boolean = true): Modifier {
    val forma = MaterialTheme.shapes.medium
    return (if (conSombra) sombraPassion(1, forma) else this)
        .background(MaterialTheme.colorScheme.surface, forma)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, forma)
}

@Composable
private fun FilaPaso(indice: Int, paso: PasoPedido, hecho: Boolean, ultimo: Boolean) {
    val cs = MaterialTheme.colorScheme
    val tonos = PassionTheme.tonos
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s4),
    ) {
        Column(Modifier.width(32.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(32.dp).background(if (hecho) cs.primary else tonos.marcaSuave, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (hecho) "✓" else "${indice + 1}",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = if (hecho) cs.onPrimary else cs.primary,
                )
            }
            Box(
                Modifier
                    .width(2.dp)
                    .weight(1f)
                    .heightIn(min = PassionTheme.spacing.s4)
                    .background(if (ultimo) Color.Transparent else tonos.marcaLinea),
            )
        }
        Column(
            Modifier.weight(1f).padding(top = 5.dp, bottom = PassionTheme.spacing.s5),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(paso.titulo, style = MaterialTheme.typography.titleSmall.copy(lineHeight = 1.4.em), color = cs.onSurface)
            Text(paso.texto, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TarjetaPasos(pasos: List<PasoPedido>) {
    Column(
        Modifier.fillMaxWidth().tarjeta().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s5),
    ) {
        Text(
            text = "¿Qué sigue?",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Column {
            pasos.forEachIndexed { i, paso ->
                FilaPaso(i, paso, hecho = i == 0, ultimo = i == pasos.lastIndex)
            }
        }
    }
}