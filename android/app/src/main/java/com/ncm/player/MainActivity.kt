package com.ncm.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ncm.player.service.RustServerService
import com.ncm.player.ui.screen.*
import com.ncm.player.ui.theme.NCMPlayerTheme
import com.ncm.player.viewmodel.LoginViewModel
import com.ncm.player.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start Rust Backend Service
        val serviceIntent = Intent(this, RustServerService::class.java)
        startService(serviceIntent)

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
                    AppNavigation(loginViewModel, playerViewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(loginViewModel: LoginViewModel, playerViewModel: PlayerViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = if (loginViewModel.isLogged) "main" else "login") {
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
            MainScreen(
                recommendedSongs = playerViewModel.recommendedSongs,
                userPlaylists = playerViewModel.userPlaylists,
                onSongClick = { song ->
                    playerViewModel.playSong(song)
                    navController.navigate("player")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }
        composable("player") {
            PlayerScreen(
                song = playerViewModel.currentSong,
                isPlaying = playerViewModel.isPlaying,
                onPlayPause = { playerViewModel.togglePlayPause() },
                onSkipNext = { /* Next song logic */ },
                onSkipPrevious = { /* Previous song logic */ },
                onBackPressed = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                currentQuality = playerViewModel.currentQuality,
                onQualityChange = { playerViewModel.setQuality(it) },
                fadeDuration = playerViewModel.fadeDuration,
                onFadeChange = { playerViewModel.setFade(it) },
                onBackPressed = { navController.popBackStack() }
            )
        }
    }
}
