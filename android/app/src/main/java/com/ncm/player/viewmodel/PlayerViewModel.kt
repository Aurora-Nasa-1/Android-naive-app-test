package com.ncm.player.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.ncm.player.model.Playlist
import com.ncm.player.model.Song
import com.ncm.player.model.UserProfile
import com.ncm.player.model.Contact
import com.ncm.player.model.Message
import com.ncm.player.service.MusicService
import com.ncm.player.util.UserPreferences
import com.ncm.player.util.DebugLog
import com.ncm.player.util.RustServerManager
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private fun callApi(method: String, params: Map<String, String> = emptyMap()): JsonObject {
        val result = RustServerManager.callApi(method, params)
        return JsonParser.parseString(result).asJsonObject
    }

    var currentSong by mutableStateOf<Song?>(null)
    var userProfile by mutableStateOf<UserProfile?>(null)
    var otherUserProfile by mutableStateOf<UserProfile?>(null)
    var otherUserPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var otherUserSongs by mutableStateOf<List<Song>>(emptyList())
    var currentQueue by mutableStateOf<List<Song>>(emptyList())
    var isPlaying by mutableStateOf(false)
    var recommendedSongs by mutableStateOf<List<Song>>(emptyList())
    var userPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var favoriteSongs by mutableStateOf<List<String>>(emptyList())
    var playlistSongs by mutableStateOf<List<Song>>(emptyList())
    var searchResults by mutableStateOf<List<Song>>(emptyList())
    var searchHistory by mutableStateOf<List<String>>(emptyList())
    var hotSearches by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var searchSuggestions by mutableStateOf<List<String>>(emptyList())
    var currentLyrics by mutableStateOf<List<LyricLine>>(emptyList())
    var isLoading by mutableStateOf(false)
    var likedSongsPlaylistId by mutableStateOf(0L)
    var isFmMode by mutableStateOf(false)
    private var isFetchingMoreFm = false

    var contacts by mutableStateOf<List<Contact>>(emptyList())
    var chatMessages by mutableStateOf<List<Message>>(emptyList())

    var currentQualityWifi by mutableStateOf("exhigh")
    var currentQualityCellular by mutableStateOf("standard")
    var fadeDuration by mutableStateOf(2f)
    var cacheSize by mutableStateOf(512)
    var useCellularCache by mutableStateOf(false)
    var downloadDir by mutableStateOf<String?>(null)
    var downloadQuality by mutableStateOf("standard")
    var isFirstDownload by mutableStateOf(true)
    var allowCellularDownload by mutableStateOf(false)
    var pureBlackMode by mutableStateOf(false)
    var showCellularDownloadDialog by mutableStateOf<Song?>(null)
    private var skipCellularPromptForSession = false

    val ncmDownloadManager = com.ncm.player.manager.NcmDownloadManager(application)

    var localSongs by mutableStateOf<List<Pair<Song, android.net.Uri>>>(emptyList())

    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
    var shuffleMode by mutableStateOf(false)
    var currentPosition by mutableStateOf(0L)
    var duration by mutableStateOf(0L)

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

        viewModelScope.launch {
            ncmDownloadManager.completedSongs.collect {
                refreshLocalSongs()
            }
        }
        viewModelScope.launch {
            networkManager.networkType.collect { type ->
                currentNetworkType = type
                if (type == com.ncm.player.util.NetworkType.CELLULAR && !allowCellularDownload && !skipCellularPromptForSession) {
                    val activeTasks = ncmDownloadManager.tasks.value.values.filter {
                        it.status == com.ncm.player.model.DownloadStatus.DOWNLOADING
                    }
                    if (activeTasks.isNotEmpty()) {
                        showCellularDownloadDialog = activeTasks.first().song
                    }
                }
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
        val files = mutableListOf<Pair<Song, android.net.Uri>>()

        // 1. Add songs from our DownloadRegistry first (these are the "official" downloads)
        val registrySongs = com.ncm.player.manager.DownloadRegistry.getAllDownloadedSongs()
        registrySongs.forEach { metadata ->
            val uri = android.net.Uri.parse(metadata.filePath)
            // Verify file still exists (DownloadRegistry usually handles this, but let's be sure)
            val exists = if (uri.scheme == "content") {
                 androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.exists() == true
            } else {
                 java.io.File(uri.path ?: "").exists()
            }

            if (exists) {
                files.add(metadata.song to uri)
            }
        }

        // 2. Scan common directories for other local music that might not be in our registry
        val userDirUri = com.ncm.player.util.UserPreferences.getDownloadDir(context)
        val registryIds = registrySongs.map { it.song.id }.toSet()

        fun scanDir(tree: androidx.documentfile.provider.DocumentFile?) {
            tree?.listFiles()?.forEach { file ->
                if (file.name?.endsWith(".mp3") == true || file.name?.endsWith(".flac") == true) {
                    // Only add if not already in registry to avoid duplicates
                    if (files.none { it.second == file.uri }) {
                        files.add(Song(
                            id = "local_${file.name}",
                            name = file.name?.removeSuffix(".mp3")?.removeSuffix(".flac") ?: "Unknown",
                            artist = "Local File",
                            album = "Local Storage"
                        ) to file.uri)
                    }
                }
            }
        }

        if (userDirUri != null) {
            try {
                val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(userDirUri))
                scanDir(tree)
            } catch (e: Exception) { e.printStackTrace() }
        }

        val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
        val ncmMusicDir = java.io.File(musicDir, "NCMPlayer")

        listOf(musicDir, ncmMusicDir).forEach { dir ->
            if (dir.exists()) {
                dir.listFiles { _, name -> name.endsWith(".mp3") || name.endsWith(".flac") }?.forEach { file ->
                    val uri = android.net.Uri.fromFile(file)
                    if (files.none { it.second == uri }) {
                        files.add(Song(
                            id = "local_${file.name}",
                            name = file.nameWithoutExtension,
                            artist = "Local File",
                            album = "Local Storage"
                        ) to uri)
                    }
                }
            }
        }

        return files.distinctBy { it.second }
    }

    fun setQualityWifi(quality: String) {
        currentQualityWifi = quality
        UserPreferences.saveQualityWifi(getApplication(), quality)
    }

    fun setQualityCellular(quality: String) {
        currentQualityCellular = quality
        UserPreferences.saveQualityCellular(getApplication(), quality)
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

    fun updateAllowCellularDownload(allow: Boolean) {
        allowCellularDownload = allow
        UserPreferences.saveAllowCellularDownload(getApplication(), allow)
    }

    fun updatePureBlackMode(enabled: Boolean) {
        pureBlackMode = enabled
        UserPreferences.savePureBlackMode(getApplication(), enabled)
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(getApplication<Application>().cacheDir, "media")
                cacheDir.deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun initController(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController?.let { controller ->
                // Sync initial state when connected
                isPlaying = controller.isPlaying
                currentPosition = controller.currentPosition
                duration = controller.duration.coerceAtLeast(0L)
                repeatMode = controller.repeatMode
                shuffleMode = controller.shuffleModeEnabled
                controller.currentMediaItem?.let { mediaItem ->
                    currentSong = Song(
                        id = mediaItem.mediaId,
                        name = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                        artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown",
                        album = mediaItem.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                        albumArtUrl = mediaItem.mediaMetadata.artworkUri?.toString(),
                        artistId = mediaItem.mediaMetadata.extras?.getString("artistId")
                    )
                }
                updateQueue()

                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        this@PlayerViewModel.isPlaying = isPlaying
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateQueue()

                        // Handle infinite FM fetching
                        if (isFmMode && !isFetchingMoreFm) {
                            val currentIndex = controller.currentMediaItemIndex
                            val totalItems = controller.mediaItemCount
                            // Fetch more when we reach the last 2 songs
                            if (currentIndex >= totalItems - 2) {
                                fetchMoreFmSongs(UserPreferences.getCookie(getApplication()))
                            }
                        }

                        mediaItem?.mediaMetadata?.let { metadata ->
                            currentSong = Song(
                                id = mediaItem.mediaId,
                                name = metadata.title?.toString() ?: "Unknown",
                                artist = metadata.artist?.toString() ?: "Unknown",
                                album = metadata.albumTitle?.toString() ?: "Unknown",
                                albumArtUrl = metadata.artworkUri?.toString(),
                                artistId = metadata.extras?.getString("artistId")
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

    private fun loadInitialCache() {
        val application = getApplication<Application>()
        val recCache = UserPreferences.getRecommendedSongsCache(application)
        if (recCache != null) {
            try {
                val array = JsonParser.parseString(recCache).asJsonArray
                recommendedSongs = array.mapNotNull { com.ncm.player.util.JsonUtils.parseSong(it) }
            } catch (e: Exception) { e.printStackTrace() }
        }

        val upCache = UserPreferences.getUserProfileCache(application)
        if (upCache != null) {
            try {
                userProfile = com.google.gson.Gson().fromJson(upCache, UserProfile::class.java)
            } catch (e: Exception) { e.printStackTrace() }
        }

        val plCache = UserPreferences.getUserPlaylistsCache(application)
        if (plCache != null) {
            try {
                val array = JsonParser.parseString(plCache).asJsonArray
                userPlaylists = array.map {
                    val obj = it.asJsonObject
                    Playlist(
                        id = obj.get("id").asLong,
                        name = obj.get("name").asString,
                        coverImgUrl = obj.get("coverImgUrl").asString,
                        trackCount = obj.get("trackCount").asInt
                    )
                }
                likedSongsPlaylistId = userPlaylists.find { it.name.contains("喜欢的音乐") }?.id ?: userPlaylists.firstOrNull()?.id ?: 0L
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun fetchUserData(cookie: String?) {
        if (cookie.isNullOrEmpty()) return

        viewModelScope.launch {
            isLoading = true
            try {
                coroutineScope {
                    val recDeferred = async(Dispatchers.IO) {
                        val body = callApi("recommend/songs", mapOf("cookie" to cookie))
                        val songsJson = body.get("data")?.asJsonObject?.get("dailySongs")?.asJsonArray
                            ?: body.get("dailySongs")?.asJsonArray

                        val songs = songsJson?.mapNotNull { com.ncm.player.util.JsonUtils.parseSong(it) } ?: emptyList()
                        if (songs.isNotEmpty()) {
                            UserPreferences.saveRecommendedSongsCache(getApplication(), com.google.gson.Gson().toJson(songs))
                        }
                        songs
                    }

                    val userAndFavDeferred = async(Dispatchers.IO) {
                        val statusBody = callApi("login/status", mapOf("cookie" to cookie))
                        val profileJson = statusBody.get("data")?.asJsonObject?.get("profile")?.asJsonObject
                            ?: statusBody.get("profile")?.asJsonObject

                        val uid = statusBody.get("data")?.asJsonObject?.get("account")?.asJsonObject?.get("id")?.asLong
                            ?: statusBody.get("account")?.asJsonObject?.get("id")?.asLong
                            ?: profileJson?.get("userId")?.asLong
                            ?: 0L

                        if (uid != 0L) {
                            if (profileJson != null) {
                                val up = UserProfile(
                                    userId = uid,
                                    nickname = profileJson.get("nickname").asString,
                                    avatarUrl = profileJson.get("avatarUrl").asString,
                                    signature = profileJson.get("signature")?.asString
                                )
                                withContext(Dispatchers.Main) {
                                    userProfile = up
                                }
                                UserPreferences.saveUserProfileCache(getApplication(), com.google.gson.Gson().toJson(up))
                            }

                            val plDeferred = async {
                                val plBody = callApi("user/playlist", mapOf("uid" to uid.toString(), "cookie" to cookie))
                                val playlistJson = plBody.get("playlist")?.asJsonArray
                                val playlists = playlistJson?.map {
                                    val obj = it.asJsonObject
                                    Playlist(
                                        id = obj.get("id").asLong,
                                        name = obj.get("name").asString,
                                        coverImgUrl = obj.get("coverImgUrl").asString,
                                        trackCount = obj.get("trackCount").asInt
                                    )
                                } ?: emptyList()
                                if (playlists.isNotEmpty()) {
                                    UserPreferences.saveUserPlaylistsCache(getApplication(), com.google.gson.Gson().toJson(playlists))
                                }
                                playlists
                            }

                            val favDeferred = async {
                                val favBody = callApi("likelist", mapOf("uid" to uid.toString(), "cookie" to cookie))
                                val favJson = favBody.get("ids")?.asJsonArray
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
                    likedSongsPlaylistId = playlists.find { it.name.contains("喜欢的音乐") }?.id ?: playlists.firstOrNull()?.id ?: 0L
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    var searchPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var searchType by mutableIntStateOf(1)

    fun search(keywords: String, type: Int = 1) {
        if (keywords.isBlank()) {
            searchResults = emptyList()
            searchPlaylists = emptyList()
            return
        }

        searchType = type
        // Update history
        val newHistory = (listOf(keywords) + searchHistory.filter { it != keywords }).take(10)
        searchHistory = newHistory
        UserPreferences.saveSearchHistory(getApplication(), newHistory)

        viewModelScope.launch {
            isLoading = true
            try {
                val body = withContext(Dispatchers.IO) {
                    callApi("cloudsearch", mapOf("keywords" to keywords, "type" to type.toString()))
                }
                val resultObj = body.get("result")?.asJsonObject

                when (type) {
                    1 -> {
                        val songsJson = resultObj?.get("songs")?.asJsonArray
                        searchResults = songsJson?.mapNotNull { com.ncm.player.util.JsonUtils.parseSong(it) } ?: emptyList()
                        searchPlaylists = emptyList()
                    }
                    1000 -> {
                        val playlistsJson = resultObj?.get("playlists")?.asJsonArray
                        searchPlaylists = playlistsJson?.map {
                            val obj = it.asJsonObject
                            Playlist(
                                id = obj.get("id").asLong,
                                name = obj.get("name").asString,
                                coverImgUrl = obj.get("coverImgUrl").asString,
                                trackCount = obj.get("trackCount").asInt
                            )
                        } ?: emptyList()
                        searchResults = emptyList()
                    }
                    else -> {
                        // For albums (10) and artists (100), we can adapt them to Song or just show empty for now
                        // Implementation can be expanded as needed
                        searchResults = emptyList()
                        searchPlaylists = emptyList()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun searchSongs(keywords: String) = search(keywords, 1)

    fun fetchHotSearches() {
        viewModelScope.launch {
            try {
                val body = withContext(Dispatchers.IO) { callApi("search/hot/detail") }
                val data = body.get("data")?.asJsonArray
                hotSearches = data?.map {
                    val obj = it.asJsonObject
                    obj.get("searchWord").asString to (obj.get("content")?.asString ?: "")
                } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchSearchSuggestions(keywords: String) {
        if (keywords.isBlank()) {
            searchSuggestions = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val body = withContext(Dispatchers.IO) { callApi("search/suggest", mapOf("keywords" to keywords, "type" to "mobile")) }
                val result = body.get("result")?.asJsonObject
                val allSuggestions = mutableListOf<String>()

                result?.get("allMatch")?.asJsonArray?.forEach {
                    allSuggestions.add(it.asJsonObject.get("keyword").asString)
                }
                searchSuggestions = allSuggestions
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearSearchHistory() {
        searchHistory = emptyList()
        UserPreferences.saveSearchHistory(getApplication(), emptyList())
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<String>, cookie: String?) {
        viewModelScope.launch {
            try {
                val tracks = songIds.joinToString(",")
                withContext(Dispatchers.IO) {
                    callApi("playlist/tracks", mapOf("op" to "add", "pid" to playlistId.toString(), "tracks" to tracks, "cookie" to (cookie ?: "")))
                }
                // Optionally refresh playlist songs if we are viewing it
                if (playlistSongs.any { songIds.contains(it.id) }) {
                    fetchPlaylistSongs(playlistId, cookie)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun removeSongsFromPlaylist(playlistId: Long, songIds: List<String>, cookie: String?) {
        viewModelScope.launch {
            try {
                val tracks = songIds.joinToString(",")
                withContext(Dispatchers.IO) {
                    callApi("playlist/tracks", mapOf("op" to "del", "pid" to playlistId.toString(), "tracks" to tracks, "cookie" to (cookie ?: "")))
                }
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
        // Load from cache first
        val cachedJson = UserPreferences.getPlaylistCache(getApplication(), playlistId)
        if (cachedJson != null) {
            try {
                val array = JsonParser.parseString(cachedJson).asJsonArray
                playlistSongs = array.mapNotNull { com.ncm.player.util.JsonUtils.parseSong(it) }
            } catch (e: Exception) { e.printStackTrace() }
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val body = withContext(Dispatchers.IO) { callApi("playlist/track/all", mapOf("id" to playlistId.toString(), "cookie" to (cookie ?: ""))) }
                val songsJson = body.get("songs")?.asJsonArray
                val unsortedSongs = songsJson?.mapNotNull { com.ncm.player.util.JsonUtils.parseSong(it) } ?: emptyList()

                if (unsortedSongs.isNotEmpty()) {
                    UserPreferences.savePlaylistCache(getApplication(), playlistId, com.google.gson.Gson().toJson(unsortedSongs))
                }

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
                val body = withContext(Dispatchers.IO) { callApi("like", mapOf("id" to songId, "like" to like.toString(), "cookie" to (cookie ?: ""))) }
                if (body.get("code")?.asInt == 200) {
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
                val body = withContext(Dispatchers.IO) { callApi("lyric/new", mapOf("id" to songId)) }
                val lrc = body.get("lrc")?.asJsonObject?.get("lyric")?.asString ?: ""
                val tlyric = body.get("tlyric")?.asJsonObject?.get("lyric")?.asString ?: ""
                val yrc = body.get("yrc")?.asJsonObject?.get("lyric")?.asString ?: ""
                val romanyric = body.get("romanyric")?.asJsonObject?.get("lyric")?.asString ?: ""

                var lyricLines = if (yrc.isNotEmpty()) {
                    com.ncm.player.util.LyricUtils.parseYrc(yrc)
                } else {
                    com.ncm.player.util.LyricUtils.parseLrc(lrc, duration)
                }

                if (tlyric.isNotEmpty()) {
                    val tlines = com.ncm.player.util.LyricUtils.parseLrc(tlyric).associateBy { it.time }
                    lyricLines = lyricLines.map { line ->
                        val trans = tlines.entries.find { it.key >= line.time - 500 && it.key <= line.time + 500 }
                        if (trans != null) {
                            line.copy(translation = trans.value.text)
                        } else {
                            line
                        }
                    }
                }

                if (romanyric.isNotEmpty()) {
                    val rlines = com.ncm.player.util.LyricUtils.parseLrc(romanyric).associateBy { it.time }
                    lyricLines = lyricLines.map { line ->
                        val roma = rlines.entries.find { it.key >= line.time - 500 && it.key <= line.time + 500 }
                        if (roma != null) {
                            line.copy(romanization = roma.value.text)
                        } else {
                            line
                        }
                    }
                }

                currentLyrics = lyricLines

            } catch (e: Exception) {
                e.printStackTrace()
                currentLyrics = emptyList()
            }
        }
    }

    data class LyricLine(
        val time: Long,
        val text: String,
        val translation: String? = null,
        val romanization: String? = null,
        val secondary: String? = null,
        val endTime: Long? = null,
        val words: List<Word>? = null
    ) {
        data class Word(val text: String, val beginTime: Long, val endTime: Long)
    }

    fun downloadSong(song: Song, cookie: String?, ignoreNetwork: Boolean = false) {
        if (!ignoreNetwork && currentNetworkType == com.ncm.player.util.NetworkType.CELLULAR && !allowCellularDownload && !skipCellularPromptForSession) {
            showCellularDownloadDialog = song
            return
        }
        ncmDownloadManager.downloadSong(song, cookie, downloadQuality, allowCellular = allowCellularDownload || ignoreNetwork)
    }

    fun confirmCellularDownload(song: Song, cookie: String?, skipForSession: Boolean) {
        if (skipForSession) {
            skipCellularPromptForSession = true
        }
        showCellularDownloadDialog = null
        ncmDownloadManager.downloadSong(song, cookie, downloadQuality, allowCellular = true)
    }

    fun playSong(song: Song, playlist: List<Song> = emptyList(), cookie: String? = null, localUri: android.net.Uri? = null) {
        isFmMode = false // Reset FM mode on normal song play
        viewModelScope.launch {
            try {
                mediaController?.let { controller ->
                    controller.stop()
                    controller.clearMediaItems()

                    val targetPlaylist = if (playlist.isNotEmpty()) playlist else listOf(song)
                    val startIndex = targetPlaylist.indexOf(song).coerceAtLeast(0)

                    val mediaItems = targetPlaylist.map { s ->
                        if (s.id == song.id && localUri != null) {
                            createMediaItem(s, localUri = localUri)
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

    private fun createMediaItem(song: Song, cookie: String? = null, localUri: android.net.Uri? = null): MediaItem {
        val extras = android.os.Bundle().apply {
            putString("artistId", song.artistId)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.name)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUrl?.let { android.net.Uri.parse(it) })
            .setExtras(extras)
            .build()

        // Check registry for local version even if localUri is not provided explicitly
        val finalLocalUri = localUri ?: com.ncm.player.manager.DownloadRegistry.getMetadata(song.id)?.let {
             android.net.Uri.parse(it.filePath)
        }

        val uri = if (finalLocalUri != null) {
            finalLocalUri.toString()
        } else {
            val quality = if (currentNetworkType == com.ncm.player.util.NetworkType.WIFI) currentQualityWifi else currentQualityCellular
            if (!cookie.isNullOrEmpty()) {
                "http://127.0.0.1:3000/song/url/v1/302?id=${song.id}&level=$quality&cookie=${URLEncoder.encode(cookie, "UTF-8")}"
            } else {
                "http://127.0.0.1:3000/song/url/v1/302?id=${song.id}&level=$quality"
            }
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
                DebugLog.toast(getApplication(), "Starting Private FM...")
                DebugLog.d("Fetching Personal FM songs...")
                val body = withContext(Dispatchers.IO) { callApi("personal_fm", mapOf("cookie" to (cookie ?: ""))) }

                if (body.get("code")?.asInt != 200 && !body.has("data")) {
                    val errorMsg = "API Error: ${body.get("msg")?.asString ?: "Unknown"}"
                    DebugLog.e(errorMsg)
                    DebugLog.toast(getApplication(), errorMsg)
                    return@launch
                }

                DebugLog.d("Personal FM raw body: $body")

                val songsJson = body?.get("data")?.asJsonArray ?: body?.get("result")?.asJsonArray
                DebugLog.d("Songs JSON size: ${songsJson?.size() ?: 0}")

                val songs = songsJson?.mapNotNull { com.ncm.player.util.JsonUtils.parseSong(it) } ?: emptyList()

                if (songs.isNotEmpty()) {
                    DebugLog.d("Playing ${songs.size} Personal FM songs")
                    isFmMode = true
                    playSongInternal(songs[0], songs, cookie)
                } else {
                    DebugLog.toast(getApplication(), "No FM songs returned from server")
                }
            } catch (e: Exception) {
                DebugLog.e("Personal FM Exception", e)
                DebugLog.toast(getApplication(), "Personal FM Failed: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    private fun fetchMoreFmSongs(cookie: String?) {
        if (isFetchingMoreFm) return
        isFetchingMoreFm = true
        viewModelScope.launch {
            try {
                DebugLog.d("Fetching more Personal FM songs...")
                val body = withContext(Dispatchers.IO) { callApi("personal_fm", mapOf("cookie" to (cookie ?: ""))) }
                if (body.has("data") || body.has("result")) {
                    val songsJson = body.get("data")?.asJsonArray ?: body.get("result")?.asJsonArray
                val songs = songsJson?.mapNotNull { com.ncm.player.util.JsonUtils.parseSong(it) } ?: emptyList()

                    mediaController?.let { controller ->
                        songs.forEach { song ->
                            controller.addMediaItem(createMediaItem(song, cookie))
                        }
                        DebugLog.d("Added ${songs.size} more songs to FM queue")
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("Failed to fetch more FM songs", e)
            } finally {
                isFetchingMoreFm = false
            }
        }
    }

    private fun playSongInternal(song: Song, playlist: List<Song> = emptyList(), cookie: String? = null) {
        viewModelScope.launch {
            try {
                mediaController?.let { controller ->
                    controller.stop()
                    controller.clearMediaItems()
                    val targetPlaylist = if (playlist.isNotEmpty()) playlist else listOf(song)
                    val startIndex = targetPlaylist.indexOf(song).coerceAtLeast(0)
                    val mediaItems = targetPlaylist.map { createMediaItem(it, cookie) }
                    controller.setMediaItems(mediaItems, startIndex, 0L)
                    controller.prepare()
                    controller.play()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun playHeartbeat(songId: String, playlistId: Long, cookie: String?) {
        viewModelScope.launch {
            try {
                isLoading = true
                DebugLog.toast(getApplication(), "Starting Heartbeat Mode...")
                DebugLog.d("Fetching Heartbeat for songId: $songId, playlistId: $playlistId")
                val body = withContext(Dispatchers.IO) {
                    callApi("playmode/intelligence/list", mapOf(
                        "id" to songId,
                        "pid" to playlistId.toString(),
                        "sid" to songId,
                        "count" to "20",
                        "cookie" to (cookie ?: "")
                    ))
                }

                if (body.get("code")?.asInt != 200 && !body.has("data")) {
                    val errorMsg = "API Error: ${body.get("msg")?.asString ?: "Unknown"}"
                    DebugLog.e(errorMsg)
                    DebugLog.toast(getApplication(), errorMsg)
                    return@launch
                }

                DebugLog.d("Heartbeat raw body: $body")

                // Fix: NCM Intelligence API returns an object where "data" might be an array OR an object containing "data" as an array
                val songsJson = when {
                    body?.get("data")?.isJsonArray == true -> {
                        DebugLog.d("Heartbeat: Found data as JsonArray")
                        body.get("data").asJsonArray
                    }
                    body?.get("data")?.isJsonObject == true && body.get("data").asJsonObject.has("data") -> {
                        DebugLog.d("Heartbeat: Found data.data as JsonArray")
                        body.get("data").asJsonObject.get("data").asJsonArray
                    }
                    body?.get("data")?.isJsonObject == true && body.get("data").asJsonObject.has("list") -> {
                        DebugLog.d("Heartbeat: Found data.list as JsonArray")
                        body.get("data").asJsonObject.get("list").asJsonArray
                    }
                    body?.has("list") == true && body.get("list").isJsonArray -> {
                        DebugLog.d("Heartbeat: Found root.list as JsonArray")
                        body.get("list").asJsonArray
                    }
                    else -> {
                        val keys = body?.keySet()?.joinToString(", ") ?: "null"
                        DebugLog.e("Heartbeat: No recognizable song list. Keys: $keys")
                        DebugLog.toast(getApplication(), "Parsing failed. Keys: $keys")
                        null
                    }
                }

                DebugLog.d("Heartbeat JSON list size: ${songsJson?.size() ?: 0}")

                val songs = songsJson?.mapNotNull { com.ncm.player.util.JsonUtils.parseSong(it) } ?: emptyList()

                if (songs.isNotEmpty()) {
                    DebugLog.d("Playing ${songs.size} Heartbeat songs")
                    playSong(songs[0], songs, cookie)
                } else {
                    DebugLog.toast(getApplication(), "No Heartbeat songs returned from server")
                }
            } catch (e: Exception) {
                DebugLog.e("Heartbeat Exception", e)
                DebugLog.toast(getApplication(), "Heartbeat Failed: ${e.message}")
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
                    albumArtUrl = metadata.artworkUri?.toString(),
                    artistId = metadata.extras?.getString("artistId")
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

    fun fetchRecentContacts(cookie: String?) {
        if (cookie.isNullOrEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { isLoading = true }
                DebugLog.d("Fetching recent contacts...")
                var body = callApi("msg/recentcontact", mapOf("cookie" to cookie, "limit" to "100"))
                DebugLog.d("Recent contacts response: $body")

                var contactJson = body.get("recentcontacts")?.asJsonArray
                    ?: body.get("data")?.asJsonObject?.get("recentcontacts")?.asJsonArray

                // Fallback to msg/private if recentcontact is empty
                if (contactJson == null || contactJson.size() == 0) {
                    DebugLog.d("msg/recentcontact empty, trying msg/private...")
                    body = callApi("msg/private", mapOf("cookie" to cookie, "limit" to "100"))
                    contactJson = body.get("msgs")?.asJsonArray
                }

                val list = contactJson?.mapNotNull { com.ncm.player.util.JsonUtils.parseContact(it) } ?: emptyList()
                withContext(Dispatchers.Main) {
                    contacts = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun fetchMessageHistory(uid: Long, cookie: String?) {
        if (cookie.isNullOrEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val myUserId = userProfile?.userId ?: 0L
                DebugLog.d("Fetching message history for $uid...")
                // Note: The Rust API might expect 'uid' parameter as 'userId' in some contexts, but callApi handles mapping.
                // However, some versions of the API use msg/private/history while others use msg/history.
                // We'll try both if one fails or returns empty, but for now we fix the route mapping.
                var body = callApi("msg/private/history", mapOf("uid" to uid.toString(), "cookie" to cookie, "limit" to "100"))

                // If it fails or returns nothing, try alternative route
                if (!body.has("msgs") || body.get("msgs").asJsonArray.size() == 0) {
                     DebugLog.d("msg/private/history returned empty, trying msg/history...")
                     body = callApi("msg/history", mapOf("uid" to uid.toString(), "cookie" to cookie, "limit" to "100"))
                }

                DebugLog.d("Message history response: $body")
                val msgsJson = body.get("msgs")?.asJsonArray
                val list = msgsJson?.mapNotNull { com.ncm.player.util.JsonUtils.parseMessage(it, myUserId) }?.reversed() ?: emptyList()
                withContext(Dispatchers.Main) {
                    chatMessages = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendTextMessage(uid: Long, text: String, cookie: String?) {
        if (cookie.isNullOrEmpty() || text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val body = callApi("send/text", mapOf("user_ids" to uid.toString(), "msg" to text, "cookie" to cookie))
                if (body.get("code")?.asInt == 200) {
                    fetchMessageHistory(uid, cookie)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var otherUserArtists by mutableStateOf<List<Song>>(emptyList())

    fun fetchOtherUserProfile(uid: Long, cookie: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { isLoading = true }
                DebugLog.d("Fetching user profile for $uid...")
                var body = callApi("user/detail", mapOf("uid" to uid.toString(), "id" to uid.toString(), "cookie" to (cookie ?: "")))

                // If standard fails or is empty, try user/detail/new
                if (!body.has("profile")) {
                    DebugLog.d("user/detail failed, trying user/detail/new...")
                    body = callApi("user/detail/new", mapOf("uid" to uid.toString(), "id" to uid.toString(), "cookie" to (cookie ?: "")))
                }

                DebugLog.d("User profile response: $body")
                val profileJson = body.get("profile")?.asJsonObject
                if (profileJson != null) {
                    val up = UserProfile(
                        userId = uid,
                        nickname = profileJson.get("nickname").asString,
                        avatarUrl = profileJson.get("avatarUrl").asString,
                        signature = profileJson.get("signature")?.asString,
                        gender = profileJson.get("gender").asInt,
                        followed = profileJson.get("followed").asBoolean,
                        follows = profileJson.get("follows").asInt,
                        followeds = profileJson.get("followeds").asInt,
                        eventCount = profileJson.get("eventCount").asInt,
                        playlistCount = profileJson.get("playlistCount").asInt
                    )
                    withContext(Dispatchers.Main) {
                        otherUserProfile = up
                    }
                }

                // If it's an artist ID (often smaller or in a specific range, but we try anyway)
                val artistBody = callApi("artist/detail", mapOf("id" to uid.toString(), "cookie" to (cookie ?: "")))
                if (artistBody.has("data")) {
                    val artistJson = artistBody.get("data").asJsonObject.get("artist").asJsonObject
                    val up = UserProfile(
                        userId = uid,
                        nickname = artistJson.get("name").asString,
                        avatarUrl = artistJson.get("cover").asString,
                        signature = artistJson.get("briefDesc")?.asString,
                        playlistCount = 0
                    )
                    withContext(Dispatchers.Main) {
                        otherUserProfile = up
                    }

                    val songsBody = callApi("artist/songs", mapOf("id" to uid.toString(), "cookie" to (cookie ?: "")))
                    val songsJson = songsBody.get("songs")?.asJsonArray
                    val songs = songsJson?.mapNotNull { com.ncm.player.util.JsonUtils.parseSong(it) } ?: emptyList()
                    withContext(Dispatchers.Main) {
                        otherUserSongs = songs
                    }
                }

                // Fetch their playlists
                val plBody = callApi("user/playlist", mapOf("uid" to uid.toString(), "cookie" to (cookie ?: "")))
                val playlistJson = plBody.get("playlist")?.asJsonArray
                val playlists = playlistJson?.map {
                    val obj = it.asJsonObject
                    Playlist(
                        id = obj.get("id").asLong,
                        name = obj.get("name").asString,
                        coverImgUrl = obj.get("coverImgUrl").asString,
                        trackCount = obj.get("trackCount").asInt
                    )
                } ?: emptyList()
                withContext(Dispatchers.Main) {
                    otherUserPlaylists = playlists
                }

                // Fetch first playlist's songs as "popular" or recent songs if any
                if (playlists.isNotEmpty()) {
                    val sBody = callApi("playlist/track/all", mapOf("id" to playlists[0].id.toString(), "limit" to "20", "cookie" to (cookie ?: "")))
                    val songsJson = sBody.get("songs")?.asJsonArray
                    val songs = songsJson?.mapNotNull { com.ncm.player.util.JsonUtils.parseSong(it) } ?: emptyList()
                    withContext(Dispatchers.Main) {
                        otherUserSongs = songs
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
