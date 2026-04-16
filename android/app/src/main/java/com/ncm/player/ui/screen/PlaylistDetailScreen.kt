package com.ncm.player.ui.screen

import com.ncm.player.ui.component.WavyCircularProgressIndicator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ncm.player.R
import coil3.compose.AsyncImage
import com.ncm.player.util.ImageUtils
import com.ncm.player.model.Playlist
import com.ncm.player.model.Song
import com.ncm.player.ui.component.SongItem

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    songs: List<Song>,
    favoriteSongs: List<String>,
    allPlaylists: List<Playlist>,
    isLoading: Boolean,
    onSongClick: (Song) -> Unit,
    onPlayAllClick: (List<Song>) -> Unit,
    onQueueAllClick: (List<Song>) -> Unit,
    onLikeClick: (Song) -> Unit,
    onAddToPlaylist: (List<String>, Long) -> Unit,
    onRemoveFromPlaylist: (List<String>) -> Unit,
    onBatchDownload: (List<Song>) -> Unit,
    completedSongs: Set<String> = emptySet(),
    isFirstDownload: Boolean = false,
    onDownloadQualityChange: (String) -> Unit = {},
    onSortChange: (String) -> Unit = {},
    onBackPressed: () -> Unit,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val scrollState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(scrollState)
    var selectedSongs by remember { mutableStateOf(setOf<String>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var pendingDownloadSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedSongs = emptySet()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selectedSongs.size)) },
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
                        IconButton(onClick = { showAddToPlaylistDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add to Playlist")
                        }
                        IconButton(onClick = {
                            onRemoveFromPlaylist(selectedSongs.toList())
                            isSelectionMode = false
                            selectedSongs = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove from Playlist")
                        }
                    }
                )
            } else {
                LargeTopAppBar(
                    title = {
                        if (isLoading && playlist.name == "Loading...") {
                            Text(stringResource(R.string.connecting), style = MaterialTheme.typography.headlineLarge)
                        } else {
                            Text(playlist.name, style = MaterialTheme.typography.headlineLarge)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                    actions = {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.sort_by_name)) }, onClick = { onSortChange("name"); showSortMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.sort_by_artist)) }, onClick = { onSortChange("artist"); showSortMenu = false })
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { innerPadding ->
        if (isLoading && songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                 WavyCircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + bottomContentPadding.calculateBottomPadding()
                )
            ) {
                if (!isSelectionMode) {
                    item { PlaylistHeader(playlist, onPlayAllClick = { onPlayAllClick(songs) }) }
                }
                items(items = songs, key = { it.id }) { song ->
                    val isSelected = selectedSongs.contains(song.id)
                    SongItem(
                        song = song,
                        isFavorite = favoriteSongs.contains(song.id),
                        isDownloaded = completedSongs.contains(song.id),
                        onLikeClick = if (!isSelectionMode) { { onLikeClick(song) } } else null,
                        onClick = {
                            if (isSelectionMode) {
                                selectedSongs = if (isSelected) selectedSongs - song.id else selectedSongs + song.id
                            } else {
                                onSongClick(song)
                            }
                        },
                        leadingContent = if (isSelectionMode) {
                            { Checkbox(checked = isSelected, onCheckedChange = {
                                selectedSongs = if (it) selectedSongs + song.id else selectedSongs - song.id
                            }) }
                        } else null,
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
        modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(modifier = Modifier.size(200.dp), shape = MaterialTheme.shapes.medium, shadowElevation = 8.dp) {
            if (playlist.coverImgUrl != null) {
                AsyncImage(model = ImageUtils.getResizedImageUrl(playlist.coverImgUrl, 400), contentDescription = null, contentScale = ContentScale.Crop)
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(64.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.songs_count, playlist.trackCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {}) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(32.dp)) }
            FloatingActionButton(onClick = onPlayAllClick, shape = androidx.compose.foundation.shape.CircleShape, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play All", modifier = Modifier.size(32.dp))
            }
        }
    }
}
