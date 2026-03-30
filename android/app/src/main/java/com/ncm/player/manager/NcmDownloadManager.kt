package com.ncm.player.manager

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.*
import androidx.documentfile.provider.DocumentFile
import com.ncm.player.api.NcmApiService
import com.ncm.player.model.DownloadStatus
import com.ncm.player.model.DownloadTask
import com.ncm.player.model.Song
import com.ncm.player.util.UserPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.net.URLEncoder

class NcmDownloadManager(private val application: Application, private val apiService: NcmApiService) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadManager = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val tasks = _tasks.asStateFlow()

    private val _completedSongs = MutableStateFlow<Set<String>>(emptySet())
    val completedSongs = _completedSongs.asStateFlow()

    init {
        application.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id != -1L) {
                        updateTaskCompletion(id)
                    }
                }
            },
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
        loadCompletedSongs()
    }

    private fun loadCompletedSongs() {
        val prefs = UserPreferences.getPrefs(application)
        val completed = prefs.getStringSet("completed_downloads", emptySet()) ?: emptySet()
        _completedSongs.value = completed
    }

    private fun saveCompletedSong(song: Song) {
        val prefs = UserPreferences.getPrefs(application)
        val current = prefs.getStringSet("completed_downloads", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(song.id)

        // Save metadata for local matching
        val metadata = "${song.name}|${song.artist}|${song.album}|${song.albumArtUrl ?: ""}"
        prefs.edit()
            .putStringSet("completed_downloads", current)
            .putString("metadata_${song.id}", metadata)
            .apply()

        _completedSongs.update { it + song.id }
    }

    fun downloadSong(song: Song, cookie: String?, quality: String = "standard") {
        if (_completedSongs.value.contains(song.id)) return
        if (_tasks.value.containsKey(song.id)) return

        val br = when (quality) {
            "standard" -> 128000
            "higher" -> 192000
            "exhigh" -> 320000
            "lossless" -> 999000
            "hires" -> 999000
            else -> 128000
        }

        scope.launch {
            try {
                val response = apiService.getDownloadUrl(song.id, br = br, cookie = cookie)
                val url = response.body()?.get("data")?.asJsonArray?.get(0)?.asJsonObject?.get("url")?.asString

                url?.let { downloadUrl ->
                    val request = DownloadManager.Request(Uri.parse(downloadUrl))
                        .setTitle("Downloading ${song.name}")
                        .setDescription("${song.artist} - ${song.album}")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)

                    val userDownloadDir = UserPreferences.getDownloadDir(application)
                    val fileName = "${song.name} - ${song.artist}.mp3"

                    if (userDownloadDir != null) {
                        try {
                            val treeUri = Uri.parse(userDownloadDir)
                            val tree = DocumentFile.fromTreeUri(application, treeUri)
                            val file = tree?.createFile("audio/mpeg", fileName)
                            file?.uri?.let { destinationUri ->
                                request.setDestinationUri(destinationUri)
                            } ?: run {
                                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "NCMPlayer/$fileName")
                            }
                        } catch (e: Exception) {
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "NCMPlayer/$fileName")
                        }
                    } else {
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "NCMPlayer/$fileName")
                    }

                    val downloadId = downloadManager.enqueue(request)
                    _tasks.update { it + (song.id to DownloadTask(song, DownloadStatus.DOWNLOADING, 0f, downloadId)) }

                    trackProgress(song.id, downloadId)
                }
            } catch (e: Exception) {
                _tasks.update { it + (song.id to DownloadTask(song, DownloadStatus.FAILED)) }
            }
        }
    }

    private fun trackProgress(songId: String, downloadId: Long) {
        scope.launch {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)

                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                    val bytesTotal = cursor.getLong(bytesTotalIndex)

                    val progress = if (bytesTotal > 0) bytesDownloaded.toFloat() / bytesTotal else 0f

                    val currentTask = _tasks.value[songId]
                    if (currentTask == null) {
                        downloading = false
                    } else {
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                _tasks.update { it + (songId to currentTask.copy(status = DownloadStatus.COMPLETED, progress = 1f)) }
                                saveCompletedSong(currentTask.song)
                                downloading = false
                            }
                            DownloadManager.STATUS_FAILED -> {
                                _tasks.update { it + (songId to currentTask.copy(status = DownloadStatus.FAILED)) }
                                downloading = false
                            }
                            else -> {
                                _tasks.update { it + (songId to currentTask.copy(progress = progress)) }
                            }
                        }
                    }
                }
                cursor.close()
                delay(1000)
            }
        }
    }

    private fun updateTaskCompletion(downloadId: Long) {
        val entry = _tasks.value.entries.find { it.value.downloadId == downloadId } ?: return
        val songId = entry.key
        val task = entry.value
        _tasks.update { it + (songId to task.copy(status = DownloadStatus.COMPLETED, progress = 1f)) }
        saveCompletedSong(task.song)
    }

    fun cancelDownload(songId: String) {
        val task = _tasks.value[songId] ?: return
        if (task.downloadId != -1L) {
            downloadManager.remove(task.downloadId)
        }
        _tasks.update { it - songId }
    }
}
