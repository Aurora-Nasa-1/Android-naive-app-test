package com.ncm.player.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

@Composable
fun NCMPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    themeMode: Int = 0, // 0: System, 1: Follow Cover, 2: Fixed
    followCoverApp: Boolean = false,
    seedColor: Int? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        themeMode == 1 && followCoverApp && seedColor != null -> {
            val color = Color(seedColor)
            if (darkTheme) {
                darkColorScheme(
                    primary = color,
                    primaryContainer = color.copy(alpha = 0.3f),
                    secondary = color.copy(alpha = 0.7f),
                    onPrimary = if (androidx.core.graphics.ColorUtils.calculateLuminance(seedColor) < 0.5) Color.White else Color.Black,
                    surface = Color(0xFF121212),
                    background = Color(0xFF121212)
                )
            } else {
                lightColorScheme(
                    primary = color,
                    primaryContainer = color.copy(alpha = 0.1f),
                    secondary = color.copy(alpha = 0.6f),
                    onPrimary = if (androidx.core.graphics.ColorUtils.calculateLuminance(seedColor) < 0.5) Color.White else Color.Black,
                    surface = Color(0xFFFDFDFD),
                    background = Color(0xFFFDFDFD)
                )
            }
        }
        themeMode == 2 -> { // Fixed (Reddish/Monet)
            val redSeed = Color(0xFFB71C1C)
            if (darkTheme) {
                darkColorScheme(
                    primary = redSeed,
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFF8B0000),
                    onPrimaryContainer = Color.White,
                    secondary = Color(0xFFFFCDD2),
                    onSecondary = Color.Black,
                    background = Color(0xFF1A1111),
                    surface = Color(0xFF1A1111)
                )
            } else {
                lightColorScheme(
                    primary = redSeed,
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFFFEBEE),
                    onPrimaryContainer = Color(0xFFB71C1C),
                    secondary = Color(0xFF757575),
                    onSecondary = Color.White,
                    background = Color(0xFFFFF8F8),
                    surface = Color(0xFFFFF8F8)
                )
            }
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    var finalColorScheme = colorScheme

    if (darkTheme && pureBlack) {
        finalColorScheme = finalColorScheme.copy(
            surface = Color.Black,
            background = Color.Black,
            surfaceVariant = Color.Black,
            secondaryContainer = Color(0xFF121212),
            onSecondaryContainer = Color.White,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainerLowest = Color.Black,
            onSurface = Color.White,
            onBackground = Color.White
        )
    }

    val expressiveShapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp)
    )

    val expressiveTypography = Typography(
        displayLarge = Typography().displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = Typography().displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = Typography().displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = Typography().headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = Typography().headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = Typography().titleSmall.copy(fontWeight = FontWeight.SemiBold)
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        shapes = expressiveShapes,
        typography = expressiveTypography,
        content = content
    )
}
