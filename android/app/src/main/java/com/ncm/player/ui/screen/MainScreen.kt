package com.ncm.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
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
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.PlayArrow
import coil3.compose.AsyncImage
import com.ncm.player.R
import com.ncm.player.util.ImageUtils
import com.ncm.player.model.Song
import com.ncm.player.model.Playlist
import com.ncm.player.model.UserProfile
import com.ncm.player.ui.component.UserAccountDialog
import com.ncm.player.ui.component.SongItem
import com.ncm.player.ui.component.SongCard
import com.ncm.player.ui.component.ExpressiveShapes
import com.ncm.player.ui.component.AppScaffold

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
    val isLandscape = widthClass != WindowWidthSizeClass.Compact

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

    AppScaffold(
        title = { 
            Text(stringResource(R.string.good_day), fontWeight = FontWeight.Normal)
        },
        actions = {
            actions()
            IconButton(onClick = onNavigateToMessages) { Icon(Icons.Default.Email, null) }
            IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, null) }
            IconButton(onClick = { showAccountDialog = true }) {
                Surface(modifier = Modifier.size(32.dp).clip(CircleShape), color = MaterialTheme.colorScheme.surfaceVariant) {
                    if (userProfile?.avatarUrl != null) {
                        AsyncImage(model = userProfile.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp, 
                bottom = innerPadding.calculateBottomPadding() + bottomContentPadding.calculateBottomPadding() + 80.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(32.dp) // Large M3 expressive spacing
        ) {
            // "Favorites" Section - Circular items
            item {
                Column {
                    Text("Favorites", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Normal, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        item {
                            FavoriteCircleItem(
                                title = "Private FM",
                                icon = { Icon(Icons.Default.Radio, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = onPersonalFmClick
                            )
                        }
                        item {
                            FavoriteCircleItem(
                                title = "Heartbeat",
                                icon = { Icon(Icons.Default.AutoGraph, null, tint = MaterialTheme.colorScheme.secondary) },
                                onClick = onHeartbeatClick
                            )
                        }
                        item {
                            FavoriteCircleItem(
                                title = stringResource(R.string.live_sort),
                                icon = { Icon(Icons.Default.SwapVert, null, tint = MaterialTheme.colorScheme.tertiary) },
                                onClick = onLiveSortClick
                            )
                        }
                        val displayPlaylists = userPlaylists.take(5)
                        items(displayPlaylists) { p ->
                            FavoriteCircleItem(
                                title = p.name,
                                icon = { AsyncImage(model = ImageUtils.getResizedImageUrl(p.coverImgUrl ?: "", 150), contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop) },
                                onClick = { onPlaylistClick(p) }
                            )
                        }
                    }
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

            if (recommendedSongs.isNotEmpty()) {
                item {
                    // Intuitive Daily Recommended Songs Banner
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().clickable { onSongClick(recommendedSongs.first()) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "每日推荐歌曲",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "符合你口味的新鲜好歌",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            FilledIconButton(
                                onClick = { onSongClick(recommendedSongs.first()) },
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play All", modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }

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
fun FavoriteCircleItem(title: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp).clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                icon()
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title, 
            style = MaterialTheme.typography.labelLarge, 
            maxLines = 1, 
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ExpressiveListCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge, // Large 32dp+ rounded corners
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
                Icon(Icons.Default.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
