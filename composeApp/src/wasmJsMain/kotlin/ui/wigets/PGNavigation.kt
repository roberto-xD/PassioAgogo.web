package ui.wigets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import passionagogo.composeapp.generated.resources.Res
import passionagogo.composeapp.generated.resources.compose_multiplatform

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Navigation(){
    val active = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier
                .weight(1f),
            text = "Inicio",
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier
                .weight(1f),
            text = "Nosotros",
            textAlign = TextAlign.Center,
        )
        Image(
            painter = painterResource(Res.drawable.compose_multiplatform),
            contentDescription = null,
            modifier = Modifier
                .size(30.dp)
                .weight(4f),
            alignment = Alignment.Center,
        )
        Text(
            modifier = Modifier
                .weight(1f),
            text = "Cuenta",
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .background(color = if (active.value) Color.Green else Color.White)
                .onPointerEvent(PointerEventType.Enter){
                    active.value = true
                    println("enter to the text")
                }
                .onPointerEvent(PointerEventType.Exit) {
                    active.value = false
                    println("exit from the text")
                },
            text = "Inicio",
            fontStyle = if (active.value) FontStyle.Italic else FontStyle.Normal,
            textAlign = TextAlign.Center,
        )
        Image(
            painter = painterResource(Res.drawable.compose_multiplatform),
            contentDescription = null,
            modifier = Modifier
                .size(30.dp)
                .weight(1f),
            alignment = Alignment.Center,
        )
    }
}