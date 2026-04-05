package com.ncm.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.ncm.player.util.ImageUtils
import com.ncm.player.model.Song
import com.ncm.player.model.Playlist
import com.ncm.player.model.UserProfile
import com.ncm.player.ui.component.UserAccountDialog

fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(
    recommendedSongs: List<Song>,
    userPlaylists: List<Playlist>,
    userProfile: UserProfile?,
    versionName: String,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onPersonalFmClick: () -> Unit,
    onHeartbeatClick: () -> Unit,
    onLikeClick: (Song) -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    unreadMessagesCount: Int = 0,
    favoriteSongs: List<String>,
    completedSongs: Set<String> = emptySet(),
    onNavigateToSettings: () -> Unit,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp),
    actions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val windowSizeClass = if (activity != null) calculateWindowSizeClass(activity) else null
    val widthClass = windowSizeClass?.widthSizeClass ?: WindowWidthSizeClass.Compact
    val isWideScreen = widthClass != WindowWidthSizeClass.Compact

    var showAccountDialog by remember { mutableStateOf(false) }

    if (showAccountDialog) {
        UserAccountDialog(
            userProfile = userProfile,
            versionName = versionName,
            onDismiss = { showAccountDialog = false },
            onNavigateToLogs = {
                showAccountDialog = false
                com.ncm.player.util.DebugLog.toast(context, "Entering Logs")
                onNavigateToLogs()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Good day",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    actions()
                    IconButton(onClick = onNavigateToMessages) {
                        Icon(Icons.Default.Email, contentDescription = "Messages")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { showAccountDialog = true }) {
                        Surface(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            if (userProfile?.avatarUrl != null) {
                                AsyncImage(
                                    model = userProfile.avatarUrl,
                                    contentDescription = "Account",
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Account",
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + bottomContentPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Quick Access Grid
            item {
                val gridColumns = when (widthClass) {
                    WindowWidthSizeClass.Expanded -> 4
                    WindowWidthSizeClass.Medium -> 3
                    else -> 2
                }
                Column {
                    val displayPlaylists = userPlaylists.take(gridColumns * 3)

                    // Simplified logic for quick access and playlists
                    val itemsToRender = mutableListOf<@Composable (Modifier) -> Unit>()
                    itemsToRender.add { modifier ->
                        QuickAccessCard("Private FM", { Icon(Icons.Default.Radio, null, tint = MaterialTheme.colorScheme.primary) }, onPersonalFmClick, modifier)
                    }
                    itemsToRender.add { modifier ->
                        QuickAccessCard("Heartbeat", { Icon(Icons.Default.AutoGraph, null, tint = MaterialTheme.colorScheme.secondary) }, onHeartbeatClick, modifier)
                    }
                    displayPlaylists.forEach { playlist ->
                        itemsToRender.add { modifier ->
                            PlaylistQuickCard(playlist, { onPlaylistClick(playlist) }, modifier)
                        }
                    }

                    for (i in itemsToRender.indices step gridColumns) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (j in 0 until gridColumns) {
                                if (i + j < itemsToRender.size) {
                                    itemsToRender[i + j](Modifier.weight(1f))
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            item(contentType = "header") {
                Text(
                    "Made For You",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                if (widthClass != WindowWidthSizeClass.Compact) {
                    // Grid for wide screens
                    val columns = if (widthClass == WindowWidthSizeClass.Expanded) 5 else 4
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (i in recommendedSongs.take(10).indices step columns) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                for (j in 0 until columns) {
                                    val index = i + j
                                    if (index < recommendedSongs.size && index < 10) {
                                        val song = recommendedSongs[index]
                                        SongCard(
                                            song = song,
                                            onClick = { onSongClick(song) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(
                            items = recommendedSongs.take(10),
                            key = { "rec_${it.id}" }
                        ) { song ->
                            SongCard(song, onClick = { onSongClick(song) })
                        }
                    }
                }
            }

            item(contentType = "header") {
                Text(
                    "Recently Played",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                val columns = if (widthClass != WindowWidthSizeClass.Compact) 2 else 1

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val items = recommendedSongs.drop(10).take(if (columns > 1) 10 else 5)
                    for (i in items.indices step columns) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (j in 0 until columns) {
                                val index = i + j
                                if (index < items.size) {
                                    val song = items[index]
                                    SongItem(
                                        song = song,
                                        isFavorite = favoriteSongs.contains(song.id),
                                        isDownloaded = completedSongs.contains(song.id),
                                        onLikeClick = { onLikeClick(song) },
                                        onClick = { onSongClick(song) },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else if (columns > 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickAccessCard(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            icon()
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PlaylistQuickCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageUtils.getResizedImageUrl(playlist.coverImgUrl ?: "", 120),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
fun SongCard(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .then(if (modifier == Modifier) Modifier.width(160.dp) else Modifier)
            .clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (song.albumArtUrl != null) {
                AsyncImage(
                    model = ImageUtils.getResizedImageUrl(song.albumArtUrl, 300),
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.padding(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = song.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SongItem(
    song: Song,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    onLikeClick: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                song.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDownloaded) {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = "Downloaded",
                        modifier = Modifier.size(14.dp).padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(56.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                if (song.albumArtUrl != null) {
                    AsyncImage(
                        model = ImageUtils.getResizedImageUrl(song.albumArtUrl, 180),
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp)
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
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}

@Composable
fun PlaylistItem(playlist: Playlist, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(playlist.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text("${playlist.trackCount} songs", style = MaterialTheme.typography.bodyMedium) },
        leadingContent = {
            Surface(
                modifier = Modifier.size(56.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                if (playlist.coverImgUrl != null) {
                    AsyncImage(
                        model = ImageUtils.getResizedImageUrl(playlist.coverImgUrl, 180),
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}
