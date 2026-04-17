package com.ncm.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import com.ncm.player.ui.component.WavyLinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ncm.player.model.Song
import com.ncm.player.model.DownloadTask
import com.ncm.player.model.DownloadStatus
import com.ncm.player.manager.DownloadedSongMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackPressed: () -> Unit,
    onPlayLocalSong: (Song, android.net.Uri) -> Unit,
    downloadedSongs: List<DownloadedSongMetadata>,
    tasks: Map<String, DownloadTask> = emptyMap(),
    onCancelDownload: (String) -> Unit = {},
    onDeleteLocalSong: (android.net.Uri) -> Unit = {},
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
            )
        ) {
            val downloadingTasks = tasks.values.filter { it.status != DownloadStatus.COMPLETED }.toList()
            if (downloadingTasks.isNotEmpty()) {
                item { Text("Downloading", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 16.dp)) }
                itemsIndexed(downloadingTasks, key = { _, t -> t.song.id }) { index, task ->
                    val shape = when {
                        downloadingTasks.size == 1 -> MaterialTheme.shapes.large
                        index == 0 -> androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                        index == downloadingTasks.size - 1 -> androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                        else -> androidx.compose.ui.graphics.RectangleShape
                    }
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column {
                            ListItem(
                                headlineContent = { Text(task.song.name) },
                                supportingContent = {
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(task.song.artist, style = MaterialTheme.typography.bodySmall)
                                            Text(if (task.progress >= 0f) "${(task.progress * 100).toInt()}%" else "Connecting...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        WavyLinearProgressIndicator(
                                            progress = { if (task.progress >= 0f) task.progress else 0f },
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                        )
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = { onCancelDownload(task.song.id) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            if (index < downloadingTasks.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            if (downloadedSongs.isEmpty() && downloadingTasks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No downloaded songs found.")
                    }
                }
            } else if (downloadedSongs.isNotEmpty()) {
                item { Text("Downloaded", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 16.dp)) }
                itemsIndexed(downloadedSongs, key = { _, m -> m.song.id }) { index, metadata ->
                    val uri = if (metadata.filePath.startsWith("content://")) {
                        android.net.Uri.parse(metadata.filePath)
                    } else {
                        android.net.Uri.fromFile(java.io.File(metadata.filePath))
                    }
                    val shape = when {
                        downloadedSongs.size == 1 -> MaterialTheme.shapes.large
                        index == 0 -> androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                        index == downloadedSongs.size - 1 -> androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                        else -> androidx.compose.ui.graphics.RectangleShape
                    }
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column {
                            ListItem(
                                headlineContent = { Text(metadata.song.name) },
                                supportingContent = { Text(metadata.song.artist) },
                                leadingContent = {
                                    Surface(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.padding(8.dp))
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = { onDeleteLocalSong(uri) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                },
                                modifier = Modifier.clickable { onPlayLocalSong(metadata.song, uri) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            if (index < downloadedSongs.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
