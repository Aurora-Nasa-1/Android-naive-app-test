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
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val apiService = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:3000/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NcmApiService::class.java)

    var currentSong by mutableStateOf<Song?>(null)
    var currentQueue by mutableStateOf<List<Song>>(emptyList())
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
    var cacheSize by mutableStateOf(512)
    var useCellularCache by mutableStateOf(false)
    var downloadDir by mutableStateOf<String?>(null)
    var downloadQuality by mutableStateOf("standard")
    var isFirstDownload by mutableStateOf(true)

    val ncmDownloadManager = com.ncm.player.manager.NcmDownloadManager(application, apiService)

    var localSongs by mutableStateOf<List<Pair<Song, android.net.Uri>>>(emptyList())

    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
    var shuffleMode by mutableStateOf(false)
    var currentPosition by mutableStateOf(0L)
    var duration by mutableStateOf(0L)

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    val mediaController: MediaController?
        get() = if (mediaControllerFuture?.isDone == true) mediaControllerFuture?.get() else null

    init {
        currentQuality = UserPreferences.getQuality(application)
        fadeDuration = UserPreferences.getFadeDuration(application)
        cacheSize = UserPreferences.getCacheSize(application)
        useCellularCache = UserPreferences.getUseCellularCache(application)
        downloadDir = UserPreferences.getDownloadDir(application)
        downloadQuality = UserPreferences.getDownloadQuality(application)
        isFirstDownload = UserPreferences.isFirstDownload(application)

        viewModelScope.launch {
            ncmDownloadManager.completedSongs.collect {
                refreshLocalSongs()
            }
        }
    }

    fun deleteLocalSong(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Deleting song...", android.widget.Toast.LENGTH_SHORT).show()
                }
                val deleted = if (uri.scheme == "content") {
                    // Try content resolver first for system-managed files
                    try {
                        context.contentResolver.delete(uri, null, null) > 0
                    } catch (e: Exception) {
                        // Fallback to DocumentFile for SAF
                        val file = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                        file?.delete() ?: false
                    }
                } else {
                    val file = java.io.File(uri.path ?: "")
                    if (file.exists()) file.delete() else false
                }

                withContext(Dispatchers.Main) {
                    if (deleted) {
                        android.widget.Toast.makeText(context, "Song deleted", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Failed to delete song", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                refreshLocalSongs()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Error deleting song: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun refreshLocalSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = listLocalSongs(getApplication())
            withContext(Dispatchers.Main) {
                localSongs = list
            }
        }
    }

    private fun listLocalSongs(context: android.content.Context): List<Pair<Song, android.net.Uri>> {
        val userDirUri = com.ncm.player.util.UserPreferences.getDownloadDir(context)
        val files = mutableListOf<Pair<Song, android.net.Uri>>()
        val prefs = com.ncm.player.util.UserPreferences.getPrefs(context)
        val completedIds = prefs.getStringSet("completed_downloads", emptySet()) ?: emptySet()

        val metadataMap = completedIds.associateWith { id ->
            prefs.getString("metadata_$id", null)?.split("|")
        }

        fun findMetadata(fileName: String): Song? {
            val cleanName = fileName.removeSuffix(".mp3")
            metadataMap.forEach { (id, parts) ->
                if (parts != null && parts.size >= 3) {
                    val songName = parts[0]
                    val artist = parts[1]
                    if (cleanName.contains(songName) && cleanName.contains(artist)) {
                        return Song(id, songName, artist, parts[2], parts.getOrNull(3))
                    }
                }
            }
            return null
        }

        if (userDirUri != null) {
            try {
                val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(userDirUri))
                tree?.listFiles()?.forEach { file ->
                    val name = file.name
                    if (name?.endsWith(".mp3") == true) {
                        val matchedSong = findMetadata(name)
                        files.add((matchedSong ?: Song(
                            id = "local_$name",
                            name = name.removeSuffix(".mp3"),
                            artist = "Local",
                            album = "Downloads"
                        )) to file.uri)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
        val ncmMusicDir = java.io.File(musicDir, "NCMPlayer")
        val dirsToScan = listOf(musicDir, ncmMusicDir)

        dirsToScan.forEach { dir ->
            if (dir.exists()) {
                dir.listFiles { _, name -> name.endsWith(".mp3") }?.forEach { file ->
                    val matchedSong = findMetadata(file.name)
                    files.add((matchedSong ?: Song(
                        id = "local_${file.name}",
                        name = file.nameWithoutExtension,
                        artist = "Local File",
                        album = "Downloads"
                    )) to android.net.Uri.fromFile(file))
                }
            }
        }

        return files.distinctBy { it.second }
    }

    fun setQuality(quality: String) {
        currentQuality = quality
        UserPreferences.saveQuality(getApplication(), quality)
    }

    fun setFade(duration: Float) {
        fadeDuration = duration
        UserPreferences.saveFadeDuration(getApplication(), duration)
    }

    fun setCache(size: Int) {
        cacheSize = size
        UserPreferences.saveCacheSize(getApplication(), size)
    }

    fun setUseCellular(use: Boolean) {
        useCellularCache = use
        UserPreferences.saveUseCellularCache(getApplication(), use)
    }

    fun setDownloadPath(path: String) {
        downloadDir = path
        UserPreferences.saveDownloadDir(getApplication(), path)
    }

    fun updateDownloadQuality(quality: String) {
        downloadQuality = quality
        UserPreferences.saveDownloadQuality(getApplication(), quality)
        if (isFirstDownload) {
            isFirstDownload = false
            UserPreferences.setFirstDownloadComplete(getApplication())
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(getApplication<Application>().cacheDir, "media")
                cacheDir.deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            // Pre-fetch next song metadata or logic if needed
                        }
                        updateQueue()
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
                            updateQueue()
                        }
                    }

                    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                        updateQueue()
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
                    coroutineScope {
                        val recDeferred = async {
                            val response = apiService.getRecommendSongs(cookie)
                            val body = response.body()
                            val songsJson = body?.get("data")?.asJsonObject?.get("dailySongs")?.asJsonArray
                                ?: body?.get("dailySongs")?.asJsonArray

                            songsJson?.map {
                                val obj = it.asJsonObject
                                Song(
                                    id = obj.get("id").asString,
                                    name = obj.get("name").asString,
                                    artist = obj.get("ar").asJsonArray.get(0).asJsonObject.get("name").asString,
                                    album = obj.get("al").asJsonObject.get("name").asString,
                                    albumArtUrl = obj.get("al").asJsonObject.get("picUrl").asString
                                )
                            } ?: emptyList()
                        }

                        val userAndFavDeferred = async {
                            val statusResponse = apiService.loginStatus(cookie = cookie)
                            val statusBody = statusResponse.body()
                            val uid = statusBody?.get("data")?.asJsonObject?.get("account")?.asJsonObject?.get("id")?.asLong
                                ?: statusBody?.get("account")?.asJsonObject?.get("id")?.asLong
                                ?: statusBody?.get("profile")?.asJsonObject?.get("userId")?.asLong
                                ?: 0L

                            if (uid != 0L) {
                                val plDeferred = async {
                                    val plResponse = apiService.getUserPlaylist(uid, cookie)
                                    val playlistJson = plResponse.body()?.get("playlist")?.asJsonArray
                                    playlistJson?.map {
                                        val obj = it.asJsonObject
                                        Playlist(
                                            id = obj.get("id").asLong,
                                            name = obj.get("name").asString,
                                            coverImgUrl = obj.get("coverImgUrl").asString,
                                            trackCount = obj.get("trackCount").asInt
                                        )
                                    } ?: emptyList()
                                }

                                val favDeferred = async {
                                    val favResponse = apiService.getLikeList(uid, cookie)
                                    val favJson = favResponse.body()?.get("ids")?.asJsonArray
                                    favJson?.map { it.asString } ?: emptyList()
                                }

                                Pair(plDeferred.await(), favDeferred.await())
                            } else {
                                Pair(emptyList(), emptyList())
                            }
                        }

                        recommendedSongs = recDeferred.await()
                        val (playlists, favs) = userAndFavDeferred.await()
                        userPlaylists = playlists
                        favoriteSongs = favs
                    }
                    isLoading = false
                    break // Success, exit retry loop
                } catch (e: Exception) {
                    e.printStackTrace()
                    retryCount++
                    if (retryCount < maxRetries) {
                        delay(1000L * retryCount)
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
        songs.forEach { ncmDownloadManager.downloadSong(it, cookie, downloadQuality) }
    }

    fun fetchPlaylistSongs(playlistId: Long, cookie: String?, sort: String? = null) {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = apiService.getPlaylistTracks(playlistId, cookie)
                val songsJson = response.body()?.get("songs")?.asJsonArray
                val unsortedSongs = songsJson?.map {
                    val obj = it.asJsonObject
                    Song(
                        id = obj.get("id").asString,
                        name = obj.get("name").asString,
                        artist = obj.get("ar").asJsonArray.get(0).asJsonObject.get("name").asString,
                        album = obj.get("al").asJsonObject.get("name").asString,
                        albumArtUrl = obj.get("al").asJsonObject.get("picUrl").asString
                    )
                } ?: emptyList()

                val order = sort ?: UserPreferences.getPlaylistSort(getApplication(), playlistId)
                playlistSongs = when (order) {
                    "name" -> unsortedSongs.sortedBy { it.name }
                    "artist" -> unsortedSongs.sortedBy { it.artist }
                    else -> unsortedSongs
                }
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
        ncmDownloadManager.downloadSong(song, cookie, downloadQuality)
    }

    fun playSong(song: Song, playlist: List<Song> = emptyList(), cookie: String? = null, localUri: android.net.Uri? = null) {
        viewModelScope.launch {
            try {
                mediaController?.let { controller ->
                    controller.stop()
                    controller.clearMediaItems()

                    val targetPlaylist = if (playlist.isNotEmpty()) playlist else listOf(song)
                    val startIndex = targetPlaylist.indexOf(song).coerceAtLeast(0)

                    val mediaItems = targetPlaylist.map { s ->
                        if (s.id == song.id && localUri != null) {
                            createLocalMediaItem(s, localUri)
                        } else {
                            createMediaItem(s, cookie)
                        }
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

    private fun createLocalMediaItem(song: Song, uri: android.net.Uri): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.name)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUrl?.let { android.net.Uri.parse(it) })
            .build()

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
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

    fun playPersonalFm(cookie: String?) {
        viewModelScope.launch {
            try {
                isLoading = true
                val response = apiService.getPersonalFm(cookie)
                val body = response.body()
                val songsJson = body?.get("data")?.asJsonArray ?: body?.get("result")?.asJsonArray
                val songs = songsJson?.map {
                    val obj = it.asJsonObject
                    val artists = obj.get("artists")?.asJsonArray ?: obj.get("ar")?.asJsonArray
                    val artistName = artists?.get(0)?.asJsonObject?.get("name")?.asString ?: "Unknown"
                    val album = obj.get("album")?.asJsonObject ?: obj.get("al")?.asJsonObject
                    val albumName = album?.get("name")?.asString ?: "Unknown"
                    val picUrl = album?.get("picUrl")?.asString

                    Song(
                        id = obj.get("id").asJsonPrimitive.asString,
                        name = obj.get("name").asString,
                        artist = artistName,
                        album = albumName,
                        albumArtUrl = picUrl
                    )
                } ?: emptyList()
                if (songs.isNotEmpty()) {
                    playSong(songs[0], songs, cookie)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun playHeartbeat(songId: String, playlistId: Long, cookie: String?) {
        viewModelScope.launch {
            try {
                isLoading = true
                val response = apiService.getIntelligenceList(songId, playlistId, cookie)
                val body = response.body()
                val songsJson = body?.get("data")?.asJsonArray
                val songs = songsJson?.map {
                    val item = it.asJsonObject
                    val obj = if (item.has("songInfo")) item.get("songInfo").asJsonObject else item

                    val artists = obj.get("ar")?.asJsonArray ?: obj.get("artists")?.asJsonArray
                    val artistName = artists?.get(0)?.asJsonObject?.get("name")?.asString ?: "Unknown"
                    val album = obj.get("al")?.asJsonObject ?: obj.get("album")?.asJsonObject
                    val albumName = album?.get("name")?.asString ?: "Unknown"
                    val picUrl = album?.get("picUrl")?.asString

                    Song(
                        id = obj.get("id").asJsonPrimitive.asString,
                        name = obj.get("name").asString,
                        artist = artistName,
                        album = albumName,
                        albumArtUrl = picUrl
                    )
                } ?: emptyList()
                if (songs.isNotEmpty()) {
                    playSong(songs[0], songs, cookie)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun updateQueue() {
        mediaController?.let { controller ->
            val list = mutableListOf<Song>()
            for (i in 0 until controller.mediaItemCount) {
                val item = controller.getMediaItemAt(i)
                val metadata = item.mediaMetadata
                list.add(Song(
                    id = item.mediaId,
                    name = metadata.title?.toString() ?: "Unknown",
                    artist = metadata.artist?.toString() ?: "Unknown",
                    album = metadata.albumTitle?.toString() ?: "Unknown",
                    albumArtUrl = metadata.artworkUri?.toString()
                ))
            }
            currentQueue = list
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        mediaController?.moveMediaItem(fromIndex, toIndex)
        updateQueue()
    }

    fun removeQueueItem(index: Int) {
        mediaController?.removeMediaItem(index)
        updateQueue()
    }

    fun clearQueue() {
        mediaController?.clearMediaItems()
        updateQueue()
    }

    override fun onCleared() {
        super.onCleared()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
