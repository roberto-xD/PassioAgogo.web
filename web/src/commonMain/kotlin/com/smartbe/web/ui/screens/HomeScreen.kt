package ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(){

//    val viewModel: HomeViewModel = getKoin().get()
//    val dataProduct by viewModel.productState.collectAsState()

    /*LaunchedEffect(Unit){
        viewModel.getProduts()
    }*/


    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
//                Navigation()
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
                            viewModel.getProduts("sexshop")
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
                            dataProduct.items?.forEach {
                                val dataCard = it.toPGDataCard()
                                ItemCard(dataCard)
                            }
                        }
                    }
                }
            },
            bottomBar = {

            }
        )

    }
}