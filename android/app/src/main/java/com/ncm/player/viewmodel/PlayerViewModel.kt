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
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val apiService = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:3000/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NcmApiService::class.java)

    var currentSong by mutableStateOf<Song?>(null)
    var isPlaying by mutableStateOf(false)
    var recommendedSongs by mutableStateOf<List<Song>>(emptyList())
    var userPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var favoriteSongs by mutableStateOf<List<String>>(emptyList())
    var playlistSongs by mutableStateOf<List<Song>>(emptyList())
    var searchResults by mutableStateOf<List<Song>>(emptyList())
    var currentLyrics by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)

    var currentQuality by mutableStateOf("standard")
    var fadeDuration by mutableStateOf(2f)
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
    var shuffleMode by mutableStateOf(false)
    var currentPosition by mutableStateOf(0L)
    var duration by mutableStateOf(0L)

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
                        duration = controller.duration.coerceAtLeast(0L)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            duration = controller.duration.coerceAtLeast(0L)
                        }
                    }
                })

                // Start a coroutine to update position
                viewModelScope.launch {
                    while (true) {
                        currentPosition = controller.currentPosition
                        kotlinx.coroutines.delay(1000L)
                    }
                }
            }
        }, MoreExecutors.directExecutor())
    }

    fun fetchUserData(cookie: String?) {
        if (cookie.isNullOrEmpty()) return

        viewModelScope.launch {
            isLoading = true
            var retryCount = 0
            val maxRetries = 5

            while (retryCount < maxRetries) {
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
                isLoading = false
                break // Success, exit retry loop
            } catch (e: Exception) {
                e.printStackTrace()
                retryCount++
                if (retryCount < maxRetries) {
                    kotlinx.coroutines.delay(1000L * retryCount)
                }
            } finally {
                if (retryCount == maxRetries) {
                    isLoading = false
                }
            }
            }
        }
    }

    fun searchSongs(keywords: String) {
        if (keywords.isBlank()) {
            searchResults = emptyList()
            return
        }
        viewModelScope.launch {
            isLoading = true
            try {
                val response = apiService.search(keywords)
                val songsJson = response.body()?.get("result")?.asJsonObject?.get("songs")?.asJsonArray
                searchResults = songsJson?.map {
                    val obj = it.asJsonObject
                    Song(
                        id = obj.get("id").asString,
                        name = obj.get("name").asString,
                        artist = obj.get("ar").asJsonArray.get(0).asJsonObject.get("name").asString,
                        album = obj.get("al").asJsonObject.get("name").asString,
                        albumArtUrl = obj.get("al").asJsonObject.get("picUrl")?.asString
                    )
                } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<String>, cookie: String?) {
        viewModelScope.launch {
            try {
                val tracks = songIds.joinToString(",")
                apiService.opPlaylistTracks("add", playlistId, tracks, cookie)
                // Optionally refresh playlist songs if we are viewing it
                if (playlistSongs.any { songIds.contains(it.id) }) {
                    fetchPlaylistSongs(playlistId, cookie)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeSongsFromPlaylist(playlistId: Long, songIds: List<String>, cookie: String?) {
        viewModelScope.launch {
            try {
                val tracks = songIds.joinToString(",")
                apiService.opPlaylistTracks("del", playlistId, tracks, cookie)
                fetchPlaylistSongs(playlistId, cookie)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun batchDownload(songs: List<Song>, cookie: String?) {
        songs.forEach { downloadSong(it, cookie) }
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

    fun fetchLyrics(songId: String) {
        viewModelScope.launch {
            try {
                val response = apiService.getLyric(songId)
                val lrc = response.body()?.get("lrc")?.asJsonObject?.get("lyric")?.asString
                currentLyrics = lrc
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
                        createMediaItem(s, cookie)
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

    fun toggleRepeatMode() {
        mediaController?.let { controller ->
            val nextMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            controller.repeatMode = nextMode
            repeatMode = nextMode
        }
    }

    fun toggleShuffleMode() {
        mediaController?.let { controller ->
            val nextMode = !controller.shuffleModeEnabled
            controller.shuffleModeEnabled = nextMode
            shuffleMode = nextMode
        }
    }

    fun addToQueue(song: Song, cookie: String? = null) {
        mediaController?.let { controller ->
            val mediaItem = createMediaItem(song, cookie)
            controller.addMediaItem(mediaItem)
        }
    }

    fun playNext(song: Song, cookie: String? = null) {
        mediaController?.let { controller ->
            val mediaItem = createMediaItem(song, cookie)
            val nextIndex = if (controller.mediaItemCount > 0) controller.currentMediaItemIndex + 1 else 0
            controller.addMediaItem(nextIndex, mediaItem)
        }
    }

    private fun createMediaItem(song: Song, cookie: String?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.name)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUrl?.let { android.net.Uri.parse(it) })
            .build()

        val uri = if (!cookie.isNullOrEmpty()) {
            "http://127.0.0.1:3000/song/url/v1/302?id=${song.id}&level=$currentQuality&cookie=${URLEncoder.encode(cookie, "UTF-8")}"
        } else {
            "http://127.0.0.1:3000/song/url/v1/302?id=${song.id}&level=$currentQuality"
        }

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun skipNext() {
        mediaController?.seekToNext()
    }

    fun skipPrevious() {
        mediaController?.seekToPrevious()
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        currentPosition = position
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
