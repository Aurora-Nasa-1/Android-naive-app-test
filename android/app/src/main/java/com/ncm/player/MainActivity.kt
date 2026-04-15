package com.ncm.player

import com.ncm.player.viewmodel.LiveSortViewModel
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.navigation.compose.*
import com.ncm.player.ui.component.*
import com.ncm.player.ui.screen.*
import com.ncm.player.ui.theme.NCMPlayerTheme
import com.ncm.player.viewmodel.*

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by viewModels()
    private val userViewModel: UserViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val socialViewModel: SocialViewModel by viewModels()
    private val downloadViewModel: DownloadViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val liveSortViewModel: LiveSortViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val useSideNav = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
            NCMPlayerTheme(pureBlack = settingsViewModel.pureBlackMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val context = LocalContext.current
                    LaunchedEffect(Unit) { playbackViewModel.initController(context) }
                    AppNavigation(loginViewModel, playbackViewModel, userViewModel, searchViewModel, socialViewModel, downloadViewModel, settingsViewModel, liveSortViewModel, useSideNav, intent)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    loginViewModel: LoginViewModel,
    playbackViewModel: PlaybackViewModel,
    userViewModel: UserViewModel,
    searchViewModel: SearchViewModel,
    socialViewModel: SocialViewModel,
    downloadViewModel: DownloadViewModel,
    settingsViewModel: SettingsViewModel,
    liveSortViewModel: LiveSortViewModel,
    useSideNav: Boolean,
    intent: Intent? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    LaunchedEffect(intent) {
        if (intent?.action == "ACTION_SHOW_PLAYER") {
            navController.navigate("player") { launchSingleTop = true }
        }
    }

    val isPlayerScreen = currentDestination?.route == "player" || currentDestination?.route == "lyrics"
    val showNav = loginViewModel.isLogged && !isPlayerScreen
    val hasBottomBar = showNav && !useSideNav
    val bottomBarHeight = 144.dp
    val density = LocalDensity.current
    val bottomBarHeightPx = with(density) { bottomBarHeight.toPx() }
    val bottomBarOffsetHeightPx = remember { mutableStateOf(0f) }
    val maxOffset = bottomBarHeightPx + WindowInsets.navigationBars.getBottom(density)

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                bottomBarOffsetHeightPx.value = (bottomBarOffsetHeightPx.value + available.y).coerceIn(-maxOffset, 0f)
                return Offset.Zero
            }
        }
    }

    val navItems = listOf(Triple("main", "Home", Icons.Filled.Home), Triple("search", "Search", Icons.Filled.Search), Triple("library", "Library", Icons.Filled.LibraryMusic))

    if (useSideNav && showNav) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(modifier = Modifier.width(240.dp)) {
                    Spacer(Modifier.height(24.dp))
                    Text("CNMDPlayer", modifier = Modifier.padding(horizontal = 28.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    navItems.forEach { (route, label, icon) ->
                        NavigationDrawerItem(label = { Text(label) }, icon = { Icon(icon, null) }, selected = currentDestination?.hierarchy?.any { it.route == route } == true, onClick = { navController.navigate(route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
                    }
                    Spacer(Modifier.weight(1f))
                    if (playbackViewModel.currentSong != null) {
                        Box(modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) {
                            BottomPlaybackBar(song = playbackViewModel.currentSong, isPlaying = playbackViewModel.isPlaying, onPlayPause = { playbackViewModel.togglePlayPause() }, onSkipNext = { playbackViewModel.skipNext() }, onSkipPrevious = { playbackViewModel.skipPrevious() }, onClick = { navController.navigate("player") { launchSingleTop = true } })
                        }
                    }
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        ) {
            AppMainContent(playbackViewModel, userViewModel, searchViewModel, socialViewModel, downloadViewModel, settingsViewModel, liveSortViewModel, PaddingValues(0.dp), isPlayerScreen, nestedScrollConnection, navController, loginViewModel, useSideNav, hasBottomBar, bottomBarHeight, context, currentDestination, bottomBarOffsetHeightPx, navItems)
        }
    } else {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            AppMainContent(playbackViewModel, userViewModel, searchViewModel, socialViewModel, downloadViewModel, settingsViewModel, liveSortViewModel, innerPadding, isPlayerScreen, nestedScrollConnection, navController, loginViewModel, useSideNav, hasBottomBar, bottomBarHeight, context, currentDestination, bottomBarOffsetHeightPx, navItems)
        }
    }
}

@Composable
fun AppMainContent(
    playbackViewModel: PlaybackViewModel,
    userViewModel: UserViewModel,
    searchViewModel: SearchViewModel,
    socialViewModel: SocialViewModel,
    downloadViewModel: DownloadViewModel,
    settingsViewModel: SettingsViewModel,
    liveSortViewModel: LiveSortViewModel,
    innerPadding: PaddingValues,
    isPlayerScreen: Boolean,
    nestedScrollConnection: NestedScrollConnection,
    navController: androidx.navigation.NavHostController,
    loginViewModel: LoginViewModel,
    useSideNav: Boolean,
    hasBottomBar: Boolean,
    bottomBarHeight: Dp,
    context: android.content.Context,
    currentDestination: androidx.navigation.NavDestination?,
    bottomBarOffsetHeightPx: MutableState<Float>,
    navItems: List<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>>
) {
    Box(modifier = Modifier.fillMaxSize().then(if (!isPlayerScreen) Modifier.nestedScroll(nestedScrollConnection) else Modifier)) {
        Box(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            NavHost(navController = navController, startDestination = if (loginViewModel.isLogged) "main" else "login", modifier = Modifier.fillMaxSize()) {
                composable("login") { LoginScreen(loginViewModel, onLoginSuccess = { userViewModel.fetchUserData(); navController.navigate("main") { popUpTo("login") { inclusive = true } } }) }
                composable("main") {
                    LaunchedEffect(Unit) { if (userViewModel.recommendedSongs.isEmpty()) userViewModel.fetchUserData() else { socialViewModel.fetchUnreadCount(); socialViewModel.fetchContacts() } }
                    val tasks by downloadViewModel.tasks.collectAsState()
                    val completedSongs by downloadViewModel.completedSongs.collectAsState()
                    MainScreen(recommendedSongs = userViewModel.recommendedSongs, userPlaylists = userViewModel.userPlaylists, userProfile = userViewModel.userProfile, versionName = "1.0.0", onSongClick = { s -> playbackViewModel.playSong(s, userViewModel.recommendedSongs); navController.navigate("player") { launchSingleTop = true } }, onPlaylistClick = { p -> userViewModel.fetchPlaylistSongs(p.id); navController.navigate("playlist/${p.id}") }, onPersonalFmClick = { playbackViewModel.playPersonalFm(); navController.navigate("player") { launchSingleTop = true } }, onHeartbeatClick = { if (userViewModel.favoriteSongs.isNotEmpty()) { playbackViewModel.playHeartbeat(userViewModel.favoriteSongs[0], userViewModel.likedSongsPlaylistId); navController.navigate("player") { launchSingleTop = true } } }, onLiveSortClick = { navController.navigate("livesort") }, onLikeClick = { s -> userViewModel.toggleLike(s.id, !userViewModel.favoriteSongs.contains(s.id)) }, favoriteSongs = userViewModel.favoriteSongs, completedSongs = completedSongs, unreadMessagesCount = socialViewModel.unreadCount, onNavigateToMessages = { navController.navigate("messages") }, onNavigateToSettings = { navController.navigate("settings") }, bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp), actions = { DownloadIndicator(tasks = tasks) { navController.navigate("downloads") } })
                }
                composable("search") { SearchScreen(searchResults = searchViewModel.searchResults, searchPlaylists = searchViewModel.searchPlaylists, favoriteSongs = userViewModel.favoriteSongs, hotSearches = searchViewModel.hotSearches, searchHistory = searchViewModel.searchHistory, suggestions = searchViewModel.searchSuggestions, searchType = searchViewModel.searchType, isLoading = searchViewModel.isLoading, onSearch = { kw, t -> searchViewModel.search(kw, t) }, onSuggestionFetch = { searchViewModel.fetchSuggestions(it) }, onClearHistory = { searchViewModel.clearHistory() }, onSongClick = { s -> playbackViewModel.playSong(s, searchViewModel.searchResults); navController.navigate("player") { launchSingleTop = true } }, onPlaylistClick = { p -> userViewModel.fetchPlaylistSongs(p.id); navController.navigate("playlist/${p.id}") }, onLikeClick = { s -> userViewModel.toggleLike(s.id, !userViewModel.favoriteSongs.contains(s.id)) }, bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp)) }
                composable("library") { LibraryScreen(userPlaylists = userViewModel.userPlaylists, onPlaylistClick = { p -> userViewModel.fetchPlaylistSongs(p.id); navController.navigate("playlist/${p.id}") }, onNavigateToLiveSort = { navController.navigate("livesort") }, onNavigateToDownloads = { navController.navigate("downloads") }, onNavigateToCloud = { navController.navigate("cloud") }, onNavigateToSettings = { navController.navigate("settings") }, bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp)) }
                composable("cloud") {
                    LaunchedEffect(Unit) { userViewModel.fetchCloudSongs() }
                    CloudMusicScreen(
                        songs = userViewModel.cloudSongs,
                        favoriteSongs = userViewModel.favoriteSongs,
                        isLoading = userViewModel.isLoading,
                        onSongClick = { s -> playbackViewModel.playSong(s, userViewModel.cloudSongs); navController.navigate("player") { launchSingleTop = true } },
                        onLikeClick = { s -> userViewModel.toggleLike(s.id, !userViewModel.favoriteSongs.contains(s.id)) },
                        onBackPressed = { navController.popBackStack() },
                        bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp)
                    )
                }
                composable("livesort") { LiveSortScreen(liveSortViewModel = liveSortViewModel, playbackViewModel = playbackViewModel, onBackPressed = { navController.popBackStack() }) }
                composable("playlist/{playlistId}") { backStackEntry ->
                    val pid = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull() ?: 0L
                    LaunchedEffect(pid) { if (userViewModel.currentPlaylistMetadata?.id != pid) userViewModel.fetchPlaylistSongs(pid) }
                    val completedSongs by downloadViewModel.completedSongs.collectAsState()
                    PlaylistDetailScreen(playlist = userViewModel.currentPlaylistMetadata ?: com.ncm.player.model.Playlist(pid, "Loading...", null, 0), songs = userViewModel.playlistSongs, favoriteSongs = userViewModel.favoriteSongs, allPlaylists = userViewModel.userPlaylists, isLoading = userViewModel.isLoading, onSongClick = { s -> playbackViewModel.playSong(s, userViewModel.playlistSongs); navController.navigate("player") { launchSingleTop = true } }, onPlayAllClick = { l -> if (l.isNotEmpty()) { playbackViewModel.playSong(l[0], l); navController.navigate("player") { launchSingleTop = true } } }, onQueueAllClick = { l -> l.forEach { playbackViewModel.addToQueue(it) } }, onLikeClick = { s -> userViewModel.toggleLike(s.id, !userViewModel.favoriteSongs.contains(s.id)) }, onAddToPlaylist = { ids, targetPid -> userViewModel.addSongsToPlaylist(targetPid, ids, null) }, onRemoveFromPlaylist = { ids -> userViewModel.removeSongsFromPlaylist(pid, ids, null) }, onBatchDownload = { l -> downloadViewModel.batchDownload(l, null) }, completedSongs = completedSongs, onBackPressed = { navController.popBackStack() }, bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp))
                }
                composable("downloads") {
                    val tasks by downloadViewModel.tasks.collectAsState()
                    val downloadedSongs by downloadViewModel.downloadedSongs.collectAsState()
                    DownloadsScreen(
                        onBackPressed = { navController.popBackStack() },
                        onPlayLocalSong = { s, u ->
                            playbackViewModel.playSong(s, downloadedSongs.map { it.song })
                            navController.navigate("player") { launchSingleTop = true }
                        },
                        downloadedSongs = downloadedSongs,
                        tasks = tasks,
                        onCancelDownload = { downloadViewModel.cancelDownload(it) },
                        onDeleteLocalSong = { playbackViewModel.deleteLocalSong(it) },
                        bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp)
                    )
                }
                composable("messages") { LaunchedEffect(Unit) { socialViewModel.fetchContacts() }; ContactListScreen(contacts = socialViewModel.contacts, onContactClick = { c -> navController.navigate("chat/${c.userId}/${c.nickname}") }, onAvatarClick = { uid -> userViewModel.fetchOtherUserProfile(uid); navController.navigate("user/$uid") }, onBackPressed = { navController.popBackStack() }) }
                composable("chat/{userId}/{nickname}") { backStackEntry ->
                    val uid = backStackEntry.arguments?.getString("userId")?.toLongOrNull() ?: 0L
                    val name = backStackEntry.arguments?.getString("nickname") ?: ""
                    LaunchedEffect(uid) { socialViewModel.fetchMessages(uid); socialViewModel.markMessageAsRead(uid) }
                    ChatScreen(recipientUid = uid, recipientName = name, messages = socialViewModel.chatMessages, onSendMessage = { t -> socialViewModel.sendMessage(uid, t) }, onAvatarClick = { u -> userViewModel.fetchOtherUserProfile(u); navController.navigate("user/$u") }, onBackPressed = { navController.popBackStack() })
                }
                composable("user/{userId}") { backStackEntry ->
                    val uid = backStackEntry.arguments?.getString("userId")?.toLongOrNull() ?: 0L
                    LaunchedEffect(uid) { userViewModel.fetchOtherUserProfile(uid) }
                    val viewState = userViewModel.otherUserViewState
                    UserProfileScreen(userProfile = viewState.profile, playlists = viewState.playlists, albums = viewState.albums, songs = viewState.songs, isArtist = viewState.isArtist, isLoading = viewState.isLoading, onPlaylistClick = { p -> userViewModel.fetchPlaylistSongs(p.id); navController.navigate("playlist/${p.id}") }, onSongClick = { s -> playbackViewModel.playSong(s, viewState.songs); navController.navigate("player") { launchSingleTop = true } }, onMessageClick = { u, n -> navController.navigate("chat/$u/$n") }, onBackPressed = { navController.popBackStack() })
                }
                composable("settings") { SettingsScreen(currentQualityWifi = settingsViewModel.qualityWifi, onQualityWifiChange = { settingsViewModel.updateQualityWifi(it) }, currentQualityCellular = settingsViewModel.qualityCellular, onQualityCellularChange = { settingsViewModel.updateQualityCellular(it) }, downloadQuality = downloadViewModel.downloadQuality, onDownloadQualityChange = { downloadViewModel.updateDownloadQuality(it) }, fadeDuration = settingsViewModel.fadeDuration, onFadeChange = { settingsViewModel.updateFade(it) }, cacheSize = settingsViewModel.cacheSize, onCacheSizeChange = { settingsViewModel.updateCache(it) }, useCellularCache = settingsViewModel.useCellularCache, onUseCellularCacheChange = { settingsViewModel.updateUseCellular(it) }, allowCellularDownload = downloadViewModel.allowCellularDownload, onAllowCellularDownloadChange = { downloadViewModel.updateAllowCellularDownload(it) }, pureBlackMode = settingsViewModel.pureBlackMode, onPureBlackModeChange = { settingsViewModel.updatePureBlackMode(it) }, downloadDir = settingsViewModel.downloadDir, onDownloadDirChange = { settingsViewModel.updateDownloadPath(it) }, onClearCache = { settingsViewModel.clearCache() }, onBackPressed = { navController.popBackStack() }, bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp)) }
                composable("player", enterTransition = { slideInVertically(initialOffsetY = { it }, animationSpec = tween(500, easing = EaseOutQuart)) + fadeIn(animationSpec = tween(400)) }, exitTransition = { slideOutVertically(targetOffsetY = { it }, animationSpec = tween(500, easing = EaseInQuart)) + fadeOut(animationSpec = tween(400)) }) {
                    val s = playbackViewModel.currentSong
                    val completedSongs by downloadViewModel.completedSongs.collectAsState()
                    val isFav = s?.let { userViewModel.favoriteSongs.contains(it.id) } ?: false
                    PlayerScreen(
                        song = s,
                        lyrics = playbackViewModel.currentLyrics,
                        isPlaying = playbackViewModel.isPlaying,
                        currentPosition = playbackViewModel.currentPosition,
                        duration = playbackViewModel.duration,
                        onPlayPause = { playbackViewModel.togglePlayPause() },
                        onSkipNext = { playbackViewModel.skipNext() },
                        onSkipPrevious = { playbackViewModel.skipPrevious() },
                        onSeek = { playbackViewModel.seekTo(it) },
                        onRepeatClick = { playbackViewModel.toggleRepeatMode() },
                        onShuffleClick = { playbackViewModel.toggleShuffleMode() },
                        repeatMode = playbackViewModel.repeatMode,
                        shuffleMode = playbackViewModel.shuffleMode,
                        isFavorite = isFav,
                        onLikeClick = { s?.let { userViewModel.toggleLike(it.id, !isFav) } },
                        onArtistClick = { id -> userViewModel.fetchOtherUserProfile(id.toLong()); navController.navigate("user/$id") },
                        onDownloadClick = { s?.let { downloadViewModel.downloadSong(it) } },
                        isDownloaded = s?.let { completedSongs.contains(it.id) } ?: false,
                        hotComments = socialViewModel.hotComments,
                        newestComments = socialViewModel.newestComments,
                        commentTotal = socialViewModel.commentTotal,
                        isCommentsLoading = socialViewModel.isLoading,
                        hasMoreComments = socialViewModel.hasMoreComments,
                        commentSortType = socialViewModel.commentSortType,
                        onLoadMoreComments = { s?.let { socialViewModel.fetchComments(it.id, "music", socialViewModel.commentSortType, socialViewModel.currentCommentPage + 1) } },
                        onLikeComment = { c -> s?.let { socialViewModel.toggleCommentLike(it.id, c.id, "music", !c.liked) } },
                        onPostComment = { t -> s?.let { socialViewModel.postComment(it.id, "music", t) } },
                        onCommentClick = { s?.let { socialViewModel.fetchComments(it.id) } },
                        onCommentSortChange = { sort -> s?.let { socialViewModel.fetchComments(it.id, "music", sort, 1) } },
                        onAvatarClick = { u -> userViewModel.fetchOtherUserProfile(u); navController.navigate("user/$u") },
                        onDislikeClick = { s?.let { userViewModel.dislikeSong(it.id); playbackViewModel.skipNext() } },
                        sleepTimerRemaining = playbackViewModel.sleepTimerRemaining,
                        onSetSleepTimer = { playbackViewModel.startSleepTimer(it) },
                        onLyricClick = { s?.let { playbackViewModel.fetchLyrics(it.id); navController.navigate("lyrics") } },
                        allPlaylists = userViewModel.userPlaylists,
                        onAddToPlaylist = { id, pid -> userViewModel.addSongsToPlaylist(pid, listOf(id), null) },
                        queue = playbackViewModel.currentQueue,
                        onMoveQueueItem = { f, t -> playbackViewModel.moveQueueItem(f, t) },
                        onRemoveQueueItem = { i -> playbackViewModel.removeQueueItem(i) },
                        onClearQueue = { playbackViewModel.clearQueue() },
                        qualityWifi = settingsViewModel.qualityWifi,
                        qualityCellular = settingsViewModel.qualityCellular,
                        sampleRate = playbackViewModel.currentSampleRate,
                        bitrate = playbackViewModel.currentBitrate,
                        onViewFloorClick = { c -> s?.let { socialViewModel.fetchFloorComments(it.id, c.id) }; socialViewModel.activeParentComment = c },
                        floorComments = socialViewModel.floorComments,
                        floorCommentTotal = socialViewModel.floorCommentTotal,
                        floorHasMore = socialViewModel.floorHasMore,
                        onLoadMoreFloor = { c -> s?.let { socialViewModel.fetchFloorComments(it.id, c.id, time = socialViewModel.floorCursor) } },
                        activeParentComment = socialViewModel.activeParentComment,
                        onDismissFloor = { socialViewModel.activeParentComment = null },
                        onBackPressed = { navController.popBackStack() }
                    )
                }
                composable("lyrics") { LyricsScreen(lyrics = playbackViewModel.currentLyrics, songName = playbackViewModel.currentSong?.name ?: "Lyrics", currentPosition = playbackViewModel.currentPosition, onBackPressed = { navController.popBackStack() }) }
                composable("logs") { LogViewerScreen(onBackPressed = { navController.popBackStack() }) }
            }
        }
        if (loginViewModel.isLogged && !isPlayerScreen && !useSideNav) {
            Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).offset { IntOffset(0, -bottomBarOffsetHeightPx.value.toInt()) }.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))) {
                if (playbackViewModel.currentSong != null) { BottomPlaybackBar(song = playbackViewModel.currentSong, isPlaying = playbackViewModel.isPlaying, onPlayPause = { playbackViewModel.togglePlayPause() }, onSkipNext = { playbackViewModel.skipNext() }, onSkipPrevious = { playbackViewModel.skipPrevious() }, onClick = { navController.navigate("player") { launchSingleTop = true } }) }
                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) { navItems.forEach { (route, label, icon) -> NavigationBarItem(icon = { Icon(icon, null) }, label = { Text(label) }, selected = currentDestination?.hierarchy?.any { it.route == route } == true, onClick = { navController.navigate(route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }) } }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        downloadViewModel.showCellularDownloadDialog?.let { song ->
            AlertDialog(onDismissRequest = { downloadViewModel.showCellularDownloadDialog = null }, title = { Text("Cellular Data Warning") }, text = { Text("You are currently on a mobile network.") }, confirmButton = { Column { TextButton(onClick = { downloadViewModel.downloadSong(song); downloadViewModel.showCellularDownloadDialog = null }) { Text("Continue") } } }, dismissButton = { TextButton(onClick = { downloadViewModel.showCellularDownloadDialog = null }) { Text("Cancel") } })
        }
    }
}
