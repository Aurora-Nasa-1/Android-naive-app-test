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

        android.widget.Toast.makeText(application, "Starting download: ${song.name}", android.widget.Toast.LENGTH_SHORT).show()

        scope.launch {
            try {
                val response = apiService.getDownloadUrl(song.id, level = quality, cookie = cookie)
                var url = com.ncm.player.util.JsonUtils.findUrl(response.body())

                if (url == null) {
                    val fallbackResp = apiService.getSongUrl(song.id, level = quality)
                    url = com.ncm.player.util.JsonUtils.findUrl(fallbackResp.body())
                }

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
                } ?: run {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(application, "Failed to get download URL for ${song.name}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    _tasks.update { it + (song.id to DownloadTask(song, DownloadStatus.FAILED)) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(application, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
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
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusIndex != -1) cursor.getInt(statusIndex) else -1

                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val currentTask = _tasks.value[songId]
                    if (currentTask == null) {
                        downloading = false
                    } else {
                        val bytesDownloaded = if (bytesDownloadedIndex != -1) cursor.getLong(bytesDownloadedIndex) else 0L
                        val bytesTotal = if (bytesTotalIndex != -1) cursor.getLong(bytesTotalIndex) else -1L

                        val progress = if (bytesTotal > 0) bytesDownloaded.toFloat() / bytesTotal else -1f

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
                            DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                                _tasks.update { it + (songId to currentTask.copy(
                                    status = DownloadStatus.DOWNLOADING,
                                    progress = progress
                                )) }
                            }
                        }
                    }
                } else {
                    val task = _tasks.value[songId]
                    if (task?.status != DownloadStatus.COMPLETED) {
                        downloading = false
                    }
                }
                cursor?.close()
                delay(500)
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
