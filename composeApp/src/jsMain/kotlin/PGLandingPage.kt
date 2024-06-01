import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import passionagogo.composeapp.generated.resources.Res
import passionagogo.composeapp.generated.resources.pg_ic_mainlogo

@Composable
fun Landing(){
    MaterialTheme {
        Column (
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x4ca1f5))
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ){
            Spacer(Modifier.height(28.dp))
            Image(painterResource(Res.drawable.pg_ic_mainlogo),null, modifier = Modifier.height(280.dp))
            Spacer(Modifier.height(28.dp))

        }
    }
}