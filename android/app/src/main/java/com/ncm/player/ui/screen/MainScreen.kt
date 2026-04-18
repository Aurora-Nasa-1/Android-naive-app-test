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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import com.ncm.player.R
import com.ncm.player.util.ImageUtils
import com.ncm.player.model.Song
import com.ncm.player.model.Playlist
import com.ncm.player.model.UserProfile
import com.ncm.player.ui.component.UserAccountDialog
import com.ncm.player.ui.component.SongItem
import com.ncm.player.ui.component.SongCard
import com.ncm.player.ui.component.PlaylistItem
import com.ncm.player.ui.component.ExpressiveShapes

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(
    recommendedSongs: List<Song>,
    recommendedPlaylists: List<Playlist> = emptyList(),
    userPlaylists: List<Playlist>,
    userProfile: UserProfile?,
    versionName: String,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onPersonalFmClick: () -> Unit,
    onHeartbeatClick: () -> Unit,
    onLiveSortClick: () -> Unit,
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
    val activity = remember(context) {
        var c = context
        while (c is ContextWrapper) {
            if (c is ComponentActivity) break
            c = c.baseContext
        }
        c as? ComponentActivity
    }
    val windowSizeClass = if (activity != null) calculateWindowSizeClass(activity) else null
    val widthClass = windowSizeClass?.widthSizeClass ?: WindowWidthSizeClass.Compact

    var showAccountDialog by remember { mutableStateOf(false) }

    if (showAccountDialog) {
        UserAccountDialog(
            userProfile = userProfile,
            versionName = versionName,
            onDismiss = { showAccountDialog = false },
            onNavigateToLogs = {
                showAccountDialog = false
                onNavigateToLogs()
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.good_day), fontWeight = FontWeight.Normal) },
                actions = {
                    actions()
                    IconButton(onClick = onNavigateToMessages) { Icon(Icons.Default.Email, null) }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, null) }
                    IconButton(onClick = { showAccountDialog = true }) {
                        // Use a custom shape (extraLarge from updated theme is 32dp, which matches avatar)
                        Surface(modifier = Modifier.size(32.dp).clip(MaterialTheme.shapes.extraLarge), color = MaterialTheme.colorScheme.primaryContainer) {
                            if (userProfile?.avatarUrl != null) {
                                AsyncImage(model = userProfile.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.Person, null, modifier = Modifier.padding(4.dp))
                            }
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding() + bottomContentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                val gridColumns = when (widthClass) {
                    WindowWidthSizeClass.Expanded -> 4
                    WindowWidthSizeClass.Medium -> 3
                    else -> 2
                }
                Column {
                    val displayPlaylists = userPlaylists.take(gridColumns * 3)
                    val itemsToRender = mutableListOf<@Composable (Modifier) -> Unit>()
                    itemsToRender.add { m -> QuickAccessCard("Private FM", { Icon(Icons.Default.Radio, null, tint = MaterialTheme.colorScheme.primary) }, onPersonalFmClick, m) }
                    itemsToRender.add { m -> QuickAccessCard("Heartbeat", { Icon(Icons.Default.AutoGraph, null, tint = MaterialTheme.colorScheme.secondary) }, onHeartbeatClick, m) }
                    itemsToRender.add { m -> QuickAccessCard(stringResource(R.string.live_sort), { Icon(Icons.Default.AutoGraph, null, tint = MaterialTheme.colorScheme.tertiary) }, onLiveSortClick, m) }
                    displayPlaylists.forEach { p -> itemsToRender.add { m -> PlaylistQuickCard(p, { onPlaylistClick(p) }, m) } }

                    com.ncm.player.ui.component.VerticalGrid(
                        items = itemsToRender,
                        columns = gridColumns,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) { item -> item(Modifier.weight(1f)) }
                }
            }

            if (recommendedPlaylists.isNotEmpty()) {
                item { Text("Recommended Playlists", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Normal) }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 16.dp)) {
                        items(items = recommendedPlaylists, key = { "rec_pl_${it.id}" }) { p ->
                            Column(modifier = Modifier.width(160.dp).clickable { onPlaylistClick(p) }) {
                                AsyncImage(
                                    model = ImageUtils.getResizedImageUrl(p.coverImgUrl ?: "", 400),
                                    contentDescription = null,
                                    modifier = Modifier.size(160.dp).clip(MaterialTheme.shapes.large),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            item { Text(stringResource(R.string.made_for_you), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Normal) }

            item {
                if (widthClass != WindowWidthSizeClass.Compact) {
                    val columns = if (widthClass == WindowWidthSizeClass.Expanded) 5 else 4
                    com.ncm.player.ui.component.VerticalGrid(
                        items = recommendedSongs.take(10),
                        columns = columns,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) { s -> SongCard(song = s, onClick = { onSongClick(s) }, modifier = Modifier.weight(1f)) }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 16.dp)) {
                        items(items = recommendedSongs.take(10), key = { "rec_${it.id}" }) { s -> SongCard(s, onClick = { onSongClick(s) }) }
                    }
                }
            }


            item { Text(stringResource(R.string.recently_played), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Normal) }

            item {
                val columns = if (widthClass != WindowWidthSizeClass.Compact) 2 else 1
                val items = recommendedSongs.drop(10).take(if (columns > 1) 10 else 5)

                com.ncm.player.ui.component.IndexedVerticalGrid(
                    items = items,
                    columns = columns,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) { _, s, rowIndex, totalRows ->
                    SongItem(
                        song = s,
                        isFavorite = favoriteSongs.contains(s.id),
                        isDownloaded = completedSongs.contains(s.id),
                        onLikeClick = { onLikeClick(s) },
                        onClick = { onSongClick(s) },
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveShapes.calculateShape(rowIndex, totalRows)
                    )
                }
            }
        }
    }
}

@Composable
fun QuickAccessCard(title: String, icon: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = modifier.height(56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            icon()
            Spacer(Modifier.width(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PlaylistQuickCard(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = modifier.height(56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = ImageUtils.getResizedImageUrl(playlist.coverImgUrl ?: "", 120), contentDescription = null, modifier = Modifier.fillMaxHeight().aspectRatio(1f).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Text(text = playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 8.dp))
        }
    }
}
