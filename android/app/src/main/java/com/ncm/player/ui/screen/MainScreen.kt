package com.ncm.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ncm.player.model.Song
import com.ncm.player.model.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    recommendedSongs: List<Song>,
    userPlaylists: List<Playlist>,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onLikeClick: (Song) -> Unit,
    favoriteSongs: List<String>,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("NCM Player") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Text(
                    "Daily Recommendations",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(recommendedSongs) { song ->
                SongItem(
                    song = song,
                    isFavorite = favoriteSongs.contains(song.id),
                    onLikeClick = { onLikeClick(song) },
                    onClick = { onSongClick(song) }
                )
            }

            item {
                Text(
                    "My Playlists",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(userPlaylists) { playlist ->
                PlaylistItem(playlist, onClick = { onPlaylistClick(playlist) })
            }
        }
    }
}

@Composable
fun SongItem(
    song: Song,
    isFavorite: Boolean = false,
    onLikeClick: () -> Unit = {},
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(song.name) },
        supportingContent = { Text(song.artist) },
        leadingContent = {
            Surface(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                if (song.albumArtUrl != null) {
                    AsyncImage(
                        model = song.albumArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onLikeClick) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun PlaylistItem(playlist: Playlist, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(playlist.name) },
        supportingContent = { Text("${playlist.trackCount} songs") },
        leadingContent = {
            Surface(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                if (playlist.coverImgUrl != null) {
                    AsyncImage(
                        model = playlist.coverImgUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}
