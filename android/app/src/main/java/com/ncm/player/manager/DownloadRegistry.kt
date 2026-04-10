package com.ncm.player.manager

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ncm.player.model.Song
import com.ncm.player.util.DebugLog
import java.io.File

data class DownloadedSongMetadata(
    val song: Song,
    val filePath: String,
    val downloadTime: Long = System.currentTimeMillis()
)

object DownloadRegistry {
    private const val REGISTRY_FILE = "download_registry.json"
    private val gson = Gson()
    private var cachedMetadata: MutableMap<String, DownloadedSongMetadata> = mutableMapOf()

    fun init(context: Context) {
        val file = File(context.filesDir, REGISTRY_FILE)
        DebugLog.d("DownloadRegistry: Initializing from ${file.absolutePath}")
        if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<MutableMap<String, DownloadedSongMetadata>>() {}.type
                cachedMetadata = gson.fromJson(json, type) ?: mutableMapOf()
                DebugLog.d("DownloadRegistry: Loaded ${cachedMetadata.size} entries")

                // Clean up non-existent files from registry
                val iterator = cachedMetadata.entries.iterator()
                var removedCount = 0
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    val path = entry.value.filePath
                    val exists = if (path.startsWith("content://")) {
                        try {
                            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, android.net.Uri.parse(path))?.exists() == true
                        } catch (e: Exception) {
                            DebugLog.d("DownloadRegistry: Error checking SAF uri $path")
                            false
                        }
                    } else if (path.startsWith("file://")) {
                         File(android.net.Uri.parse(path).path ?: "").exists()
                    } else {
                        File(path).exists()
                    }

                    if (!exists) {
                        DebugLog.d("DownloadRegistry: File not found, removing from registry: $path")
                        iterator.remove()
                        removedCount++
                    }
                }
                if (removedCount > 0) {
                    DebugLog.i("DownloadRegistry: Cleaned up $removedCount missing entries")
                    save(context)
                }
            } catch (e: Exception) {
                DebugLog.e("DownloadRegistry: Failed to load registry", e)
                cachedMetadata = mutableMapOf()
            }
        } else {
            DebugLog.i("DownloadRegistry: Registry file does not exist yet")
        }
    }

    fun register(context: Context, song: Song, filePath: String) {
        cachedMetadata[song.id] = DownloadedSongMetadata(song, filePath)
        save(context)
    }

    fun unregister(context: Context, songId: String) {
        cachedMetadata.remove(songId)
        save(context)
    }

    fun getMetadata(songId: String): DownloadedSongMetadata? {
        return cachedMetadata[songId]
    }

    fun getAllDownloadedIds(): Set<String> {
        return cachedMetadata.keys
    }

    fun getAllDownloadedSongs(): List<DownloadedSongMetadata> {
        return cachedMetadata.values.toList()
    }

    private fun save(context: Context) {
        try {
            val file = File(context.filesDir, REGISTRY_FILE)
            file.writeText(gson.toJson(cachedMetadata))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
