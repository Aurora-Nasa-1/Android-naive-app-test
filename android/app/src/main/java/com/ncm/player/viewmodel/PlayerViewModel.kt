package com.ncm.player.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ncm.player.api.NcmApiService
import com.ncm.player.model.Playlist
import com.ncm.player.model.Song
import com.ncm.player.service.MusicService
import com.ncm.player.util.UserPreferences
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:3000/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NcmApiService::class.java)

    var currentSong by mutableStateOf<Song?>(null)
    var isPlaying by mutableStateOf(false)
    var recommendedSongs by mutableStateOf<List<Song>>(emptyList())
    var userPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var favoriteSongs by mutableStateOf<List<String>>(emptyList())

    var currentQuality by mutableStateOf("standard")
    var fadeDuration by mutableStateOf(2f)

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private val mediaController: MediaController?
        get() = if (mediaControllerFuture?.isDone == true) mediaControllerFuture?.get() else null

    init {
        currentQuality = UserPreferences.getQuality(application)
        fadeDuration = UserPreferences.getFadeDuration(application)
    }

    fun setQuality(quality: String) {
        currentQuality = quality
        UserPreferences.saveQuality(getApplication(), quality)
    }

    fun setFade(duration: Float) {
        fadeDuration = duration
        UserPreferences.saveFadeDuration(getApplication(), duration)
    }

    fun initController(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    this@PlayerViewModel.isPlaying = isPlaying
                }
            })
        }, MoreExecutors.directExecutor())
    }

    fun fetchUserData(cookie: String?) {
        viewModelScope.launch {
            try {
                // Fetch Recommendations
                val recResponse = apiService.getRecommendSongs(cookie)
                val songsJson = recResponse.body()?.get("data")?.asJsonObject?.get("dailySongs")?.asJsonArray
                recommendedSongs = songsJson?.map {
                    val obj = it.asJsonObject
                    Song(
                        id = obj.get("id").asString,
                        name = obj.get("name").asString,
                        artist = obj.get("ar").asJsonArray.get(0).asJsonObject.get("name").asString,
                        album = obj.get("al").asJsonObject.get("name").asString,
                        albumArtUrl = obj.get("al").asJsonObject.get("picUrl").asString
                    )
                } ?: emptyList()

                // Fetch User Playlists
                val statusResponse = apiService.loginStatus()
                val uid = statusResponse.body()?.get("data")?.asJsonObject?.get("account")?.asJsonObject?.get("id")?.asLong ?: 0L

                if (uid != 0L) {
                    val plResponse = apiService.getUserPlaylist(uid, cookie)
                    val playlistJson = plResponse.body()?.get("playlist")?.asJsonArray
                    userPlaylists = playlistJson?.map {
                        val obj = it.asJsonObject
                        Playlist(
                            id = obj.get("id").asLong,
                            name = obj.get("name").asString,
                            coverImgUrl = obj.get("coverImgUrl").asString,
                            trackCount = obj.get("trackCount").asInt
                        )
                    } ?: emptyList()

                    val favResponse = apiService.getLikeList(uid, cookie)
                    val favJson = favResponse.body()?.get("ids")?.asJsonArray
                    favoriteSongs = favJson?.map { it.asString } ?: emptyList()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playSong(song: Song) {
        currentSong = song

        viewModelScope.launch {
            try {
                val response = apiService.getSongUrl(song.id, currentQuality)
                val url = response.body()?.get("data")?.asJsonArray?.get(0)?.asJsonObject?.get("url")?.asString

                url?.let {
                    val mediaItem = MediaItem.fromUri(it)
                    mediaController?.let { controller ->
                        controller.setMediaItem(mediaItem)
                        controller.prepare()
                        controller.play()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
