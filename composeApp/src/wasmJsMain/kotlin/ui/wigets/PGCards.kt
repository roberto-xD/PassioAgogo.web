package ui.wigets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ui.pgmodels.PGDataCard

@Composable
fun ItemCard(
    dataCard : PGDataCard
){
    val rotated = remember { mutableStateOf(false) }

    val rotation = animateFloatAsState(
        targetValue = if(rotated.value) 180f else 0f,
        animationSpec = tween(500)
    )

    val animateFront = animateFloatAsState(
        targetValue = if(rotated.value.not()) 1f else 0f,
        animationSpec = tween(500)
    )

    val animateBack = animateFloatAsState(
        targetValue = if(rotated.value) 1f else 0f,
        animationSpec = tween(500)
    )

    Card(
        modifier = Modifier
            .height(220.dp)
            .fillMaxWidth()
            .padding(10.dp)
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 8 * density
            }
            .clickable {
                rotated.value = rotated.value.not()
            },
        shape = RoundedCornerShape(14.dp),
        elevation = 4.dp,
    ) {
        if(rotated.value.not()){
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .padding(8.dp)
            ){
                AsyncImage(
                    model = dataCard.urlImage,
                    contentDescription = "",
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = animateFront.value
                        }
                )
            }
        }else{
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .padding(8.dp)
            ){
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    Text(
                        text = dataCard.productTittle,
                        modifier = Modifier
                            .padding(10.dp)
                            .background(Color.White)
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = animateBack.value
                                rotationY = rotation.value
                            },
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Black,
                    )
                    Text(
                        text = dataCard.productRealPrice,
                        modifier = Modifier
                            .padding(3.dp)
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = animateBack.value
                                rotationY = rotation.value
                            },
                        fontSize = 15.sp,
                        textAlign = TextAlign.Justify,
                        color = Color.Black,
                    )
                }
            }
        }
    }
}