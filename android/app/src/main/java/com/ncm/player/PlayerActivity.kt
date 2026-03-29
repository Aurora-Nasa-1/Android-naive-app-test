package com.ncm.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ncm.player.ui.screen.LyricsScreen
import com.ncm.player.ui.screen.PlayerScreen
import com.ncm.player.ui.theme.NCMPlayerTheme
import com.ncm.player.viewmodel.LoginViewModel
import com.ncm.player.viewmodel.PlayerViewModel

class PlayerActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NCMPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    LaunchedEffect(Unit) {
                        playerViewModel.initController(context)
                    }

                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "player") {
                        composable("player") {
                            PlayerScreen(
                                song = playerViewModel.currentSong,
                                isPlaying = playerViewModel.isPlaying,
                                onPlayPause = { playerViewModel.togglePlayPause() },
                                onSkipNext = { playerViewModel.skipNext() },
                                onSkipPrevious = { playerViewModel.skipPrevious() },
                                onRepeatClick = { playerViewModel.toggleRepeatMode() },
                                onShuffleClick = { playerViewModel.toggleShuffleMode() },
                                repeatMode = playerViewModel.repeatMode,
                                shuffleMode = playerViewModel.shuffleMode,
                                isFavorite = playerViewModel.currentSong?.let { playerViewModel.favoriteSongs.contains(it.id) } ?: false,
                                onLikeClick = {
                                    playerViewModel.currentSong?.let { song ->
                                        val isFavorite = playerViewModel.favoriteSongs.contains(song.id)
                                        playerViewModel.toggleLike(song.id, !isFavorite, loginViewModel.cookie)
                                    }
                                },
                                onDownloadClick = {
                                    playerViewModel.currentSong?.let { song ->
                                        playerViewModel.downloadSong(song, loginViewModel.cookie)
                                    }
                                },
                                onLyricClick = {
                                    playerViewModel.currentSong?.let { song ->
                                        playerViewModel.fetchLyrics(song.id)
                                        navController.navigate("lyrics")
                                    }
                                },
                                onBackPressed = { finish() }
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
        }
    }
}
