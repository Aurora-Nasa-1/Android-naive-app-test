package com.ncm.player.util

object ImageUtils {
    fun getThumbnailUrl(url: String?, size: Int = 200): String? {
        if (url == null) return null
        return if (url.contains("?")) {
            "$url&param=${size}y$size"
        } else {
            "$url?param=${size}y$size"
        }
    }
}
