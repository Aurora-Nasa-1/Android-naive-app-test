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

fun createCustomColorScheme(seedColor: Int, isDark: Boolean, pureBlack: Boolean = false): ColorScheme {
    val color = Color(seedColor)
    val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(seedColor)
    val isDarkSeed = luminance < 0.5

    return if (isDark) {
        darkColorScheme(
            primary = color,
            onPrimary = if (isDarkSeed) Color.White else Color.Black,
            primaryContainer = color.copy(alpha = 0.3f),
            onPrimaryContainer = Color.White,
            secondary = color.copy(alpha = 0.5f),
            onSecondary = Color.White,
            secondaryContainer = color.copy(alpha = 0.2f),
            onSecondaryContainer = Color.White,
            surface = if (pureBlack) Color.Black else Color(0xFF121212),
            onSurface = Color.White,
            background = if (pureBlack) Color.Black else Color(0xFF121212),
            onBackground = Color.White,
            surfaceVariant = color.copy(alpha = 0.1f),
            onSurfaceVariant = Color.White
        )
    } else {
        lightColorScheme(
            primary = color,
            onPrimary = if (isDarkSeed) Color.White else Color.Black,
            primaryContainer = color.copy(alpha = 0.2f),
            onPrimaryContainer = color,
            secondary = color.copy(alpha = 0.6f),
            onSecondary = Color.White,
            secondaryContainer = color.copy(alpha = 0.1f),
            onSecondaryContainer = color,
            surface = Color(0xFFFDFDFD),
            onSurface = Color.Black,
            background = Color(0xFFFDFDFD),
            onBackground = Color.Black,
            surfaceVariant = color.copy(alpha = 0.05f),
            onSurfaceVariant = Color.Black
        )
    }
}

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
            createCustomColorScheme(seedColor, darkTheme, pureBlack)
        }
        themeMode == 2 -> { // Fixed (Reddish/Monet)
            createCustomColorScheme(0xFFB71C1C.toInt(), darkTheme, pureBlack)
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    var finalColorScheme = colorScheme

    if (darkTheme && pureBlack && themeMode == 0) {
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
