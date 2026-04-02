package com.ncm.player.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncm.player.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    lyrics: List<PlayerViewModel.LyricLine>,
    songName: String,
    currentPosition: Long,
    onBackPressed: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val activeIndex = remember(currentPosition, lyrics) {
        val index = lyrics.indexOfLast { it.time <= currentPosition }
        if (index == -1) 0 else index
    }

    LaunchedEffect(activeIndex) {
        if (lyrics.isNotEmpty()) {
            listState.animateScrollToItem(
                index = (activeIndex - 2).coerceAtLeast(0),
                scrollOffset = -100
            )
        }
    }

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
            if (lyrics.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No lyrics available", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 200.dp, horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    itemsIndexed(lyrics) { index, line ->
                        val isActive = index == activeIndex
                        val alpha by animateFloatAsState(
                            targetValue = if (isActive) 1f else 0.4f,
                            animationSpec = tween(500)
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (isActive) 1.1f else 1.0f,
                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                },
                            horizontalAlignment = Alignment.Start
                        ) {
                            if (line.words != null && isActive) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    line.words.forEach { word ->
                                        val isWordActive = currentPosition >= word.beginTime
                                        val wordAlpha by animateFloatAsState(
                                            targetValue = if (isWordActive) 1f else 0.3f,
                                            animationSpec = tween(200)
                                        )
                                        Text(
                                            text = word.text,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 36.sp,
                                                fontSize = 28.sp
                                            ),
                                            color = Color.White.copy(alpha = wordAlpha),
                                            textAlign = TextAlign.Start
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 36.sp,
                                        fontSize = 28.sp
                                    ),
                                    color = Color.White,
                                    textAlign = TextAlign.Start
                                )
                            }
                            if (!line.romanization.isNullOrEmpty()) {
                                Text(
                                    text = line.romanization,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 20.sp,
                                        fontSize = 14.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                            if (!line.translation.isNullOrEmpty()) {
                                Text(
                                    text = line.translation,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        lineHeight = 28.sp,
                                        fontSize = 20.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
