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
import com.ncm.player.model.DownloadStatus
import com.ncm.player.model.DownloadTask
import com.ncm.player.model.Song
import com.ncm.player.util.UserPreferences
import com.ncm.player.util.RustServerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.net.URLEncoder

class NcmDownloadManager(private val application: Application) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadManager = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val tasks = _tasks.asStateFlow()

    private val _completedSongs = MutableStateFlow<Set<String>>(DownloadRegistry.getAllDownloadedIds())
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
    }

    private fun saveCompletedSong(song: Song, downloadId: Long = -1) {
        if (downloadId != -1L) {
            scope.launch {
                try {
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (uri != null) {
                        val parcelFileDescriptor = application.contentResolver.openFileDescriptor(uri, "r")
                        val fileSize = parcelFileDescriptor?.statSize ?: 0L
                        parcelFileDescriptor?.close()

                        if (fileSize <= 0) {
                            android.util.Log.e("NcmDownload", "Downloaded file is 0B or invalid for ${song.name}. Deleting.")
                            downloadManager.remove(downloadId)
                            _tasks.update { it - song.id }
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(application, "Download failed (invalid file): ${song.name}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }

                        // Determine the final storage path
                        val userDownloadDir = UserPreferences.getDownloadDir(application)
                        val finalFilePath: String

                        if (userDownloadDir != null) {
                            val treeUri = Uri.parse(userDownloadDir)
                            val tree = DocumentFile.fromTreeUri(application, treeUri)
                            val sanitizedName = song.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            val sanitizedArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            val fileName = "$sanitizedName - $sanitizedArtist.mp3"
                            val file = tree?.createFile("audio/mpeg", fileName)

                            if (file != null) {
                                application.contentResolver.openInputStream(uri)?.use { input ->
                                    application.contentResolver.openOutputStream(file.uri)?.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                finalFilePath = file.uri.toString()
                            } else {
                                finalFilePath = uri.toString()
                            }
                        } else {
                            finalFilePath = uri.toString()
                        }

                        // Save to registry
                        DownloadRegistry.register(application, song, finalFilePath)
                        _completedSongs.update { it + song.id }

                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(application, "Download completed: ${song.name}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NcmDownload", "Error processing completed download", e)
                }
            }
        }
    }

    private val downloadMutex = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun downloadSong(song: Song, cookie: String?, quality: String = "standard", allowCellular: Boolean = false) {
        // 1. Check registry and state first
        if (DownloadRegistry.getMetadata(song.id) != null) return
        if (_tasks.value.containsKey(song.id)) return

        // 2. Prevent race conditions on the same song
        if (downloadMutex.putIfAbsent(song.id, true) != null) return

        android.widget.Toast.makeText(application, "Starting download: ${song.name}", android.widget.Toast.LENGTH_SHORT).show()

        scope.launch {
            try {
                // Fetch the actual audio URL via the API first
                val params = mutableMapOf("id" to song.id, "level" to quality)
                cookie?.let { params["cookie"] = it }
                val result = RustServerManager.callApi("song/download/url/v1", params)
                val body = com.google.gson.JsonParser.parseString(result).asJsonObject

                android.util.Log.d("NcmDownload", "API response: $body")
                var url = com.ncm.player.util.JsonUtils.findUrl(body)

                if (url == null) {
                    android.util.Log.d("NcmDownload", "Primary URL null, trying fallback")
                    val fallbackResult = RustServerManager.callApi("song/url/v1", mapOf("id" to song.id, "level" to quality))
                    val fallbackBody = com.google.gson.JsonParser.parseString(fallbackResult).asJsonObject
                    android.util.Log.d("NcmDownload", "Fallback response: $fallbackBody")
                    url = com.ncm.player.util.JsonUtils.findUrl(fallbackBody)
                }

                if (url != null && url.startsWith("http")) {
                    val downloadUrl = url
                    val sanitizedName = song.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val sanitizedArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val fileName = "$sanitizedName - $sanitizedArtist.mp3"

                    val request = DownloadManager.Request(Uri.parse(downloadUrl))
                        .setTitle("Downloading ${song.name}")
                        .setDescription("${song.artist} - ${song.album}")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(allowCellular)
                        .setAllowedOverRoaming(allowCellular)
                        .addRequestHeader("User-Agent", "NeteaseMusic/9.1.20 (iPhone; iOS 16.5; Scale/3.00)")

                    cookie?.let {
                        request.addRequestHeader("Cookie", it)
                    }

                    // Ensure the directory exists
                    val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    val ncmDir = File(musicDir, "NCMPlayer")
                    if (!ncmDir.exists()) {
                        ncmDir.mkdirs()
                    }

                    // Use setDestinationInExternalPublicDir.
                    // The "not a file URL" issue is often because the path provided is already an absolute path
                    // or somehow incorrectly formatted. Using the simple subPath should be correct.
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "NCMPlayer/$fileName")

                    val downloadId = try {
                        downloadManager.enqueue(request)
                    } catch (e: Exception) {
                        android.util.Log.e("NcmDownload", "Failed to enqueue download", e)
                        throw e
                    }

                    _tasks.update { it + (song.id to DownloadTask(song, DownloadStatus.DOWNLOADING, 0f, downloadId)) }
                    trackProgress(song.id, downloadId)
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(application, "No valid audio URL found for ${song.name}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    _tasks.update { it + (song.id to DownloadTask(song, DownloadStatus.FAILED)) }
                }
            } catch (e: Exception) {
                android.util.Log.e("NcmDownload", "Download error", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(application, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
                _tasks.update { it + (song.id to DownloadTask(song, DownloadStatus.FAILED)) }
            } finally {
                downloadMutex.remove(song.id)
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
                                saveCompletedSong(currentTask.song, downloadId)
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
        if (task.status != DownloadStatus.COMPLETED) {
            _tasks.update { it + (songId to task.copy(status = DownloadStatus.COMPLETED, progress = 1f)) }
            saveCompletedSong(task.song, downloadId)
        }
    }

    fun cancelDownload(songId: String) {
        val task = _tasks.value[songId] ?: return
        if (task.downloadId != -1L) {
            try {
                downloadManager.remove(task.downloadId)
            } catch (e: Exception) {
                android.util.Log.e("NcmDownload", "Failed to remove download: ${task.downloadId}", e)
            }
        }
        _tasks.update { it - songId }
    }
}
