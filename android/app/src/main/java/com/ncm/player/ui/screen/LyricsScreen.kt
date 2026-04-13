package com.ncm.player.ui.screen

import com.ncm.player.model.LyricLine
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ncm.player.viewmodel.PlayerViewModel
import com.ncm.player.ui.component.LyricContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    lyrics: List<LyricLine>,
    songName: String,
    currentPosition: Long,
    onBackPressed: () -> Unit
) {
    Scaffold(
        containerColor = Color.Black,
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
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A1A),
                            Color.Black
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            LyricContent(
                lyrics = lyrics,
                currentPosition = currentPosition
            )
        }
    }
}
