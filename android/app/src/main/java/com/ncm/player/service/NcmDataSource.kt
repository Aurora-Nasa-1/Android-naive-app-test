package com.ncm.player.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
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
) : BaseDataSource(true) {

    private val fileDataSource = FileDataSource()
    private val contentDataSource = ContentDataSource(context)
    private var activeDataSource: DataSource? = null

    init {
        // Internal data sources should not trigger transfer listener twice,
        // we manually trigger them in our read/open methods for the aggregate source.
    }

    override fun open(dataSpec: DataSpec): Long {
        activeDataSource?.close()
        if (dataSpec.position == 0L) transferInitializing(dataSpec)
        val uri = dataSpec.uri
        DebugLog.d("NcmDS: opening ${uri}")

        try {
            if (uri.scheme != "ncm") {
                val ds = if (uri.scheme == "content") contentDataSource else fileDataSource
                activeDataSource = ds
                val length = ds.open(dataSpec)
                if (dataSpec.position == 0L) transferStarted(dataSpec)
                return length
            }

            val songId = uri.host ?: throw IOException("Invalid song ID")
            val quality = uri.getQueryParameter("quality") ?: "standard"
            val cookie = uri.getQueryParameter("cookie")

            val metadata = DownloadRegistry.getMetadata(songId)
            if (metadata != null) {
                val localUri = Uri.parse(metadata.filePath)
                val localDataSpec = dataSpec.buildUpon().setUri(localUri).setKey(songId).build()
                activeDataSource = if (localUri.scheme == "content") contentDataSource else fileDataSource
                val length = activeDataSource!!.open(localDataSpec)
                if (dataSpec.position == 0L) transferStarted(dataSpec)
                return length
            }

            val params = mutableMapOf("id" to songId, "level" to quality)
            cookie?.let { params["cookie"] = it }

            var cdnUrl: String? = null
            for (attempt in 1..3) {
                try {
                    val method = if (attempt == 1) "song/url/v1/302" else "song/url/v1"
                    val result = RustServerManager.callApi(method, params)
                    val body = JsonParser.parseString(result).asJsonObject
                    cdnUrl = body.get("redirectUrl")?.asString ?: JsonUtils.findUrl(body)
                    if (cdnUrl != null && cdnUrl.startsWith("http")) break
                } catch (e: Exception) { }
                if (attempt < 3) Thread.sleep(200L)
            }

            if (cdnUrl == null) throw IOException("Failed to resolve ${songId}")

            val resolvedDataSpec = dataSpec.buildUpon().setUri(Uri.parse(cdnUrl)).setKey(songId).build()
            activeDataSource = httpDataSource
            val length = httpDataSource.open(resolvedDataSpec)
            if (dataSpec.position == 0L) transferStarted(dataSpec)
            return length
        } catch (e: Exception) {
            DebugLog.e("NcmDS: open failed", e)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val read = activeDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        if (read > 0) {
            bytesTransferred(read)
        }
        return read
    }

    override fun getUri(): Uri? = activeDataSource?.getUri()
    override fun getResponseHeaders(): Map<String, List<String>> = activeDataSource?.getResponseHeaders() ?: emptyMap()

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
