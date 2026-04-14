package com.ncm.player.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.ncm.player.model.*
import com.ncm.player.util.JsonUtils
import com.ncm.player.util.UserPreferences
import kotlinx.coroutines.*

class UserViewModel(application: Application) : BaseViewModel(application) {
    var userProfile by mutableStateOf<UserProfile?>(null)
    var userPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var favoriteSongs by mutableStateOf<List<String>>(emptyList())
    var recommendedSongs by mutableStateOf<List<Song>>(emptyList())
    var cloudSongs by mutableStateOf<List<Song>>(emptyList())

    var playlistSongs by mutableStateOf<List<Song>>(emptyList())
    var currentPlaylistMetadata by mutableStateOf<Playlist?>(null)

    var likedSongsPlaylistId by mutableLongStateOf(0L)
    var otherUserViewState by mutableStateOf(OtherUserViewState())

    init {
        loadCache()
    }

    private fun loadCache() {
        UserPreferences.getUserProfileCache(getApplication())?.let {
            userProfile = Gson().fromJson(it, UserProfile::class.java)
        }
        UserPreferences.getRecommendedSongsCache(getApplication())?.let {
            recommendedSongs = JsonParser.parseString(it).asJsonArray.mapNotNull { s -> JsonUtils.parseSong(s) }
        }
        UserPreferences.getUserPlaylistsCache(getApplication())?.let {
            userPlaylists = JsonParser.parseString(it).asJsonArray.map { p ->
                val obj = p.asJsonObject
                Playlist(obj.get("id").asLong, obj.get("name").asString, obj.get("coverImgUrl").asString, obj.get("trackCount").asInt)
            }
            likedSongsPlaylistId = userPlaylists.find { it.name.contains("喜欢的音乐") }?.id ?: userPlaylists.firstOrNull()?.id ?: 0L
        }
    }

    fun fetchUserData() {
        if (cookie == null) return
        viewModelScope.launch {
            isLoading = true
            try {
                coroutineScope {
                    val statusDef = async(Dispatchers.IO) { callApi("login/status") }
                    val recDef = async(Dispatchers.IO) { callApi("recommend/songs") }

                    val statusBody = statusDef.await()
                    val profileJson = statusBody.get("data")?.asJsonObject?.get("profile")?.asJsonObject
                        ?: statusBody.get("profile")?.asJsonObject
                    val uid = profileJson?.get("userId")?.asLong ?: 0L

                    if (uid != 0L) {
                        userProfile = UserProfile(
                            userId = uid,
                            nickname = profileJson?.get("nickname")?.asString ?: "Unknown",
                            avatarUrl = profileJson?.get("avatarUrl")?.asString ?: ""
                        )

                        val plBody = withContext(Dispatchers.IO) { callApi("user/playlist", mapOf("uid" to uid.toString())) }
                        userPlaylists = plBody.get("playlist")?.asJsonArray?.map {
                            val obj = it.asJsonObject
                            Playlist(obj.get("id").asLong, obj.get("name").asString, obj.get("coverImgUrl").asString, obj.get("trackCount").asInt)
                        } ?: emptyList()

                        val favBody = withContext(Dispatchers.IO) { callApi("likelist", mapOf("uid" to uid.toString())) }
                        favoriteSongs = favBody.get("ids")?.asJsonArray?.map { it.asString } ?: emptyList()
                    }

                    val recBody = recDef.await()
                    recommendedSongs = (recBody.get("data")?.asJsonObject?.get("dailySongs")?.asJsonArray
                        ?: recBody.get("dailySongs")?.asJsonArray)
                        ?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
                }
            } finally { isLoading = false }
        }
    }

    fun fetchPlaylistSongs(playlistId: Long) {
        viewModelScope.launch {
            isLoading = true
            try {
                // Fetch details first to ensure large image/title are correct
                val detailBody = withContext(Dispatchers.IO) { callApi("playlist/detail", mapOf("id" to playlistId.toString())) }
                val plObj = detailBody.get("playlist")?.asJsonObject
                if (plObj != null) {
                    currentPlaylistMetadata = Playlist(
                        plObj.get("id").asLong,
                        plObj.get("name").asString,
                        plObj.get("coverImgUrl").asString,
                        plObj.get("trackCount").asInt
                    )
                }

                val body = withContext(Dispatchers.IO) { callApi("playlist/track/all", mapOf("id" to playlistId.toString())) }
                playlistSongs = body.get("songs")?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
            } finally { isLoading = false }
        }
    }

    fun fetchCloudSongs() {
        if (cookie == null) return
        viewModelScope.launch {
            isLoading = true
            try {
                val body = withContext(Dispatchers.IO) { callApi("user/cloud", mapOf("limit" to "100")) }
                cloudSongs = body.get("data")?.asJsonArray?.mapNotNull {
                    val obj = it.asJsonObject
                    // Cloud songs have a bit different structure
                    val songName = obj.get("songName")?.asString ?: "Unknown"
                    val artist = obj.get("artist")?.asString ?: "Unknown"
                    val album = obj.get("album")?.asString ?: "Unknown"
                    val songId = obj.get("songId")?.asString ?: obj.get("songId")?.asLong?.toString() ?: ""

                    Song(
                        id = songId,
                        name = songName,
                        artist = artist,
                        album = album,
                        albumArtUrl = null // Cloud songs usually don't have direct picUrl in /user/cloud
                    )
                } ?: emptyList()
            } finally { isLoading = false }
        }
    }

    fun toggleLike(songId: String, like: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val body = callApi("like", mapOf("id" to songId, "like" to like.toString()))
            if (body.get("code")?.asInt == 200) {
                withContext(Dispatchers.Main) {
                    favoriteSongs = if (like) favoriteSongs + songId else favoriteSongs - songId
                }
            }
        }
    }

    fun dislikeSong(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val body = callApi("recommend/songs/dislike", mapOf("id" to songId))
            if (body.get("code")?.asInt == 200) {
                withContext(Dispatchers.Main) {
                    recommendedSongs = recommendedSongs.filter { it.id != songId }
                }
            }
        }
    }

    fun addSongsToPlaylist(pid: Long, ids: List<String>, cookie: String?) {
        viewModelScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                callApi("playlist/tracks", mapOf("op" to "add", "pid" to pid.toString(), "tracks" to ids.joinToString(",")))
            }
            fetchPlaylistSongs(pid)
        }
    }

    fun removeSongsFromPlaylist(pid: Long, ids: List<String>, cookie: String?) {
        viewModelScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                callApi("playlist/tracks", mapOf("op" to "del", "pid" to pid.toString(), "tracks" to ids.joinToString(",")))
            }
            fetchPlaylistSongs(pid)
        }
    }

    fun fetchOtherUserProfile(uid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { otherUserViewState = OtherUserViewState(uid = uid, isLoading = true) }
                val body = callApi("user/detail", mapOf("uid" to uid.toString()))
                val profileJson = body.get("profile")?.asJsonObject
                if (profileJson != null) {
                    val profile = UserProfile(userId = uid, nickname = profileJson?.get("nickname")?.asString ?: "Unknown", avatarUrl = profileJson?.get("avatarUrl")?.asString ?: "")
                    val plBody = callApi("user/playlist", mapOf("uid" to uid.toString()))
                    val playlists = plBody.get("playlist")?.asJsonArray?.map {
                        val obj = it.asJsonObject
                        Playlist(obj.get("id").asLong, obj.get("name").asString, obj.get("coverImgUrl").asString, obj.get("trackCount").asInt)
                    } ?: emptyList()
                    withContext(Dispatchers.Main) { otherUserViewState = OtherUserViewState(uid = uid, profile = profile, playlists = playlists, isLoading = false) }
                } else {
                    withContext(Dispatchers.Main) { otherUserViewState = otherUserViewState.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { otherUserViewState = otherUserViewState.copy(isLoading = false) }
            }
        }
    }
}
