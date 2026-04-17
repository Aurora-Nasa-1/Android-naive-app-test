package com.ncm.player.ui.screen

import com.ncm.player.model.LyricLine
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ncm.player.viewmodel.PlaybackViewModel
import com.ncm.player.ui.component.LyricContent
import com.ncm.player.ui.theme.createCustomColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    lyrics: List<LyricLine>,
    songName: String,
    currentPosition: Long,
    useCoverColor: Boolean = false,
    coverColor: Int? = null,
    onBackPressed: () -> Unit
) {
    val lyricsColorScheme = if (useCoverColor && coverColor != null) {
        createCustomColorScheme(coverColor, isSystemInDarkTheme())
    } else {
        MaterialTheme.colorScheme
    }

    MaterialTheme(colorScheme = lyricsColorScheme) {
        val bgBrush = if (useCoverColor && coverColor != null) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(coverColor).copy(alpha = 0.6f),
                    Color.Black
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1A1A1A),
                    Color.Black
                )
            )
        }

        val view = androidx.compose.ui.platform.LocalView.current
        if (!view.isInEditMode) {
            val isDarkTheme = isSystemInDarkTheme()
            val luminance = coverColor?.let { androidx.core.graphics.ColorUtils.calculateLuminance(it) } ?: 0.0
            val isAppearanceLightStatusBars = !isDarkTheme && (luminance > 0.5)

            SideEffect {
                val window = (view.context as android.app.Activity).window
                androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isAppearanceLightStatusBars
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    ),
                    title = { Text(songName, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    windowInsets = WindowInsets.statusBars
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgBrush)
                    .padding(innerPadding)
            ) {
                LyricContent(
                    lyrics = lyrics,
                    currentPosition = currentPosition
                )
            }
        }
    }
}
