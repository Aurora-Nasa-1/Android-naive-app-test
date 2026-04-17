package com.ncm.player.ui.screen

import com.ncm.player.ui.component.WavyCircularProgressIndicator
import com.ncm.player.ui.component.WavySlider
import com.ncm.player.model.LyricLine
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ncm.player.R
import coil3.compose.AsyncImage
import com.ncm.player.util.ImageUtils
import com.ncm.player.model.Song
import com.ncm.player.ui.component.QueueBottomSheet
import com.ncm.player.ui.component.CommentBottomSheet
import com.ncm.player.ui.component.SleepTimerBottomSheet
import androidx.compose.foundation.isSystemInDarkTheme
import com.ncm.player.ui.component.LyricContent
import com.ncm.player.ui.theme.createCustomColorScheme
import com.ncm.player.viewmodel.PlaybackViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun PlayerScreen(
    song: Song?,
    lyrics: List<LyricLine> = emptyList(),
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    currentPosition: Long = 0L,
    duration: Long = 0L,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    shuffleMode: Boolean = false,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    allPlaylists: List<com.ncm.player.model.Playlist> = emptyList(),
    onLikeClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onCommentClick: () -> Unit = {},
    onLyricClick: () -> Unit = {},
    onAddToPlaylist: (String, Long) -> Unit = { _, _ -> },
    queue: List<Song> = emptyList(),
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onRemoveQueueItem: (Int) -> Unit = { _ -> },
    onClearQueue: () -> Unit = {},
    qualityWifi: String = "Unknown",
    qualityCellular: String = "Unknown",
    sampleRate: Int = 0,
    bitrate: Int = 0,
    hotComments: List<com.ncm.player.model.Comment> = emptyList(),
    newestComments: List<com.ncm.player.model.Comment> = emptyList(),
    commentTotal: Int = 0,
    isCommentsLoading: Boolean = false,
    hasMoreComments: Boolean = true,
    commentSortType: Int = 1,
    onLoadMoreComments: () -> Unit = {},
    onLikeComment: (com.ncm.player.model.Comment) -> Unit = {},
    onReplyComment: (com.ncm.player.model.Comment) -> Unit = {},
    onPostComment: (String) -> Unit = {},
    onAvatarClick: (Long) -> Unit = {},
    onDislikeClick: () -> Unit = {},
    onCommentSortChange: (Int) -> Unit = {},
    onViewFloorClick: (com.ncm.player.model.Comment) -> Unit = {},
    floorComments: List<com.ncm.player.model.Comment> = emptyList(),
    floorCommentTotal: Int = 0,
    floorHasMore: Boolean = false,
    onLoadMoreFloor: (com.ncm.player.model.Comment) -> Unit = {},
    onDismissFloor: () -> Unit = {},
    activeParentComment: com.ncm.player.model.Comment? = null,
    sleepTimerRemaining: Long = 0L,
    onSetSleepTimer: (Int) -> Unit = {},
    useCoverColor: Boolean = false,
    useFluidBackground: Boolean = false,
    coverColor: Int? = null,
    onBackPressed: () -> Unit
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
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val isWideScreen = windowSizeClass?.let { it.widthSizeClass != WindowWidthSizeClass.Compact } ?: false

    if (song == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

            WavyCircularProgressIndicator()
        }
        return
    }

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showQueueBottomSheet by remember { mutableStateOf(false) }
    var showCommentBottomSheet by remember { mutableStateOf(false) }
    var showSleepTimerBottomSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSongInfoDialog by remember { mutableStateOf(false) }
    var showQualityInfoDialog by remember { mutableStateOf(false) }

    if (showSongInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSongInfoDialog = false },
            title = { Text(stringResource(R.string.song_info)) },
            text = {
                Column {
                    Text(stringResource(R.string.title_label, song.name))
                    Text(stringResource(R.string.artist_label, song.artist))
                    Text(stringResource(R.string.album_label, song.album))
                    Text(stringResource(R.string.song_id_label, song.id))
                }
            },
            confirmButton = {
                TextButton(onClick = { showSongInfoDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showQualityInfoDialog) {
        AlertDialog(
            onDismissRequest = { showQualityInfoDialog = false },
            title = { Text(stringResource(R.string.quality_info)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.wifi_quality, qualityWifi), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.cellular_quality, qualityCellular), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                    Text(stringResource(R.string.playback_stats), style = MaterialTheme.typography.titleSmall)
                    if (sampleRate > 0) {
                        Text(stringResource(R.string.sample_rate, sampleRate), style = MaterialTheme.typography.bodyMedium)
                    } else if (sampleRate == 0) {
                        Text(stringResource(R.string.sample_rate_na), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }

                    if (bitrate > 0) {
                        Text(stringResource(R.string.bitrate, bitrate / 1000), style = MaterialTheme.typography.bodyMedium)
                    } else if (bitrate == 0) {
                        Text(stringResource(R.string.bitrate_na), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityInfoDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showAddToPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text(stringResource(R.string.add_to_playlist)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(allPlaylists) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            modifier = Modifier.clickable {
                                onAddToPlaylist(song.id, p.id)
                                showAddToPlaylistDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showQueueBottomSheet) {
        QueueBottomSheet(
            queue = queue,
            currentSongId = song.id,
            onMove = onMoveQueueItem,
            onRemove = onRemoveQueueItem,
            onClear = onClearQueue,
            onClose = { showQueueBottomSheet = false }
        )
    }

    if (showCommentBottomSheet) {
        CommentBottomSheet(
            hotComments = hotComments,
            newestComments = newestComments,
            totalCount = commentTotal,
            isLoading = isCommentsLoading,
            hasMore = hasMoreComments,
            currentSort = commentSortType,
            onLoadMore = onLoadMoreComments,
            onLikeClick = onLikeComment,
            onReplyClick = onReplyComment,
            onPostComment = onPostComment,
            onAvatarClick = onAvatarClick,
            onSortChange = onCommentSortChange,
            onViewFloorClick = onViewFloorClick,
            useCoverColor = useCoverColor,
            coverColor = coverColor,
            onDismiss = { showCommentBottomSheet = false }
        )
    }

    if (activeParentComment != null) {
        com.ncm.player.ui.component.FloorCommentBottomSheet(
            parentComment = activeParentComment,
            replies = floorComments,
            totalCount = floorCommentTotal,
            isLoading = isCommentsLoading,
            hasMore = floorHasMore,
            onLoadMore = { onLoadMoreFloor(activeParentComment) },
            onLikeClick = onLikeComment,
            onReplyClick = onReplyComment,
            onPostComment = { onPostComment(it) /* TODO: Support floor reply */ },
            onAvatarClick = onAvatarClick,
            useCoverColor = useCoverColor,
            coverColor = coverColor,
            onDismiss = onDismissFloor
        )
    }

    if (showSleepTimerBottomSheet) {
        SleepTimerBottomSheet(
            remainingTime = sleepTimerRemaining,
            onSetTimer = onSetSleepTimer,
            onDismiss = { showSleepTimerBottomSheet = false }
        )
    }

    val playerColorScheme = if (useCoverColor && coverColor != null) {
        createCustomColorScheme(coverColor, isSystemInDarkTheme())
    } else {
        MaterialTheme.colorScheme
    }

    MaterialTheme(colorScheme = playerColorScheme) {
        val bgBrush = if (useCoverColor && coverColor != null) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(coverColor).copy(alpha = 0.5f),
                    MaterialTheme.colorScheme.surface
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.surface
                )
            )
        }

        val view = androidx.compose.ui.platform.LocalView.current
        if (!view.isInEditMode) {
            val isDarkTheme = isSystemInDarkTheme()
            val luminance = coverColor?.let { androidx.core.graphics.ColorUtils.calculateLuminance(it) } ?: 0.0
            val isAppearanceLightStatusBars = if (useFluidBackground) {
                !isDarkTheme
            } else {
                !isDarkTheme && (luminance > 0.5)
            }

            SideEffect {
                val window = (view.context as android.app.Activity).window
                androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isAppearanceLightStatusBars
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (useCoverColor && coverColor != null && useFluidBackground) {
                com.ncm.player.ui.component.FluidBackground(
                    color = Color(coverColor),
                    isDark = isSystemInDarkTheme()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(bgBrush))
            }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = { Text(stringResource(R.string.playing)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: Album Art & Info & Controls
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .aspectRatio(1f),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.large,
                            shadowElevation = 8.dp
                        ) {
                            if (song.albumArtUrl != null) {
                                AsyncImage(
                                    model = ImageUtils.getResizedImageUrl(song.albumArtUrl, 600),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(96.dp)
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        song.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        song.artist,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { song.artistId?.let { onArtistClick(it) } }
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        onCommentClick()
                                        showCommentBottomSheet = true
                                    }, modifier = Modifier.size(44.dp)) {
                                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comments", modifier = Modifier.size(28.dp))
                                    }
                                    IconButton(onClick = onLikeClick, modifier = Modifier.size(44.dp)) {
                                        AnimatedContent(
                                            targetState = isFavorite,
                                            label = "LikeAnimation",
                                            transitionSpec = {
                                                scaleIn(animationSpec = spring(Spring.DampingRatioMediumBouncy)) togetherWith
                                                scaleOut()
                                            }
                                        ) { targetFavorite ->
                                            Icon(
                                                if (targetFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Like",
                                                modifier = Modifier.size(32.dp),
                                                tint = if (targetFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                            WavySlider(
                                value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                                onValueChange = { onSeek((it * duration).toLong()) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.onSurface,
                                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatTime(currentPosition), style = MaterialTheme.typography.labelSmall)
                                Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
                            }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp).clickable { showQualityInfoDialog = true }
                        ) {
                            if (bitrate > 0) {
                                Surface(
                                    color = if (bitrate > 800000) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = if (bitrate > 800000) "HI-RES" else "LOSSLESS",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (bitrate > 800000) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Text(
                                    text = "${bitrate / 1000} kbps" + (if (sampleRate > 0) " / ${sampleRate / 1000} kHz" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onShuffleClick, modifier = Modifier.requiredSize(48.dp)) {
                                    Icon(
                                        Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        modifier = Modifier.size(26.dp),
                                        tint = if (shuffleMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                }
                                IconButton(onClick = onSkipPrevious, modifier = Modifier.requiredSize(56.dp)) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                                }
                                FloatingActionButton(
                                    onClick = onPlayPause,
                                    modifier = Modifier.requiredSize(64.dp),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.surface
                                ) {
                                    if (isBuffering) {
                                        WavyCircularProgressIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.surface)
                                    } else {
                                        Icon(
                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                IconButton(onClick = onSkipNext, modifier = Modifier.requiredSize(56.dp)) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                                }
                                IconButton(onClick = onRepeatClick, modifier = Modifier.requiredSize(48.dp)) {
                                    val icon = when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                        else -> Icons.Default.Repeat
                                    }
                                    Icon(
                                        icon,
                                        contentDescription = "Repeat",
                                        modifier = Modifier.size(26.dp),
                                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(onClick = { showQueueBottomSheet = true }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", modifier = Modifier.size(28.dp))
                                }
                                Box {
                                    IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(44.dp)) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(28.dp))
                                    }
                                    DropdownMenu(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.add_to_playlist)) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                                            onClick = {
                                                showAddToPlaylistDialog = true
                                                showMoreMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (isDownloaded) stringResource(R.string.downloaded) else "Download") },
                                            leadingIcon = { Icon(if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download, null) },
                                            onClick = {
                                                onDownloadClick()
                                                showMoreMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sleep_timer)) },
                                            leadingIcon = { Icon(Icons.Default.Timer, null) },
                                            onClick = {
                                                showSleepTimerBottomSheet = true
                                                showMoreMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.song_info)) },
                                            leadingIcon = { Icon(Icons.Default.Info, null) },
                                            onClick = {
                                                showSongInfoDialog = true
                                                showMoreMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.quality_info)) },
                                            leadingIcon = { Icon(Icons.Default.HighQuality, null) },
                                            onClick = {
                                                showQualityInfoDialog = true
                                                showMoreMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Share Song") },
                                            leadingIcon = { Icon(Icons.Default.Share, null) },
                                            onClick = {
                                                val shareIntent = android.content.Intent().apply {
                                                    action = android.content.Intent.ACTION_SEND
                                                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out this song: ${song.name} by ${song.artist}\nhttps://music.163.com/song?id=${song.id}")
                                                    type = "text/plain"
                                                }
                                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Song"))
                                                showMoreMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.not_interested)) },
                                            leadingIcon = { Icon(Icons.Default.Block, null) },
                                            onClick = {
                                                onDislikeClick()
                                                showMoreMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right Side: Lyrics
                    Box(modifier = Modifier.weight(1.3f)) {
                        LyricContent(
                            lyrics = lyrics,
                            currentPosition = currentPosition,
                            contentPadding = PaddingValues(vertical = 120.dp, horizontal = 0.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Album Art
                    Surface(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 8.dp
                    ) {
                        if (song.albumArtUrl != null) {
                            AsyncImage(
                                model = ImageUtils.getResizedImageUrl(song.albumArtUrl, 600),
                                contentDescription = null,
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(128.dp)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    song.artist,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable { song.artistId?.let { onArtistClick(it) } }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    onCommentClick()
                                    showCommentBottomSheet = true
                                }) {
                                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comments", modifier = Modifier.size(28.dp))
                                }
                                IconButton(onClick = onLikeClick) {
                                    AnimatedContent(
                                        targetState = isFavorite,
                                        label = "LikeAnimationMobile",
                                        transitionSpec = {
                                            scaleIn(animationSpec = spring(Spring.DampingRatioMediumBouncy)) togetherWith
                                            scaleOut()
                                        }
                                    ) { targetFavorite ->
                                        Icon(
                                            if (targetFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Like",
                                            modifier = Modifier.size(32.dp),
                                            tint = if (targetFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Progress Bar
                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                        WavySlider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { onSeek((it * duration).toLong()) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.onSurface,
                                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(currentPosition), style = MaterialTheme.typography.labelSmall)
                            Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp).clickable { showQualityInfoDialog = true }
                        ) {
                            if (bitrate > 0) {
                                Surface(
                                    color = if (bitrate > 800000) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = if (bitrate > 800000) "HI-RES" else "LOSSLESS",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (bitrate > 800000) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Text(
                                    text = "${bitrate / 1000} kbps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onShuffleClick) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                modifier = Modifier.size(28.dp),
                                tint = if (shuffleMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = onSkipPrevious) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        FloatingActionButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(72.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        ) {
                            if (isBuffering) {
                                WavyCircularProgressIndicator(modifier = Modifier.size(40.dp), color = MaterialTheme.colorScheme.surface)
                            } else {
                                AnimatedContent(
                                    targetState = isPlaying,
                                    label = "PlayPauseAnimationMobile",
                                    transitionSpec = {
                                            // MD3E Spring based transition
                                            (fadeIn(animationSpec = spring()) + scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))) togetherWith
                                            (fadeOut(animationSpec = spring()) + scaleOut(animationSpec = spring()))
                                    }
                                ) { targetPlaying ->
                                    Icon(
                                        if (targetPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onSkipNext) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        IconButton(onClick = onRepeatClick) {
                            val icon = when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            }
                            Icon(
                                icon,
                                contentDescription = "Repeat",
                                modifier = Modifier.size(28.dp),
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onLyricClick) {
                            Icon(Icons.Default.Lyrics, contentDescription = "Lyrics")
                        }
                        IconButton(onClick = { showQueueBottomSheet = true }) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.add_to_playlist)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                                        onClick = {
                                            showAddToPlaylistDialog = true
                                            showMoreMenu = false
                                        }
                                    )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.song_info)) },
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                    onClick = {
                                        showSongInfoDialog = true
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isDownloaded) stringResource(R.string.downloaded) else "Download") },
                                    leadingIcon = { Icon(if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download, null) },
                                    onClick = {
                                        onDownloadClick()
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sleep_timer)) },
                                    leadingIcon = { Icon(Icons.Default.Timer, null) },
                                    onClick = {
                                        showSleepTimerBottomSheet = true
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.quality_info)) },
                                    leadingIcon = { Icon(Icons.Default.HighQuality, null) },
                                    onClick = {
                                        showQualityInfoDialog = true
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Song") },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = {
                                        val shareIntent = android.content.Intent().apply {
                                            action = android.content.Intent.ACTION_SEND
                                            putExtra(android.content.Intent.EXTRA_TEXT, "Check out this song: ${song.name} by ${song.artist}\nhttps://music.163.com/song?id=${song.id}")
                                            type = "text/plain"
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Song"))
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.not_interested)) },
                                    leadingIcon = { Icon(Icons.Default.Block, null) },
                                    onClick = {
                                        onDislikeClick()
                                        showMoreMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
