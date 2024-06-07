
import androidx.compose.runtime.Composable
import di.appModule
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.compose.KoinApplication
import ui.HomeScreen

@OptIn(ExperimentalResourceApi::class)
@Composable
fun App() {
    KoinApplication(
        application = {
            modules(appModule())
        }
    ){
        HomeScreen()
    }
}

