package com.ncm.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ncm.player.util.ImageUtils
import com.ncm.player.model.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    userPlaylists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Your Library",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = bottomContentPadding.calculateBottomPadding())
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Downloads") },
                    supportingContent = { Text("Offline music") },
                    leadingContent = {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.padding(8.dp))
                        }
                    },
                    modifier = Modifier.clickable { onNavigateToDownloads() }
                )
            }
            items(
                items = userPlaylists,
                key = { it.id },
                contentType = { "playlist" }
            ) { playlist ->
                PlaylistItem(playlist, onClick = { onPlaylistClick(playlist) })
            }
        }
    }
}
