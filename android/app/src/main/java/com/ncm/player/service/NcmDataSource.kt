package com.ncm.player.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.TransferListener
import com.ncm.player.manager.DownloadRegistry
import com.ncm.player.util.RustServerManager
import com.ncm.player.util.JsonUtils
import com.ncm.player.util.DebugLog
import com.google.gson.JsonParser
import java.io.IOException

class NcmDataSource(
    private val context: Context,
    private val httpDataSource: DataSource
) : DataSource {

    private val fileDataSource = FileDataSource()
    private val contentDataSource = ContentDataSource(context)
    private var activeDataSource: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        httpDataSource.addTransferListener(transferListener)
        fileDataSource.addTransferListener(transferListener)
        contentDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        DebugLog.d("NcmDS: Opening URI: $uri")

        if (uri.scheme != "ncm") {
            val ds = when (uri.scheme) {
                "content" -> contentDataSource
                else -> fileDataSource
            }
            activeDataSource = ds
            return ds.open(dataSpec)
        }

        val songId = uri.host ?: throw IOException("Invalid song ID in URI: $uri")
        val quality = uri.getQueryParameter("quality") ?: "standard"
        val cookie = uri.getQueryParameter("cookie")

        // 1. Check local registry
        val metadata = DownloadRegistry.getMetadata(songId)
        if (metadata != null) {
            DebugLog.d("NcmDS: Found local file for $songId: ${metadata.filePath}")
            val localUri = Uri.parse(metadata.filePath)
            val localDataSpec = dataSpec.withUri(localUri).buildUpon()
                .setKey(songId)
                .build()

            val ds = when (localUri.scheme) {
                "content" -> contentDataSource
                else -> fileDataSource
            }

            activeDataSource = ds
            return ds.open(localDataSpec)
        }

        // 2. Resolve via JNI
        val params = mutableMapOf("id" to songId, "level" to quality)
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
                val message = body.get("message")?.asString ?: body.get("msg")?.asString ?: "No URL in response"
                lastError = "Code $code: $message"

                if (code == 404 || code == 403) break
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
            }
            if (attempt < 3) Thread.sleep(200L * attempt)
        }

        if (cdnUrl == null || !cdnUrl.startsWith("http")) {
            DebugLog.e("NcmDS: Failed to resolve $songId. Last error: $lastError")
            throw IOException("Failed to resolve NCM URL for ID $songId: $lastError")
        }

        DebugLog.d("NcmDS: Resolved URL for $songId: $cdnUrl")
        val resolvedUri = Uri.parse(cdnUrl)
        val resolvedDataSpec = dataSpec.withUri(resolvedUri).buildUpon()
            .setKey(songId)
            .build()

        activeDataSource = httpDataSource
        return httpDataSource.open(resolvedDataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return activeDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? {
        return activeDataSource?.uri
    }

    override fun close() {
        try {
            activeDataSource?.close()
        } finally {
            activeDataSource = null
        }
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
