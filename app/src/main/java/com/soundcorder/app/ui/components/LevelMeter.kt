package com.soundcorder.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val BAR_COUNT = 56

/**
 * A scrolling bar meter fed by the recorder's peak amplitude. Purely decorative feedback that
 * something is being captured — no scientific claims.
 */
@Composable
fun LevelMeter(
    amplitudeProvider: () -> Int,
    running: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val levels = remember { mutableStateListOf<Float>().apply { repeat(BAR_COUNT) { add(0.04f) } } }

    LaunchedEffect(running) {
        while (isActive && running) {
            val norm = (amplitudeProvider() / 22000f).coerceIn(0.04f, 1f)
            levels.removeAt(0)
            levels.add(norm)
            delay(55)
        }
        if (!running) {
            for (i in levels.indices) levels[i] = 0.04f
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        val count = levels.size
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        val midY = size.height / 2f
        levels.forEachIndexed { i, level ->
            val barHeight = (size.height * level).coerceAtLeast(barWidth)
            val x = i * (barWidth + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, midY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
