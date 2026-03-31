package com.ncm.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NCMPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    var serverReady by remember { mutableStateOf(false) }

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

                    AppNavigation(loginViewModel, playerViewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(loginViewModel: LoginViewModel, playerViewModel: PlayerViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (loginViewModel.isLogged) {
                Column {
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
                    val items = listOf(
                        Triple("main", "Home", Icons.Filled.Home),
                        Triple("search", "Search", Icons.Filled.Search),
                        Triple("library", "Library", Icons.Filled.LibraryMusic)
                    )
                    NavigationBar {
                        items.forEach { (route, label, icon) ->
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
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (loginViewModel.isLogged) "main" else "login",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
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

                MainScreen(
                    recommendedSongs = playerViewModel.recommendedSongs,
                    userPlaylists = playerViewModel.userPlaylists,
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
                            // Use first favorite song to seed heartbeat
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
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    },
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
                    favoriteSongs = playerViewModel.favoriteSongs,
                    isLoading = playerViewModel.isLoading,
                    onSearch = { playerViewModel.searchSongs(it) },
                    onSongClick = { song ->
                        playerViewModel.playSong(song, playerViewModel.searchResults, loginViewModel.cookie)
                        navController.navigate("player") {
                            launchSingleTop = true
                        }
                    },
                    onLikeClick = { song ->
                        val isFavorite = playerViewModel.favoriteSongs.contains(song.id)
                        playerViewModel.toggleLike(song.id, !isFavorite, loginViewModel.cookie)
                    }
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
                    }
                )
            }
            composable("playlist/{playlistId}") { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull() ?: 0L
                val playlist = playerViewModel.userPlaylists.find { it.id == playlistId }

                if (playlist != null) {
                    val completedSongs by playerViewModel.ncmDownloadManager.completedSongs.collectAsState()
                    PlaylistDetailScreen(
                        playlist = playlist,
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
                        onBackPressed = { navController.popBackStack() }
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
                    onDeleteLocalSong = { playerViewModel.deleteLocalSong(it) }
                )
            }
            composable("settings") {
                SettingsScreen(
                    currentQuality = playerViewModel.currentQuality,
                    onQualityChange = { playerViewModel.setQuality(it) },
                    downloadQuality = playerViewModel.downloadQuality,
                    onDownloadQualityChange = { playerViewModel.updateDownloadQuality(it) },
                    fadeDuration = playerViewModel.fadeDuration,
                    onFadeChange = { playerViewModel.setFade(it) },
                    cacheSize = playerViewModel.cacheSize,
                    onCacheSizeChange = { playerViewModel.setCache(it) },
                    useCellularCache = playerViewModel.useCellularCache,
                    onUseCellularCacheChange = { playerViewModel.setUseCellular(it) },
                    downloadDir = playerViewModel.downloadDir,
                    onDownloadDirChange = { playerViewModel.setDownloadPath(it) },
                    onClearCache = { playerViewModel.clearCache() },
                    onBackPressed = { navController.popBackStack() }
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
                    onBackPressed = { navController.popBackStack() }
                )
            }
        }
    }
}
