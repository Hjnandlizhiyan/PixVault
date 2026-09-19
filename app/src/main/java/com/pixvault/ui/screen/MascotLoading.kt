package com.pixvault.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pixvault.R
import kotlin.math.roundToInt

enum class MascotMood(val column: Int, val row: Int) {
    Welcome(0, 0),
    Thinking(1, 0),
    Working(2, 0),
    Private(0, 1),
    Surprised(1, 1),
    Sleepy(2, 1)
}

@Composable
fun MascotExpression(
    mood: MascotMood,
    modifier: Modifier = Modifier
) {
    val sheet = ImageBitmap.imageResource(R.drawable.mascot_expression_sheet)
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val cellWidth = sheet.width / 3
        val cellHeight = sheet.height / 2
        drawImage(
            image = sheet,
            srcOffset = IntOffset(mood.column * cellWidth, mood.row * cellHeight),
            srcSize = IntSize(cellWidth, cellHeight),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        )
    }
}

@Composable
fun MascotLoading(
    modifier: Modifier = Modifier,
    mood: MascotMood = MascotMood.Working
) {
    val transition = rememberInfiniteTransition(label = "mascotLoading")
    val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascotFloat"
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MascotExpression(
            mood = mood,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = offsetY.dp)
                .padding(horizontal = 20.dp)
        )
    }
}

@Composable
fun MascotMoodState(
    mood: MascotMood,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MascotExpression(mood = mood, modifier = Modifier.size(180.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
