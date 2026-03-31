package com.ncm.player.util

import android.content.Context
import android.content.SharedPreferences

object UserPreferences {
    private const val PREFS_NAME = "ncm_player_prefs"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_QUALITY_WIFI = "quality_wifi"
    private const val KEY_QUALITY_CELLULAR = "quality_cellular"
    private const val KEY_FADE_DURATION = "fade_duration"
    private const val KEY_CACHE_SIZE = "cache_size"
    private const val KEY_USE_CELLULAR_CACHE = "use_cellular_cache"
    private const val KEY_DOWNLOAD_DIR = "download_dir"
    private const val KEY_DOWNLOAD_QUALITY = "download_quality"
    private const val KEY_FIRST_DOWNLOAD = "first_download"
    private const val KEY_ALLOW_CELLULAR_DOWNLOAD = "allow_cellular_download"
    private const val KEY_PURE_BLACK_MODE = "pure_black_mode"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveCookie(context: Context, cookie: String) {
        getPrefs(context).edit().putString(KEY_COOKIE, cookie).apply()
    }

    fun getCookie(context: Context): String? {
        return getPrefs(context).getString(KEY_COOKIE, null)
    }

    fun saveQualityWifi(context: Context, quality: String) {
        getPrefs(context).edit().putString(KEY_QUALITY_WIFI, quality).apply()
    }

    fun getQualityWifi(context: Context): String {
        return getPrefs(context).getString(KEY_QUALITY_WIFI, "exhigh") ?: "exhigh"
    }

    fun saveQualityCellular(context: Context, quality: String) {
        getPrefs(context).edit().putString(KEY_QUALITY_CELLULAR, quality).apply()
    }

    fun getQualityCellular(context: Context): String {
        return getPrefs(context).getString(KEY_QUALITY_CELLULAR, "standard") ?: "standard"
    }

    fun saveFadeDuration(context: Context, duration: Float) {
        getPrefs(context).edit().putFloat(KEY_FADE_DURATION, duration).apply()
    }

    fun getFadeDuration(context: Context): Float {
        return getPrefs(context).getFloat(KEY_FADE_DURATION, 2f)
    }

    fun saveCacheSize(context: Context, sizeMb: Int) {
        getPrefs(context).edit().putInt(KEY_CACHE_SIZE, sizeMb).apply()
    }

    fun getCacheSize(context: Context): Int {
        return getPrefs(context).getInt(KEY_CACHE_SIZE, 512) // Default 512MB
    }

    fun saveUseCellularCache(context: Context, use: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_CELLULAR_CACHE, use).apply()
    }

    fun getUseCellularCache(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_CELLULAR_CACHE, false)
    }

    fun saveDownloadDir(context: Context, uri: String) {
        getPrefs(context).edit().putString(KEY_DOWNLOAD_DIR, uri).apply()
    }

    fun getDownloadDir(context: Context): String? {
        return getPrefs(context).getString(KEY_DOWNLOAD_DIR, null)
    }

    fun saveDownloadQuality(context: Context, quality: String) {
        getPrefs(context).edit().putString(KEY_DOWNLOAD_QUALITY, quality).apply()
    }

    fun getDownloadQuality(context: Context): String {
        return getPrefs(context).getString(KEY_DOWNLOAD_QUALITY, "standard") ?: "standard"
    }

    fun isFirstDownload(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FIRST_DOWNLOAD, true)
    }

    fun setFirstDownloadComplete(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_DOWNLOAD, false).apply()
    }

    fun saveAllowCellularDownload(context: Context, allow: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ALLOW_CELLULAR_DOWNLOAD, allow).apply()
    }

    fun getAllowCellularDownload(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ALLOW_CELLULAR_DOWNLOAD, false)
    }

    fun savePureBlackMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PURE_BLACK_MODE, enabled).apply()
    }

    fun getPureBlackMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PURE_BLACK_MODE, false)
    }

    fun savePlaylistSort(context: Context, playlistId: Long, sortOrder: String) {
        getPrefs(context).edit().putString("sort_playlist_$playlistId", sortOrder).apply()
    }

    fun getPlaylistSort(context: Context, playlistId: Long): String {
        return getPrefs(context).getString("sort_playlist_$playlistId", "default") ?: "default"
    }
}
