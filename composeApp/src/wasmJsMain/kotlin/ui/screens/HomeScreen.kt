package ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.getKoin
import passionagogo.composeapp.generated.resources.Res
import passionagogo.composeapp.generated.resources.compose_multiplatform
import ui.wigets.Navigation
import viewmodel.HomeViewModel
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(){

    val viewModel: HomeViewModel = getKoin().get()
    val homeScreenState by viewModel.homeViewState.collectAsState()
    val cuac by viewModel.homeState.collectAsState()

    /*LaunchedEffect(Unit){
        viewModel.getProduts()
    }*/


    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }

        Scaffold(
            backgroundColor = Color.Cyan,
            topBar = {
                Navigation()
            },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = "https://upload.wikimedia.org/wikipedia/en/f/ff/SuccessKid.jpg",
                        contentDescription = "",
                        modifier = Modifier.size(500.dp),
                    )
                    Button(
                        onClick = {
                            showContent = !showContent
                            viewModel.getProduts()
                        }
                    ) {
                        Text("Hola Papu!")
                    }
                    AnimatedVisibility(showContent) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll( state= rememberScrollState(), enabled = true ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(painterResource(Res.drawable.compose_multiplatform), null)
                            Text("> {cuac.responseData} <")

                        }
                    }
                }
            },
            bottomBar = {

            }
        )

    }
}