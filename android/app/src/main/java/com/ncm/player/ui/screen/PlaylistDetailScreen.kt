package com.ncm.player.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ncm.player.model.Playlist
import com.ncm.player.model.Song

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    songs: List<Song>,
    favoriteSongs: List<String>,
    isLoading: Boolean,
    onSongClick: (Song) -> Unit,
    onPlayAllClick: (List<Song>) -> Unit,
    onQueueAllClick: (List<Song>) -> Unit,
    onLikeClick: (Song) -> Unit,
    onBackPressed: () -> Unit
) {
    val scrollState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(scrollState)
    var selectedSongs by remember { mutableStateOf(setOf<String>()) }
    var isSelectionMode by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedSongs = emptySet()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedSongs.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedSongs = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val selectedList = songs.filter { selectedSongs.contains(it.id) }
                            onQueueAllClick(selectedList)
                            isSelectionMode = false
                            selectedSongs = emptySet()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Queue")
                        }
                    }
                )
            } else {
                LargeTopAppBar(
                    title = { Text(playlist.name) },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!isSelectionMode) {
                    item {
                        PlaylistHeader(playlist, onPlayAllClick = { onPlayAllClick(songs) })
                    }
                }
                items(
                    items = songs,
                    key = { it.id },
                    contentType = { "song" }
                ) { song ->
                    val isSelected = selectedSongs.contains(song.id)
                    ListItem(
                        headlineContent = { Text(song.name) },
                        supportingContent = { Text(song.artist) },
                        leadingContent = {
                            if (isSelectionMode) {
                                Checkbox(checked = isSelected, onCheckedChange = {
                                    selectedSongs = if (it) selectedSongs + song.id else selectedSongs - song.id
                                })
                            } else {
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
                            }
                        },
                        trailingContent = {
                            if (!isSelectionMode) {
                                IconButton(onClick = { onLikeClick(song) }) {
                                    Icon(
                                        if (favoriteSongs.contains(song.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Like",
                                        tint = if (favoriteSongs.contains(song.id)) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedSongs = if (isSelected) selectedSongs - song.id else selectedSongs + song.id
                                    } else {
                                        onSongClick(song)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedSongs = setOf(song.id)
                                    }
                                }
                            )
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistHeader(playlist: Playlist, onPlayAllClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(200.dp),
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 8.dp
        ) {
            if (playlist.coverImgUrl != null) {
                AsyncImage(
                    model = playlist.coverImgUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(64.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = playlist.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${playlist.trackCount} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = {}) {
                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(32.dp))
            }

            FloatingActionButton(
                onClick = onPlayAllClick,
                shape = androidx.compose.foundation.shape.CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play All", modifier = Modifier.size(32.dp))
            }
        }
    }
}
