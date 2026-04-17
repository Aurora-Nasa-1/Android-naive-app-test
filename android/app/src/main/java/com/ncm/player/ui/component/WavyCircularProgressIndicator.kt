package com.ncm.player.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun WavyCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavy_loading")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.size(48.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val strokeWidthPx = strokeWidth.toPx()
        val radius = (min(size.width, size.height) - strokeWidthPx * 4) / 2

        val path = Path()
        val segments = 150

        // MD3 Expressive: Rolling active wave with variable thickness/morphing feel
        val baseAmplitude = radius * 0.18f
        val morphAmplitude = sin(phase * 0.5f) * (radius * 0.05f)
        val amplitude = baseAmplitude + morphAmplitude
        val wavelengthMultiplier = 8

        for (i in 0..segments) {
            val angle = 2 * PI * i / segments
            // Morph the wave by modulating the amplitude and frequency slightly over time
            val wave = sin(angle * wavelengthMultiplier - phase) * amplitude
            val r = radius + wave

            val x = (center.x + r * cos(angle + Math.toRadians(rotation.toDouble()))).toFloat()
            val y = (center.y + r * sin(angle + Math.toRadians(rotation.toDouble()))).toFloat()

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
    }
}
