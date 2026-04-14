package com.ncm.player.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.ncm.player.manager.NcmDownloadManager
import com.ncm.player.manager.DownloadRegistry
import com.ncm.player.manager.DownloadedSongMetadata
import androidx.compose.runtime.*
import com.ncm.player.model.Song
import com.ncm.player.model.DownloadTask
import com.ncm.player.model.DownloadStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadViewModel(application: Application) : BaseViewModel(application) {
    private val downloadManager = NcmDownloadManager(application)
    val tasks: StateFlow<Map<String, DownloadTask>> = downloadManager.tasks
    val completedSongs: StateFlow<Set<String>> = downloadManager.completedSongs
    val downloadedSongs = DownloadRegistry.downloadedSongsFlow

    var downloadQuality by mutableStateOf("standard")
    var isFirstDownload by mutableStateOf(true)
    var allowCellularDownload by mutableStateOf(false)
    var showCellularDownloadDialog by mutableStateOf<Song?>(null)

    fun downloadSong(song: Song, quality: String = "standard") {
        downloadManager.downloadSong(song, cookie, quality)
    }

    fun cancelDownload(songId: String) {
        downloadManager.cancelDownload(songId)
    }

    fun updateDownloadQuality(q: String) { downloadQuality = q }
    fun updateAllowCellularDownload(a: Boolean) { allowCellularDownload = a }
    fun batchDownload(songs: List<Song>, cookie: String?) { songs.forEach { downloadSong(it) } }
}
