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
import com.ncm.player.util.UserPreferences
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class NcmDataSource(
    private val context: Context,
    private val httpDataSource: DataSource
) : BaseDataSource(true) {

    private val fileDataSource = FileDataSource()
    private val contentDataSource = ContentDataSource(context)
    private var activeDataSource: DataSource? = null

    companion object {
        private val urlCache = ConcurrentHashMap<String, String>()
        private val cacheExpiry = ConcurrentHashMap<String, Long>()
        private const val CACHE_DURATION = 15 * 60 * 1000L // 15 minutes
    }

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        try {
            activeDataSource?.close()
            val uri = dataSpec.uri
            DebugLog.d("NcmDS: opening URI: $uri at position ${dataSpec.position}")

            // 1. Handle non-ncm schemes immediately
            if (uri.scheme != "ncm") {
                val ds = when (uri.scheme) {
                    "content" -> contentDataSource
                    "http", "https" -> httpDataSource
                    else -> fileDataSource
                }
                activeDataSource = ds
                val length = ds.open(dataSpec)
                transferStarted(dataSpec)
                return length
            }

            // 2. Extract Song ID and Quality
            val songId = if (uri.host == "song") uri.pathSegments.firstOrNull() else uri.host ?: uri.authority
            if (songId == null) throw IOException("Invalid NCM URI: $uri")

            val quality = uri.getQueryParameter("quality") ?: "standard"
            val cookie = uri.getQueryParameter("cookie")

            // 3. Check Local Registry (Downloads)
            val metadata = DownloadRegistry.getMetadata(songId)
            if (metadata != null) {
                val localUri = Uri.parse(metadata.filePath)
                DebugLog.d("NcmDS: Found in local registry: $localUri")
                val localDataSpec = dataSpec.buildUpon()
                    .setUri(localUri)
                    .setKey(songId)
                    .build()
                activeDataSource = if (localUri.scheme == "content") contentDataSource else fileDataSource
                val length = activeDataSource!!.open(localDataSpec)
                transferStarted(dataSpec)
                return length
            }

            // 4. Resolve URL with Retries and URL Caching
            var lastException: Exception? = null
            val cacheKey = "${songId}_$quality"

            for (attempt in 1..3) {
                try {
                    // Check cache first in each attempt (in case it was updated by another thread)
                    var cdnUrl = urlCache[cacheKey]?.takeIf { (cacheExpiry[cacheKey] ?: 0L) > System.currentTimeMillis() }

                    if (cdnUrl == null) {
                        val params = mutableMapOf("id" to songId, "level" to quality)
                        if (!cookie.isNullOrEmpty()) params["cookie"] = cookie

                        val method = if (attempt == 1) "song/url/v1/302" else "song/url/v1"
                        val result = RustServerManager.callApi(method, params)
                        val body = JsonParser.parseString(result).asJsonObject

                        cdnUrl = body.get("redirectUrl")?.asString ?: JsonUtils.findUrl(body)

                        if (!cdnUrl.isNullOrEmpty() && cdnUrl.startsWith("http")) {
                            urlCache[cacheKey] = cdnUrl
                            cacheExpiry[cacheKey] = System.currentTimeMillis() + CACHE_DURATION
                        }
                    }

                    if (cdnUrl != null) {
                        DebugLog.d("NcmDS: Resolved to $cdnUrl (attempt $attempt)")
                        val resolvedDataSpec = dataSpec.buildUpon()
                            .setUri(Uri.parse(cdnUrl))
                            .setKey(songId)
                            .build()

                        activeDataSource = httpDataSource
                        val length = httpDataSource.open(resolvedDataSpec)
                        transferStarted(dataSpec)
                        return length
                    } else {
                        throw IOException("API failed to provide a URL for song $songId")
                    }
                } catch (e: Exception) {
                    DebugLog.e("NcmDS: Open attempt $attempt failed", e)
                    lastException = e
                    urlCache.remove(cacheKey) // Clear cache on failure to force re-resolve next time
                    if (attempt < 3) Thread.sleep(200L * attempt)
                }
            }

            throw lastException ?: IOException("Unknown error during resolution")
        } catch (e: Exception) {
            DebugLog.e("NcmDS: Final open failure for ${dataSpec.uri}", e)
            throw if (e is IOException) e else IOException(e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        try {
            val read = activeDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
            if (read > 0) {
                bytesTransferred(read)
            }
            return read
        } catch (e: IOException) {
            throw e
        }
    }

    override fun getUri(): Uri? = activeDataSource?.getUri()
    override fun getResponseHeaders(): Map<String, List<String>> = activeDataSource?.getResponseHeaders() ?: emptyMap()

    override fun close() {
        try {
            activeDataSource?.close()
        } catch (e: IOException) {
            DebugLog.e("NcmDS: Error closing", e)
        } finally {
            activeDataSource = null
            transferEnded()
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
