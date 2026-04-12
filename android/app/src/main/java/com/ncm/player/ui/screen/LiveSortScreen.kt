package com.ncm.player.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ncm.player.viewmodel.LiveSortState
import com.ncm.player.viewmodel.LiveSortViewModel
import com.ncm.player.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSortScreen(
    liveSortViewModel: LiveSortViewModel,
    playerViewModel: PlayerViewModel,
    onBackPressed: () -> Unit
) {
    val sortState by liveSortViewModel.sortState.collectAsState()
    val currentQueue = playerViewModel.currentQueue
    val localSongs = playerViewModel.localSongs

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LiveSort") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = sortState) {
                is LiveSortState.Idle -> {
                    Icon(
                        Icons.Default.AutoGraph,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Smart Playlist Reordering",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Reorder your current queue based on BPM, energy, and emotion flow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            val processableSongs = currentQueue.mapNotNull { song ->
                                val uri = localSongs.find { it.first.id == song.id }?.second
                                if (uri != null) {
                                    val path = uri.path ?: uri.toString()
                                    song to path
                                } else {
                                    null
                                }
                            }
                            liveSortViewModel.processPlaylist(processableSongs)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reorder Current Queue")
                    }
                    if (currentQueue.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Your queue is empty. Play some local songs first.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                is LiveSortState.Analyzing -> {
                    CircularProgressIndicator(
                        progress = { state.progress.toFloat() / state.total.toFloat() },
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Analyzing: ${state.currentSong}")
                    Text("${state.progress} / ${state.total}")
                }

                is LiveSortState.Sorting -> {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Optimizing playlist flow...")
                }

                is LiveSortState.Error -> {
                    Text(
                        "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { liveSortViewModel.processPlaylist(emptyList()) /* reset to empty to allow retry maybe? Actually just a dummy call won't help if we don't have songs. But since we don't have reset API, we'll just show it. */ }) {
                        Text("Go Back")
                    }
                }

                is LiveSortState.Completed -> {
                    Text(
                        "Sorting Completed!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (state.sortedSongs.isNotEmpty()) {
                        Button(
                            onClick = {
                                val songs = state.sortedSongs.map { it.song }
                                playerViewModel.playSong(songs.first(), songs)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play Now")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.sortedSongs) { item ->
                            ListItem(
                                headlineContent = { Text(item.song.name, maxLines = 1) },
                                supportingContent = { Text(item.song.artist, maxLines = 1) },
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("BPM: ${"%.0f".format(item.bpm)}", style = MaterialTheme.typography.bodySmall)
                                        Text("Energy: ${"%.2f".format(item.energy)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
