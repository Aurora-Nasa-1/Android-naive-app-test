package com.ncm.player

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

class NCMApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        com.ncm.player.util.LogManager.init(this)
        com.ncm.player.util.DebugLog.i("Application onCreate")
        com.ncm.player.manager.DownloadRegistry.init(this)
        // Start native server via JNI
        com.ncm.player.util.RustServerManager.startServer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
