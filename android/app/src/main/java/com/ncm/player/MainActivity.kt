package com.ncm.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.withContext
import com.ncm.player.service.RustServerService
import com.ncm.player.ui.component.BottomPlaybackBar
import com.ncm.player.ui.component.DownloadIndicator
import com.ncm.player.ui.screen.*
import com.ncm.player.ui.theme.NCMPlayerTheme
import com.ncm.player.viewmodel.LoginViewModel
import com.ncm.player.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val useSideNav = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

            NCMPlayerTheme(
                pureBlack = playerViewModel.pureBlackMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        playerViewModel.initController(context)
                        // Ensure service is running
                        try {
                            val serviceIntent = Intent(context, RustServerService::class.java)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to start RustServerService", e)
                        }

                    }

                    AppNavigation(loginViewModel, playerViewModel, useSideNav, intent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}

@Composable
fun AppNavigation(
    loginViewModel: LoginViewModel,
    playerViewModel: PlayerViewModel,
    useSideNav: Boolean,
    intent: Intent? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    LaunchedEffect(intent) {
        if (intent?.action == "ACTION_SHOW_PLAYER") {
            navController.navigate("player") {
                launchSingleTop = true
            }
        }
    }

    val isPlayerScreen = currentDestination?.route == "player" || currentDestination?.route == "lyrics"
    val showNav = loginViewModel.isLogged && !isPlayerScreen
    val hasBottomBar = showNav && !useSideNav
    val bottomBarHeight = 144.dp // 64 (playback) + 80 (nav)
    val density = LocalDensity.current
    val bottomBarHeightPx = with(density) { bottomBarHeight.toPx() }
    val bottomBarOffsetHeightPx = remember { mutableStateOf(0f) }

    val navBarPadding = WindowInsets.navigationBars.getBottom(density)
    val maxOffset = bottomBarHeightPx + navBarPadding

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = bottomBarOffsetHeightPx.value + delta
                bottomBarOffsetHeightPx.value = newOffset.coerceIn(-maxOffset, 0f)
                return Offset.Zero
            }
        }
    }

    val navItems = listOf(
        Triple("main", "Home", Icons.Filled.Home),
        Triple("search", "Search", Icons.Filled.Search),
        Triple("library", "Library", Icons.Filled.LibraryMusic)
    )

    if (useSideNav && showNav) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(
                    modifier = Modifier.width(240.dp)
                ) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "CNMDPlayer",
                        modifier = Modifier.padding(horizontal = 28.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(24.dp))
                    navItems.forEach { (route, label, icon) ->
                        NavigationDrawerItem(
                            label = { Text(label) },
                            icon = { Icon(icon, contentDescription = label) },
                            selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    if (playerViewModel.currentSong != null) {
                        Box(modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) {
                            BottomPlaybackBar(
                                song = playerViewModel.currentSong,
                                isPlaying = playerViewModel.isPlaying,
                                onPlayPause = { playerViewModel.togglePlayPause() },
                                onSkipNext = { playerViewModel.skipNext() },
                                onSkipPrevious = { playerViewModel.skipPrevious() },
                                onClick = {
                                    navController.navigate("player") {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    Spacer(Modifier.height(16.dp))
                }
            }
        ) {
            AppMainContent(
                innerPadding = PaddingValues(0.dp),
                isPlayerScreen = isPlayerScreen,
                nestedScrollConnection = nestedScrollConnection,
                navController = navController,
                loginViewModel = loginViewModel,
                playerViewModel = playerViewModel,
                useSideNav = useSideNav,
                hasBottomBar = hasBottomBar,
                bottomBarHeight = bottomBarHeight,
                context = context,
                currentDestination = currentDestination,
                bottomBarOffsetHeightPx = bottomBarOffsetHeightPx,
                navItems = navItems
            )
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            AppMainContent(
                innerPadding = innerPadding,
                isPlayerScreen = isPlayerScreen,
                nestedScrollConnection = nestedScrollConnection,
                navController = navController,
                loginViewModel = loginViewModel,
                playerViewModel = playerViewModel,
                useSideNav = useSideNav,
                hasBottomBar = hasBottomBar,
                bottomBarHeight = bottomBarHeight,
                context = context,
                currentDestination = currentDestination,
                bottomBarOffsetHeightPx = bottomBarOffsetHeightPx,
                navItems = navItems
            )
        }
    }
}

@Composable
fun AppMainContent(
    innerPadding: PaddingValues,
    isPlayerScreen: Boolean,
    nestedScrollConnection: NestedScrollConnection,
    navController: androidx.navigation.NavHostController,
    loginViewModel: LoginViewModel,
    playerViewModel: PlayerViewModel,
    useSideNav: Boolean,
    hasBottomBar: Boolean,
    bottomBarHeight: androidx.compose.ui.unit.Dp,
    context: android.content.Context,
    currentDestination: androidx.navigation.NavDestination?,
    bottomBarOffsetHeightPx: MutableState<Float>,
    navItems: List<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>>
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!isPlayerScreen) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                NavHost(
                    navController = navController,
                    startDestination = if (loginViewModel.isLogged) "main" else "login",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(400, easing = EaseOutQuart)
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { -it / 3 },
                            animationSpec = tween(400, easing = EaseOutQuart)
                        ) + fadeOut(animationSpec = tween(300))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = tween(400, easing = EaseOutQuart)
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(400, easing = EaseOutQuart)
                        ) + fadeOut(animationSpec = tween(300))
                    }
                ) {
                    composable("login") {
                        LoginScreen(loginViewModel, onLoginSuccess = {
                            playerViewModel.fetchUserData(loginViewModel.cookie)
                            navController.navigate("main") {
                                popUpTo("login") { inclusive = true }
                            }
                        })
                    }
                    composable("main") {
                        LaunchedEffect(Unit) {
                            if (playerViewModel.recommendedSongs.isEmpty()) {
                                playerViewModel.fetchUserData(loginViewModel.cookie)
                            }
                        }
                        val tasks by playerViewModel.ncmDownloadManager.tasks.collectAsState()
                        val completedSongs by playerViewModel.ncmDownloadManager.completedSongs.collectAsState()

                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        val versionName = packageInfo.versionName ?: "1.0.0"

                        MainScreen(
                            recommendedSongs = playerViewModel.recommendedSongs,
                            userPlaylists = playerViewModel.userPlaylists,
                            userProfile = playerViewModel.userProfile,
                            versionName = versionName,
                            onSongClick = { song ->
                                playerViewModel.playSong(song, playerViewModel.recommendedSongs, loginViewModel.cookie)
                                navController.navigate("player") {
                                    launchSingleTop = true
                                }
                            },
                            onPlaylistClick = { playlist ->
                                playerViewModel.fetchPlaylistSongs(playlist.id, loginViewModel.cookie)
                                navController.navigate("playlist/${playlist.id}")
                            },
                            onPersonalFmClick = {
                                playerViewModel.playPersonalFm(loginViewModel.cookie)
                                navController.navigate("player") {
                                    launchSingleTop = true
                                }
                            },
                            onHeartbeatClick = {
                                if (playerViewModel.favoriteSongs.isNotEmpty()) {
                                    playerViewModel.playHeartbeat(
                                        songId = playerViewModel.favoriteSongs[0],
                                        playlistId = playerViewModel.likedSongsPlaylistId,
                                        cookie = loginViewModel.cookie
                                    )
                                    navController.navigate("player") {
                                        launchSingleTop = true
                                    }
                                } else {
                                    com.ncm.player.util.DebugLog.toast(context, "No liked songs to start Heartbeat")
                                }
                            },
                            onLikeClick = { song ->
                                val isFavorite = playerViewModel.favoriteSongs.contains(song.id)
                                playerViewModel.toggleLike(song.id, !isFavorite, loginViewModel.cookie)
                            },
                            favoriteSongs = playerViewModel.favoriteSongs,
                            completedSongs = completedSongs,
                            unreadMessagesCount = playerViewModel.unreadMessagesCount,
                            onNavigateToMessages = {
                                navController.navigate("messages")
                            },
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            },
                            bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp),
                            actions = {
                                DownloadIndicator(tasks = tasks) {
                                    navController.navigate("downloads")
                                }
                            }
                        )
                    }
                    composable("search") {
                        SearchScreen(
                            searchResults = playerViewModel.searchResults,
                            searchPlaylists = playerViewModel.searchPlaylists,
                            favoriteSongs = playerViewModel.favoriteSongs,
                            hotSearches = playerViewModel.hotSearches,
                            searchHistory = playerViewModel.searchHistory,
                            suggestions = playerViewModel.searchSuggestions,
                            searchType = playerViewModel.searchType,
                            isLoading = playerViewModel.isLoading,
                            onSearch = { kw, type -> playerViewModel.search(kw, type) },
                            onSuggestionFetch = { playerViewModel.fetchSearchSuggestions(it) },
                            onClearHistory = { playerViewModel.clearSearchHistory() },
                            onSongClick = { song ->
                                playerViewModel.playSong(song, playerViewModel.searchResults, loginViewModel.cookie)
                                navController.navigate("player") {
                                    launchSingleTop = true
                                }
                            },
                            onPlaylistClick = { playlist ->
                                playerViewModel.fetchPlaylistSongs(playlist.id, loginViewModel.cookie)
                                navController.navigate("playlist/${playlist.id}")
                            },
                            onLikeClick = { song ->
                                val isFavorite = playerViewModel.favoriteSongs.contains(song.id)
                                playerViewModel.toggleLike(song.id, !isFavorite, loginViewModel.cookie)
                            },
                            bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp)
                        )
                    }
                    composable("library") {
                        LibraryScreen(
                            userPlaylists = playerViewModel.userPlaylists,
                            onPlaylistClick = { playlist ->
                                playerViewModel.fetchPlaylistSongs(playlist.id, loginViewModel.cookie)
                                navController.navigate("playlist/${playlist.id}")
                            },
                            onNavigateToDownloads = {
                                navController.navigate("downloads")
                            },
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            },
                            bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp)
                        )
                    }
                    composable("playlist/{playlistId}") { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull() ?: 0L

                        LaunchedEffect(playlistId) {
                            if (playerViewModel.currentPlaylistMetadata?.id != playlistId) {
                                playerViewModel.fetchPlaylistSongs(playlistId, loginViewModel.cookie)
                            }
                        }

                        val playlist = playerViewModel.currentPlaylistMetadata

                        if (playlist != null || playerViewModel.isLoading) {
                            val completedSongs by playerViewModel.ncmDownloadManager.completedSongs.collectAsState()
                            PlaylistDetailScreen(
                                playlist = playlist ?: com.ncm.player.model.Playlist(playlistId, "Loading...", null, 0),
                                songs = playerViewModel.playlistSongs,
                                favoriteSongs = playerViewModel.favoriteSongs,
                                completedSongs = completedSongs,
                                isFirstDownload = playerViewModel.isFirstDownload,
                                onDownloadQualityChange = { playerViewModel.updateDownloadQuality(it) },
                                allPlaylists = playerViewModel.userPlaylists,
                                isLoading = playerViewModel.isLoading,
                                onSongClick = { song ->
                                    playerViewModel.playSong(song, playerViewModel.playlistSongs, loginViewModel.cookie)
                                    navController.navigate("player") {
                                        launchSingleTop = true
                                    }
                                },
                                onPlayAllClick = { songs ->
                                    if (songs.isNotEmpty()) {
                                        playerViewModel.playSong(songs[0], songs, loginViewModel.cookie)
                                        navController.navigate("player") {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onQueueAllClick = { songs ->
                                    songs.forEach { playerViewModel.addToQueue(it, loginViewModel.cookie) }
                                },
                                onLikeClick = { song ->
                                    val isFavorite = playerViewModel.favoriteSongs.contains(song.id)
                                    playerViewModel.toggleLike(song.id, !isFavorite, loginViewModel.cookie)
                                },
                                onAddToPlaylist = { songIds, pid ->
                                    playerViewModel.addSongsToPlaylist(pid, songIds, loginViewModel.cookie)
                                },
                                onRemoveFromPlaylist = { songIds ->
                                    playerViewModel.removeSongsFromPlaylist(playlistId, songIds, loginViewModel.cookie)
                                },
                                onBatchDownload = { songs ->
                                    playerViewModel.batchDownload(songs, loginViewModel.cookie)
                                },
                                onSortChange = { sort ->
                                    playerViewModel.fetchPlaylistSongs(playlistId, loginViewModel.cookie, sort)
                                    com.ncm.player.util.UserPreferences.savePlaylistSort(context, playlistId, sort)
                                },
                                onBackPressed = { navController.popBackStack() },
                                bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp)
                            )
                        }
                    }
                    composable("downloads") {
                        val tasks by playerViewModel.ncmDownloadManager.tasks.collectAsState()
                        DownloadsScreen(
                            onBackPressed = { navController.popBackStack() },
                            onPlayLocalSong = { song, uri ->
                                playerViewModel.playSong(song, localUri = uri)
                                navController.navigate("player") {
                                    launchSingleTop = true
                                }
                            },
                            localSongs = playerViewModel.localSongs,
                            tasks = tasks,
                            onCancelDownload = { playerViewModel.ncmDownloadManager.cancelDownload(it) },
                            onDeleteLocalSong = { playerViewModel.deleteLocalSong(it) },
                            bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp)
                        )
                    }
                    composable("messages") {
                        LaunchedEffect(Unit) {
                            playerViewModel.fetchRecentContacts(loginViewModel.cookie)
                        }
                        ContactListScreen(
                            contacts = playerViewModel.contacts,
                            onContactClick = { contact ->
                                navController.navigate("chat/${contact.userId}/${contact.nickname}")
                            },
                            onAvatarClick = { uid ->
                                playerViewModel.fetchOtherUserProfile(uid, loginViewModel.cookie)
                                navController.navigate("user/$uid")
                            },
                            onBackPressed = { navController.popBackStack() }
                        )
                    }
                    composable("chat/{userId}/{nickname}") { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId")?.toLongOrNull() ?: 0L
                        val nickname = backStackEntry.arguments?.getString("nickname") ?: ""
                        LaunchedEffect(userId) {
                            playerViewModel.fetchMessageHistory(userId, loginViewModel.cookie)
                            playerViewModel.markMessageAsRead(userId, loginViewModel.cookie)
                        }
                        ChatScreen(
                            recipientUid = userId,
                            recipientName = nickname,
                            messages = playerViewModel.chatMessages,
                            onSendMessage = { text ->
                                playerViewModel.sendTextMessage(userId, text, loginViewModel.cookie)
                            },
                            onAvatarClick = { uid ->
                                playerViewModel.fetchOtherUserProfile(uid, loginViewModel.cookie)
                                navController.navigate("user/$uid")
                            },
                            onBackPressed = { navController.popBackStack() }
                        )
                    }
                    composable("user/{userId}") { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId")?.toLongOrNull() ?: 0L
                        LaunchedEffect(userId) {
                            playerViewModel.fetchOtherUserProfile(userId, loginViewModel.cookie)
                        }

                        val viewState = playerViewModel.otherUserViewState

                        UserProfileScreen(
                            userProfile = viewState.profile,
                            playlists = viewState.playlists,
                            albums = viewState.albums,
                            songs = viewState.songs,
                            isArtist = viewState.isArtist,
                            isLoading = viewState.isLoading,
                            onPlaylistClick = { playlist ->
                                playerViewModel.fetchPlaylistSongs(playlist.id, loginViewModel.cookie)
                                navController.navigate("playlist/${playlist.id}")
                            },
                            onSongClick = { song ->
                                playerViewModel.playSong(song, viewState.songs, loginViewModel.cookie)
                                navController.navigate("player") {
                                    launchSingleTop = true
                                }
                            },
                            onMessageClick = { uid, name ->
                                navController.navigate("chat/$uid/$name")
                            },
                            onBackPressed = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            currentQualityWifi = playerViewModel.currentQualityWifi,
                            onQualityWifiChange = { playerViewModel.setQualityWifi(it) },
                            currentQualityCellular = playerViewModel.currentQualityCellular,
                            onQualityCellularChange = { playerViewModel.setQualityCellular(it) },
                            downloadQuality = playerViewModel.downloadQuality,
                            onDownloadQualityChange = { playerViewModel.updateDownloadQuality(it) },
                            fadeDuration = playerViewModel.fadeDuration,
                            onFadeChange = { playerViewModel.setFade(it) },
                            cacheSize = playerViewModel.cacheSize,
                            onCacheSizeChange = { playerViewModel.setCache(it) },
                            useCellularCache = playerViewModel.useCellularCache,
                            onUseCellularCacheChange = { playerViewModel.setUseCellular(it) },
                            allowCellularDownload = playerViewModel.allowCellularDownload,
                            onAllowCellularDownloadChange = { playerViewModel.updateAllowCellularDownload(it) },
                            pureBlackMode = playerViewModel.pureBlackMode,
                            onPureBlackModeChange = { playerViewModel.updatePureBlackMode(it) },
                            downloadDir = playerViewModel.downloadDir,
                            onDownloadDirChange = { playerViewModel.setDownloadPath(it) },
                            onClearCache = { playerViewModel.clearCache() },
                            onBackPressed = { navController.popBackStack() },
                            bottomContentPadding = PaddingValues(bottom = if (hasBottomBar) bottomBarHeight else 0.dp)
                        )
                    }
                    composable(
                        "player",
                        enterTransition = {
                            slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(500, easing = EaseOutQuart)
                            ) + fadeIn(animationSpec = tween(400))
                        },
                        exitTransition = {
                            slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(500, easing = EaseInQuart)
                            ) + fadeOut(animationSpec = tween(400))
                        },
                        popEnterTransition = {
                            slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(500, easing = EaseOutQuart)
                            ) + fadeIn(animationSpec = tween(400))
                        },
                        popExitTransition = {
                            slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(500, easing = EaseInQuart)
                            ) + fadeOut(animationSpec = tween(400))
                        }
                    ) {
                        val currentSong = playerViewModel.currentSong
                        val completedSongs by playerViewModel.ncmDownloadManager.completedSongs.collectAsState()
                        val isFavorite = currentSong?.let { playerViewModel.favoriteSongs.contains(it.id) } ?: false
                        val isDownloaded = currentSong?.let { completedSongs.contains(it.id) } ?: false

                        PlayerScreen(
                            song = currentSong,
                            isPlaying = playerViewModel.isPlaying,
                            currentPosition = playerViewModel.currentPosition,
                            duration = playerViewModel.duration,
                            onPlayPause = { playerViewModel.togglePlayPause() },
                            onSkipNext = { playerViewModel.skipNext() },
                            onSkipPrevious = { playerViewModel.skipPrevious() },
                            onSeek = { playerViewModel.seekTo(it) },
                            onRepeatClick = { playerViewModel.toggleRepeatMode() },
                            onShuffleClick = { playerViewModel.toggleShuffleMode() },
                            repeatMode = playerViewModel.repeatMode,
                            shuffleMode = playerViewModel.shuffleMode,
                            isFavorite = isFavorite,
                            onLikeClick = {
                                currentSong?.let { song ->
                                    playerViewModel.toggleLike(song.id, !isFavorite, loginViewModel.cookie)
                                }
                            },
                            onArtistClick = { artistId ->
                                playerViewModel.fetchOtherUserProfile(artistId.toLong(), loginViewModel.cookie)
                                navController.navigate("user/$artistId")
                            },
                            onDownloadClick = {
                                currentSong?.let { song ->
                                    if (!isDownloaded) {
                                        if (playerViewModel.isFirstDownload) {
                                            navController.navigate("settings")
                                        } else {
                                            playerViewModel.downloadSong(song, loginViewModel.cookie)
                                        }
                                    }
                                }
                            },
                            isDownloaded = isDownloaded,
                            onLyricClick = {
                                playerViewModel.currentSong?.let { song ->
                                    playerViewModel.fetchLyrics(song.id)
                                    navController.navigate("lyrics")
                                }
                            },
                            allPlaylists = playerViewModel.userPlaylists,
                            onAddToPlaylist = { songId, pid ->
                                playerViewModel.addSongsToPlaylist(pid, listOf(songId), loginViewModel.cookie)
                            },
                            queue = playerViewModel.currentQueue,
                            onMoveQueueItem = { from, to -> playerViewModel.moveQueueItem(from, to) },
                            onRemoveQueueItem = { index -> playerViewModel.removeQueueItem(index) },
                            onClearQueue = { playerViewModel.clearQueue() },
                            onBackPressed = { navController.popBackStack() }
                        )
                    }
                    composable("lyrics") {
                        LyricsScreen(
                            lyrics = playerViewModel.currentLyrics,
                            songName = playerViewModel.currentSong?.name ?: "Lyrics",
                            currentPosition = playerViewModel.currentPosition,
                            onBackPressed = { navController.popBackStack() }
                        )
                    }
                }
            }

            if (loginViewModel.isLogged && !isPlayerScreen && !useSideNav) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .offset { IntOffset(x = 0, y = -bottomBarOffsetHeightPx.value.toInt()) }
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                ) {
                    if (playerViewModel.currentSong != null) {
                        BottomPlaybackBar(
                            song = playerViewModel.currentSong,
                            isPlaying = playerViewModel.isPlaying,
                            onPlayPause = { playerViewModel.togglePlayPause() },
                            onSkipNext = { playerViewModel.skipNext() },
                            onSkipPrevious = { playerViewModel.skipPrevious() },
                            onClick = {
                                navController.navigate("player") {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    NavigationBar(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        tonalElevation = 0.dp
                    ) {
                        navItems.forEach { (route, label, icon) ->
                            NavigationBarItem(
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }

            playerViewModel.showCellularDownloadDialog?.let { song ->
                AlertDialog(
                    onDismissRequest = { playerViewModel.showCellularDownloadDialog = null },
                    title = { Text("Cellular Data Warning") },
                    text = { Text("You are currently on a mobile network. Do you want to download \"${song.name}\"?") },
                    confirmButton = {
                        Column {
                            @Suppress("DEPRECATION")
                            TextButton(onClick = { playerViewModel.confirmCellularDownload(song, loginViewModel.cookie, false) }) {
                                Text("Continue")
                            }
                            @Suppress("DEPRECATION")
                            TextButton(onClick = { playerViewModel.confirmCellularDownload(song, loginViewModel.cookie, true) }) {
                                Text("Don't remind me this session")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { playerViewModel.showCellularDownloadDialog = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
    }
}
