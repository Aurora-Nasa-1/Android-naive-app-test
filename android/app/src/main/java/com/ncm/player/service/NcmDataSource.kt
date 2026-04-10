package com.ncm.player.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ContentDataSource
import com.ncm.player.manager.DownloadRegistry
import com.ncm.player.util.RustServerManager
import com.ncm.player.util.JsonUtils
import com.ncm.player.util.DebugLog
import com.google.gson.JsonParser
import java.io.IOException

class NcmDataSource(
    private val context: Context,
    private val httpDataSource: DataSource
) : BaseDataSource(true) {

    private val fileDataSource = FileDataSource()
    private val contentDataSource = ContentDataSource(context)
    private var activeDataSource: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val uri = dataSpec.uri

        if (uri.scheme != "ncm") {
            throw IOException("Unsupported scheme: ${uri.scheme}")
        }

        val songId = uri.host ?: throw IOException("Invalid song ID in URI: $uri")
        val quality = uri.getQueryParameter("quality") ?: "standard"
        val cookie = uri.getQueryParameter("cookie")

        // 1. Check local registry
        val metadata = DownloadRegistry.getMetadata(songId)
        if (metadata != null) {
            DebugLog.d("NcmDS: Found local file for $songId: ${metadata.filePath}")
            val localUri = Uri.parse(metadata.filePath)
            val localDataSpec = dataSpec.withUri(localUri)

            val ds = if (localUri.scheme == "content") {
                contentDataSource
            } else {
                fileDataSource
            }

            activeDataSource = ds
            val bytesRead = ds.open(localDataSpec)
            transferStarted(dataSpec)
            return bytesRead
        }

        // 2. Resolve via JNI
        val params = mutableMapOf("id" to songId, "level" to quality)
        cookie?.let { params["cookie"] = it }

        DebugLog.d("NcmDS: Resolving CDN URL for $songId (quality: $quality)...")
        val result = RustServerManager.callApi("song/url/v1", params)
        val body = JsonParser.parseString(result).asJsonObject

        val cdnUrl = JsonUtils.findUrl(body)
            ?: run {
                DebugLog.e("NcmDS: Failed to find URL in response for $songId: $result")
                throw IOException("Failed to resolve NCM URL for ID $songId: $result")
            }

        DebugLog.d("NcmDS: Resolved URL for $songId: $cdnUrl")
        val resolvedUri = Uri.parse(cdnUrl)
        val resolvedDataSpec = dataSpec.withUri(resolvedUri)

        activeDataSource = httpDataSource
        return try {
            val bytesRead = httpDataSource.open(resolvedDataSpec)
            transferStarted(dataSpec)
            bytesRead
        } catch (e: IOException) {
            DebugLog.e("NcmDS: HTTP open failed for $songId: ${e.message}", e)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return activeDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? {
        return activeDataSource?.uri
    }

    override fun close() {
        transferEnded()
        activeDataSource?.close()
        activeDataSource = null
    }

    class Factory(
        private val context: Context,
        private val httpDataSourceFactory: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return NcmDataSource(context, httpDataSourceFactory.createDataSource())
        }
    }
}
