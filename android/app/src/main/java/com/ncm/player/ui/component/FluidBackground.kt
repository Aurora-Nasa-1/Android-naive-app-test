package com.ncm.player.ui.component

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

// AGSL Fluid Shader inspired by Apple Music's dynamic background
private const val FLUID_SHADER = """
    uniform float2 iResolution;
    uniform float iTime;
    uniform float3 iColor1;
    uniform float3 iColor2;
    uniform float3 iColor3;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
    }

    float noise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);
        return mix(mix(hash(i + float2(0.0, 0.0)), hash(i + float2(1.0, 0.0)), u.x),
                   mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
    }

    float fbm(float2 p) {
        float v = 0.0;
        float a = 0.5;
        float2 shift = float2(100.0);
        float2x2 rot = float2x2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
        for (int i = 0; i < 5; ++i) {
            v += a * noise(p);
            p = rot * p * 2.0 + shift;
            a *= 0.5;
        }
        return v;
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;
        float2 p = uv * 3.0;

        float t = iTime * 0.2;
        float2 q = float2(fbm(p + t), fbm(p + 1.0));
        float2 r = float2(fbm(p + 1.0 * q + float2(1.7, 9.2) + 0.15 * t), fbm(p + 1.0 * q + float2(8.3, 2.8) + 0.126 * t));
        float f = fbm(p + r);

        float3 color = mix(iColor1, iColor2, clamp(f * f * 4.0, 0.0, 1.0));
        color = mix(color, iColor3, clamp(length(q), 0.0, 1.0));
        color = mix(color, iColor1, clamp(length(r.x), 0.0, 1.0));

        return half4(color * (f * f * f + 0.6 * f * f + 0.5 * f), 1.0);
    }
"""

@Composable
fun FluidBackground(
    color: Color,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AgslFluidBackground(color, modifier)
    } else {
        // Fallback for older versions: Simple animated gradient
        val infiniteTransition = rememberInfiniteTransition(label = "FluidFallback")
        val offset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(20000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Offset"
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0.2f), Color.Black),
                        start = androidx.compose.ui.geometry.Offset(offset, offset),
                        end = androidx.compose.ui.geometry.Offset(offset + 500f, offset + 500f)
                    )
                )
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AgslFluidBackground(
    color: Color,
    modifier: Modifier = Modifier
) {
    val shader = remember { RuntimeShader(FLUID_SHADER) }
    val infiniteTransition = rememberInfiniteTransition(label = "FluidShader")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(50000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Time"
    )

    // Derive complementary colors for fluid effect
    val color1 = color
    val color2 = remember(color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv[0] = (hsv[0] + 30) % 360
        hsv[1] = (hsv[1] * 0.8f).coerceIn(0f, 1f)
        Color.hsv(hsv[0], hsv[1], hsv[2])
    }
    val color3 = remember(color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv[0] = (hsv[0] - 30 + 360) % 360
        hsv[2] = (hsv[2] * 0.7f).coerceIn(0f, 1f)
        Color.hsv(hsv[0], hsv[1], hsv[2])
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("iColor1", color1.red, color1.green, color1.blue)
        shader.setFloatUniform("iColor2", color2.red, color2.green, color2.blue)
        shader.setFloatUniform("iColor3", color3.red, color3.green, color3.blue)

        drawRect(brush = ShaderBrush(shader))
    }
}
