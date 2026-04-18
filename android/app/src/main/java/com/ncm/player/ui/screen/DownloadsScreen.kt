package com.ncm.player.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ncm.player.R
import com.ncm.player.ui.component.AppScaffold
import com.ncm.player.ui.component.WavyLinearProgressIndicator
import com.ncm.player.ui.component.ExpressiveShapes
import com.ncm.player.model.Song
import com.ncm.player.model.DownloadTask
import com.ncm.player.model.DownloadStatus
import com.ncm.player.manager.DownloadedSongMetadata

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
    AppScaffold(
        title = stringResource(R.string.downloads),
        onBackPressed = onBackPressed
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val downloadingTasks = tasks.values.filter { it.status != DownloadStatus.COMPLETED && it.song != null }.toList()
            if (downloadingTasks.isNotEmpty()) {
                item { Text("Downloading", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 16.dp)) }
                itemsIndexed(downloadingTasks) { index, task ->
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
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clip(ExpressiveShapes.calculateShape(index, downloadingTasks.size)),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }

            if (downloadedSongs.isEmpty() && downloadingTasks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No downloaded songs found.")
                    }
                }
            } else if (downloadedSongs.isNotEmpty()) {
                val validDownloadedSongs = downloadedSongs.filter { it.song != null }
                item { Text("Downloaded", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 16.dp)) }
                itemsIndexed(validDownloadedSongs) { index, metadata ->
                    val uri = if (metadata.filePath?.startsWith("content://") == true) {
                        android.net.Uri.parse(metadata.filePath)
                    } else {
                        android.net.Uri.fromFile(java.io.File(metadata.filePath ?: ""))
                    }
                    ListItem(
                        headlineContent = { Text(metadata.song.name) },
                        supportingContent = { Text(metadata.song.artist) },
                        leadingContent = {
                            Surface(modifier = Modifier.size(56.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.padding(12.dp))
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteLocalSong(uri) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clip(ExpressiveShapes.calculateShape(index, validDownloadedSongs.size))
                            .clickable { onPlayLocalSong(metadata.song, uri) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }
        }
    }
}
