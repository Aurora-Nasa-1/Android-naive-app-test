package com.ncm.player.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.ncm.player.model.Playlist
import com.ncm.player.model.Song
import com.ncm.player.model.UserProfile
import com.ncm.player.model.Contact
import com.ncm.player.model.Message
import com.ncm.player.model.Comment
import com.ncm.player.model.Event
import com.ncm.player.service.MusicService
import com.ncm.player.util.UserPreferences
import com.ncm.player.util.DebugLog
import com.ncm.player.util.RustServerManager
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private fun callApi(method: String, params: Map<String, String> = emptyMap()): JsonObject {
        val result = RustServerManager.callApi(method, params)
        return try { JsonParser.parseString(result).asJsonObject } catch(e: Exception) { JsonObject() }
    }

    var currentSong by mutableStateOf<Song?>(null)
    var userProfile by mutableStateOf<UserProfile?>(null)

    var otherUserViewState by mutableStateOf(com.ncm.player.model.OtherUserViewState())
        private set
    var currentQueue by mutableStateOf<List<Song>>(emptyList())
    var isPlaying by mutableStateOf(false)
    var recommendedSongs by mutableStateOf<List<Song>>(emptyList())
    var userPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var favoriteSongs by mutableStateOf<List<String>>(emptyList())
    var playlistSongs by mutableStateOf<List<Song>>(emptyList())
    var isFmMode by mutableStateOf(false)
    private var isFetchingMoreFm = false

    var currentQualityWifi by mutableStateOf("standard")
    var currentQualityCellular by mutableStateOf("standard")
    var fadeDuration by mutableStateOf(2f)
    var cacheSize by mutableIntStateOf(500)
    var useCellularCache by mutableStateOf(false)
    var downloadDir by mutableStateOf<String?>(null)
    var downloadQuality by mutableStateOf("standard")
    var isFirstDownload by mutableStateOf(true)
    var allowCellularDownload by mutableStateOf(false)
    var pureBlackMode by mutableStateOf(false)
    var searchHistory by mutableStateOf<List<String>>(emptyList())

    var isLoading by mutableStateOf(false)
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)

    var currentSampleRate by mutableIntStateOf(0)
    var currentBitrate by mutableIntStateOf(0)

    var lastLocalSongsScanTime = 0L
    var localSongs by mutableStateOf<List<Pair<Song, android.net.Uri>>>(emptyList())

    private val ncmDownloadManager = com.ncm.player.manager.NcmDownloadManager(application)
    val downloadTasks = ncmDownloadManager.tasks

    var showCellularDownloadDialog by mutableStateOf<Song?>(null)
    var skipCellularPromptForSession = false

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    val mediaController: MediaController?
        get() = if (mediaControllerFuture?.isDone == true) mediaControllerFuture?.get() else null

    val networkManager = com.ncm.player.util.NetworkManager(application)
    var currentNetworkType by mutableStateOf(com.ncm.player.util.NetworkType.NONE)

    init {
        currentQualityWifi = UserPreferences.getQualityWifi(application)
        currentQualityCellular = UserPreferences.getQualityCellular(application)
        fadeDuration = UserPreferences.getFadeDuration(application)
        cacheSize = UserPreferences.getCacheSize(application)
        useCellularCache = UserPreferences.getUseCellularCache(application)
        downloadDir = UserPreferences.getDownloadDir(application)
        downloadQuality = UserPreferences.getDownloadQuality(application)
        isFirstDownload = UserPreferences.isFirstDownload(application)
        allowCellularDownload = UserPreferences.getAllowCellularDownload(application)
        pureBlackMode = UserPreferences.getPureBlackMode(application)
        searchHistory = UserPreferences.getSearchHistory(application)

        loadInitialCache()
        fetchHotSearches()
        refreshLocalSongs(force = false)

        viewModelScope.launch {
            ncmDownloadManager.completedSongs.collect {
                refreshLocalSongs()
            }
        }
        viewModelScope.launch {
            networkManager.networkType.collect { type ->
                currentNetworkType = type
            }
        }
    }

    private fun loadInitialCache() {
        val cache = UserPreferences.getLocalSongsCache(getApplication())
        if (cache != null) {
            try {
                val array = JsonParser.parseString(cache).asJsonArray
                localSongs = array.mapNotNull {
                    val obj = it.asJsonObject
                    val song = com.google.gson.Gson().fromJson(obj.get("song"), Song::class.java)
                    val uri = android.net.Uri.parse(obj.get("uri").asString)
                    song to uri
                }
            } catch (e: Exception) {
                DebugLog.e("PlayerVM: Failed to load local songs cache", e)
            }
        }
    }

    fun refreshLocalSongs(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - lastLocalSongsScanTime < 5000) return
        viewModelScope.launch(Dispatchers.IO) {
            val list = listLocalSongs(getApplication())
            withContext(Dispatchers.Main) {
                localSongs = list
                lastLocalSongsScanTime = System.currentTimeMillis()
                try {
                    val array = com.google.gson.JsonArray()
                    list.forEach { (song, uri) ->
                        val obj = com.google.gson.JsonObject()
                        obj.add("song", JsonParser.parseString(com.google.gson.Gson().toJson(song)))
                        obj.addProperty("uri", uri.toString())
                        array.add(obj)
                    }
                    UserPreferences.saveLocalSongsCache(getApplication(), array.toString())
                } catch (e: Exception) {
                    DebugLog.e("PlayerVM: Failed to save local songs cache", e)
                }
            }
        }
    }

    private fun listLocalSongs(context: Context): List<Pair<Song, android.net.Uri>> {
        val files = mutableListOf<Pair<Song, android.net.Uri>>()
        val registrySongs = com.ncm.player.manager.DownloadRegistry.getAllDownloadedSongs()
        registrySongs.forEach { metadata ->
            val uri = if (metadata.filePath.startsWith("/")) {
                android.net.Uri.fromFile(java.io.File(metadata.filePath))
            } else {
                android.net.Uri.parse(metadata.filePath)
            }
            files.add(metadata.song to uri)
        }
        return files
    }

    fun playSong(song: Song, playlist: List<Song> = emptyList(), cookie: String? = null) {
        isFmMode = false
        val targetPlaylist = if (playlist.isNotEmpty()) playlist else listOf(song)
        playSongsInternal(targetPlaylist, targetPlaylist.indexOfFirst { it.id == song.id }.coerceAtLeast(0), cookie)
    }

    private fun playSongsInternal(songs: List<Song>, startIndex: Int, cookie: String?) {
        viewModelScope.launch {
            isLoading = true
            runWithController { controller ->
                try {
                    controller.stop()
                    controller.clearMediaItems()
                    val mediaItems = songs.map { createMediaItem(it, cookie) }
                    controller.setMediaItems(mediaItems, startIndex, 0L)
                    controller.prepare()
                    controller.play()
                } catch (e: Exception) {
                    DebugLog.e("PlayerVM: playSongsInternal failed", e)
                } finally {
                    isLoading = false
                }
            }
        }
    }

    private fun createMediaItem(song: Song, cookie: String? = null): MediaItem {
        val extras = android.os.Bundle().apply {
            putString("artistId", song.artistId)
        }
        val isFavorite = favoriteSongs.contains(song.id)
        val metadata = MediaMetadata.Builder()
            .setTitle(song.name)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUrl?.let { android.net.Uri.parse(it) })
            .setUserRating(androidx.media3.common.HeartRating(isFavorite))
            .setExtras(extras)
            .build()

        val localUri = localSongs.find { it.first.id == song.id }?.second
        val mediaUri = if (localUri != null) {
            localUri
        } else {
            val quality = if (currentNetworkType == com.ncm.player.util.NetworkType.WIFI) currentQualityWifi else currentQualityCellular
            android.net.Uri.Builder()
                .scheme("ncm")
                .authority(song.id)
                .appendQueryParameter("quality", quality)
                .apply { if (!cookie.isNullOrEmpty()) appendQueryParameter("cookie", cookie) }
                .build()
        }

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun initController(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController?.let { controller ->
                isPlaying = controller.isPlaying
                currentSong = controller.currentMediaItem?.let { item ->
                    Song(
                        id = item.mediaId,
                        name = item.mediaMetadata.title?.toString() ?: "Unknown",
                        artist = item.mediaMetadata.artist?.toString() ?: "Unknown",
                        album = item.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                        albumArtUrl = item.mediaMetadata.artworkUri?.toString(),
                        artistId = item.mediaMetadata.extras?.getString("artistId")
                    )
                }
                updateQueue()
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        updateQueue()
                        item?.let {
                            currentSong = Song(
                                id = it.mediaId,
                                name = it.mediaMetadata.title?.toString() ?: "Unknown",
                                artist = it.mediaMetadata.artist?.toString() ?: "Unknown",
                                album = it.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                                albumArtUrl = it.mediaMetadata.artworkUri?.toString(),
                                artistId = it.mediaMetadata.extras?.getString("artistId")
                            )
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        // Safe error handling without accessing cause directly in a way that might fail
                        val message = error.message ?: "Unknown"
                        if (message.contains("InterruptedIOException")) return
                        DebugLog.e("PlayerVM: Player Error: $message", error)
                        DebugLog.toast(getApplication(), "Playback Error: ${error.errorCodeName}")
                    }
                })
            }
        }, MoreExecutors.directExecutor())
    }

    private fun updateQueue() {
        mediaController?.let { controller ->
            val list = mutableListOf<Song>()
            for (i in 0 until controller.mediaItemCount) {
                controller.getMediaItemAt(i).let { item ->
                    list.add(Song(
                        id = item.mediaId,
                        name = item.mediaMetadata.title?.toString() ?: "Unknown",
                        artist = item.mediaMetadata.artist?.toString() ?: "Unknown",
                        album = item.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                        albumArtUrl = item.mediaMetadata.artworkUri?.toString(),
                        artistId = item.mediaMetadata.extras?.getString("artistId")
                    ))
                }
            }
            currentQueue = list
        }
    }

    fun runWithController(action: (MediaController) -> Unit) {
        mediaController?.let { action(it) }
    }

    fun skipNext() { runWithController { it.seekToNext() } }
    fun skipPrevious() { runWithController { it.seekToPrevious() } }
    fun seekTo(pos: Long) { runWithController { it.seekTo(pos) } }
    fun togglePlayPause() { runWithController { if (it.isPlaying) it.pause() else it.play() } }

    fun reorderQueueByLiveSort(liveSortViewModel: LiveSortViewModel, cookie: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val cacheDir = File(context.cacheDir, "livesort_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val songsWithPaths = mutableListOf<Pair<Song, String>>()
            val queue = currentQueue.toList()

            for (song in queue) {
                val localUri = localSongs.find { it.first.id == song.id }?.second
                if (localUri != null && localUri.scheme == "file") {
                    songsWithPaths.add(Pair(song, localUri.path ?: ""))
                } else if (localUri != null && localUri.scheme == "content") {
                    val tempFile = File(cacheDir, "${song.id}_local.mp3")
                    if (!tempFile.exists()) {
                        try {
                            context.contentResolver.openInputStream(localUri)?.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        } catch(e: Exception) {}
                    }
                    if (tempFile.exists()) songsWithPaths.add(Pair(song, tempFile.absolutePath))
                } else {
                    val body = callApi("song/url/v1", mapOf("id" to song.id, "level" to "standard", "cookie" to (cookie ?: "")))
                    val dataArray = if (body.has("data")) body.get("data").asJsonArray else null
                    val url = dataArray?.firstOrNull()?.asJsonObject?.get("url")?.asString
                    if (!url.isNullOrEmpty() && url != "null") {
                        val tempFile = File(cacheDir, "${song.id}.mp3")
                        if (!tempFile.exists()) {
                            try {
                                java.net.URL(url).openStream().use { input ->
                                    tempFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            } catch (e: Exception) { }
                        }
                        if (tempFile.exists()) songsWithPaths.add(Pair(song, tempFile.absolutePath))
                    }
                }
            }

            liveSortViewModel.processPlaylist(songsWithPaths)
            val finalState = liveSortViewModel.sortState.first { it is com.ncm.player.viewmodel.LiveSortState.Completed || it is com.ncm.player.viewmodel.LiveSortState.Error }

            if (finalState is com.ncm.player.viewmodel.LiveSortState.Completed) {
                val sortedSongs = finalState.sortedSongs.map { it.song }
                withContext(Dispatchers.Main) {
                    runWithController { controller ->
                        try {
                            val currentMediaId = controller.currentMediaItem?.mediaId
                            val currentPos = if (controller.mediaItemCount > 0) controller.currentPosition else 0L
                            val mediaItems = sortedSongs.map { createMediaItem(it, cookie) }
                            val newIndex = sortedSongs.indexOfFirst { it.id == currentMediaId }.coerceAtLeast(0)

                            controller.setMediaItems(mediaItems, newIndex, currentPos)
                            controller.prepare()
                            controller.play()
                            updateQueue()
                        } catch (e: Exception) {
                            DebugLog.e("PlayerVM: LiveSort apply failed", e)
                        }
                    }
                }
            }
            try { cacheDir.listFiles()?.forEach { it.delete() } } catch (e: Exception) { }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            runWithController { it.sendCustomCommand(androidx.media3.session.SessionCommand("ACTION_CLEAR_CACHE", android.os.Bundle.EMPTY), android.os.Bundle.EMPTY) }
            File(getApplication<Application>().cacheDir, "media").deleteRecursively()
            File(getApplication<Application>().cacheDir, "livesort_cache").deleteRecursively()
        }
    }

    fun fetchPlaylistSongs(id: Long, cookie: String?, sort: String? = null) { }
    fun fetchHotSearches() { }
}
