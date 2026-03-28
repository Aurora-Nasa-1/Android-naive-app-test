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
import androidx.media3.common.MediaMetadata
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
import java.net.URLEncoder
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
    var playlistSongs by mutableStateOf<List<Song>>(emptyList())
    var isLoading by mutableStateOf(false)

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
            mediaController?.let { controller ->
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        this@PlayerViewModel.isPlaying = isPlaying
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.mediaMetadata?.let { metadata ->
                            currentSong = Song(
                                id = mediaItem.mediaId,
                                name = metadata.title?.toString() ?: "Unknown",
                                artist = metadata.artist?.toString() ?: "Unknown",
                                album = metadata.albumTitle?.toString() ?: "Unknown",
                                albumArtUrl = metadata.artworkUri?.toString()
                            )
                        }
                    }
                })
            }
        }, MoreExecutors.directExecutor())
    }

    fun fetchUserData(cookie: String?) {
        if (cookie.isNullOrEmpty()) return

        viewModelScope.launch {
            isLoading = true
            try {
                // Fetch Recommendations
                val recResponse = apiService.getRecommendSongs(cookie)
                val recBody = recResponse.body()
                val songsJson = recBody?.get("data")?.asJsonObject?.get("dailySongs")?.asJsonArray
                    ?: recBody?.get("dailySongs")?.asJsonArray

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
                val statusResponse = apiService.loginStatus(cookie = cookie)
                val statusBody = statusResponse.body()
                val uid = statusBody?.get("data")?.asJsonObject?.get("account")?.asJsonObject?.get("id")?.asLong
                    ?: statusBody?.get("account")?.asJsonObject?.get("id")?.asLong
                    ?: statusBody?.get("profile")?.asJsonObject?.get("userId")?.asLong
                    ?: 0L

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
            } finally {
                isLoading = false
            }
        }
    }

    fun fetchPlaylistSongs(playlistId: Long, cookie: String?) {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = apiService.getPlaylistTracks(playlistId, cookie)
                val songsJson = response.body()?.get("songs")?.asJsonArray
                playlistSongs = songsJson?.map {
                    val obj = it.asJsonObject
                    Song(
                        id = obj.get("id").asString,
                        name = obj.get("name").asString,
                        artist = obj.get("ar").asJsonArray.get(0).asJsonObject.get("name").asString,
                        album = obj.get("al").asJsonObject.get("name").asString,
                        albumArtUrl = obj.get("al").asJsonObject.get("picUrl").asString
                    )
                } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleLike(songId: String, like: Boolean, cookie: String?) {
        viewModelScope.launch {
            try {
                val response = apiService.likeSong(songId, like, cookie)
                if (response.isSuccessful) {
                    favoriteSongs = if (like) {
                        favoriteSongs + songId
                    } else {
                        favoriteSongs - songId
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun downloadSong(song: Song, cookie: String?) {
        viewModelScope.launch {
            try {
                val response = apiService.getDownloadUrl(song.id, cookie = cookie)
                val url = response.body()?.get("data")?.asJsonArray?.get(0)?.asJsonObject?.get("url")?.asString

                url?.let { downloadUrl ->
                    val request = android.app.DownloadManager.Request(android.net.Uri.parse(downloadUrl))
                        .setTitle("Downloading ${song.name}")
                        .setDescription("${song.artist} - ${song.album}")
                        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, "${song.name}.mp3")
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)

                    val downloadManager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                    downloadManager.enqueue(request)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playSong(song: Song, playlist: List<Song> = emptyList(), cookie: String? = null) {
        viewModelScope.launch {
            try {
                mediaController?.let { controller ->
                    controller.stop()
                    controller.clearMediaItems()

                    val targetPlaylist = if (playlist.isNotEmpty()) playlist else listOf(song)
                    val startIndex = targetPlaylist.indexOf(song).coerceAtLeast(0)

                    val mediaItems = targetPlaylist.map { s ->
                        val metadata = MediaMetadata.Builder()
                            .setTitle(s.name)
                            .setArtist(s.artist)
                            .setAlbumTitle(s.album)
                            .setArtworkUri(s.albumArtUrl?.let { android.net.Uri.parse(it) })
                            .build()

                        val uri = if (!cookie.isNullOrEmpty()) {
                            "http://127.0.0.1:3000/song/url/v1?id=${s.id}&level=$currentQuality&cookie=${URLEncoder.encode(cookie, "UTF-8")}"
                        } else {
                            "http://127.0.0.1:3000/song/url/v1?id=${s.id}&level=$currentQuality"
                        }

                        MediaItem.Builder()
                            .setMediaId(s.id)
                            .setUri(uri)
                            .setMediaMetadata(metadata)
                            .build()
                    }

                    controller.setMediaItems(mediaItems, startIndex, 0L)
                    controller.prepare()
                    controller.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun skipNext() {
        mediaController?.seekToNext()
    }

    fun skipPrevious() {
        mediaController?.seekToPrevious()
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
