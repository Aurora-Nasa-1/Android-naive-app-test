package com.ncm.player.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ContentDataSource
import com.ncm.player.manager.DownloadRegistry
import com.ncm.player.util.RustServerManager
import com.ncm.player.util.JsonUtils
import com.ncm.player.util.DebugLog
import com.google.gson.JsonParser
import java.io.IOException

/**
 * NcmDataSource handles ncm://songId URIs.
 * It resolves them to either local files or CDN URLs.
 * It extends BaseDataSource to ensure proper notification to CacheDataSource.
 */
class NcmDataSource(
    private val context: Context,
    private val httpDataSource: DataSource
) : BaseDataSource(/* isNetwork= */ true) {

    private val fileDataSource = FileDataSource()
    private val contentDataSource = ContentDataSource(context)
    private var activeDataSource: DataSource? = null
    private var openedDataSpec: DataSpec? = null

    override fun open(dataSpec: DataSpec): Long {
        openedDataSpec = dataSpec
        transferInitializing(dataSpec)

        val uri = dataSpec.uri
        val songId = if (uri.scheme == "ncm") uri.host else null

        // Ensure consistent key for caching
        val baseDataSpec = if (songId != null && dataSpec.key == null) {
            dataSpec.buildUpon().setKey(songId).build()
        } else {
            dataSpec
        }

        if (uri.scheme != "ncm") {
            val ds = when (uri.scheme) {
                "content" -> contentDataSource
                else -> fileDataSource
            }
            activeDataSource = ds
            val bytesRead = ds.open(dataSpec)
            transferStarted(baseDataSpec)
            return bytesRead
        }

        val id = songId ?: throw IOException("Invalid song ID in URI: $uri")
        val quality = uri.getQueryParameter("quality") ?: "standard"
        val cookie = uri.getQueryParameter("cookie")

        // 1. Check local registry
        val metadata = DownloadRegistry.getMetadata(id)
        if (metadata != null) {
            val localUri = Uri.parse(metadata.filePath)
            val localDataSpec = dataSpec.withUri(localUri).buildUpon()
                .setKey(id)
                .build()

            val ds = when (localUri.scheme) {
                "content" -> contentDataSource
                else -> fileDataSource
            }

            activeDataSource = ds
            val bytesRead = try {
                ds.open(localDataSpec)
            } catch (e: Exception) {
                DebugLog.e("NcmDS: Local open failed for $id: ${e.message}")
                throw e
            }
            transferStarted(baseDataSpec)
            return bytesRead
        }

        // 2. Resolve via JNI
        val params = mutableMapOf("id" to id, "level" to quality)
        cookie?.let { params["cookie"] = it }

        var lastError = ""
        var cdnUrl: String? = null

        for (attempt in 1..3) {
            try {
                val method = if (attempt == 1) "song/url/v1/302" else "song/url/v1"
                val result = RustServerManager.callApi(method, params)
                val body = JsonParser.parseString(result).asJsonObject

                cdnUrl = body.get("redirectUrl")?.asString ?: JsonUtils.findUrl(body)

                if (cdnUrl != null && cdnUrl.startsWith("http")) {
                    break
                }

                val code = body.get("code")?.asInt ?: -1
                val message = body.get("message")?.asString ?: body.get("msg")?.asString ?: "No URL"
                lastError = "Code $code: $message"
                if (code == 404 || code == 403) break
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown"
            }
            if (attempt < 3) Thread.sleep(200L)
        }

        if (cdnUrl == null || !cdnUrl.startsWith("http")) {
            throw IOException("Failed to resolve $id: $lastError")
        }

        val resolvedUri = Uri.parse(cdnUrl)
        val resolvedDataSpec = dataSpec.withUri(resolvedUri).buildUpon()
            .setKey(id)
            .build()

        activeDataSource = httpDataSource
        val bytesRead = try {
            httpDataSource.open(resolvedDataSpec)
        } catch (e: IOException) {
            DebugLog.e("NcmDS: HTTP open failed for $id: ${e.message}")
            throw e
        }

        transferStarted(baseDataSpec)
        return bytesRead
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = activeDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        if (bytesRead != C.RESULT_END_OF_INPUT) {
            bytesTransferred(bytesRead)
        }
        return bytesRead
    }

    override fun getUri(): Uri? {
        return openedDataSpec?.uri // Return the original NCM URI so CacheDataSource stays consistent
    }

    override fun close() {
        transferEnded()
        try {
            activeDataSource?.close()
        } finally {
            activeDataSource = null
            openedDataSpec = null
        }
    }

    class Factory(
        private val context: Context,
        private val httpDataSourceFactory: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            // We don't pass the TransferListeners here because NcmDataSource notifies them itself
            return NcmDataSource(context, httpDataSourceFactory.createDataSource())
        }
    }
}
