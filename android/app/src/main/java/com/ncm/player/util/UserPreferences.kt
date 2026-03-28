package com.ncm.player.util

import android.content.Context
import android.content.SharedPreferences

object UserPreferences {
    private const val PREFS_NAME = "ncm_player_prefs"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_QUALITY = "quality"
    private const val KEY_FADE_DURATION = "fade_duration"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveCookie(context: Context, cookie: String) {
        getPrefs(context).edit().putString(KEY_COOKIE, cookie).apply()
    }

    fun getCookie(context: Context): String? {
        return getPrefs(context).getString(KEY_COOKIE, null)
    }

    fun saveQuality(context: Context, quality: String) {
        getPrefs(context).edit().putString(KEY_QUALITY, quality).apply()
    }

    fun getQuality(context: Context): String {
        return getPrefs(context).getString(KEY_QUALITY, "standard") ?: "standard"
    }

    fun saveFadeDuration(context: Context, duration: Float) {
        getPrefs(context).edit().putFloat(KEY_FADE_DURATION, duration).apply()
    }

    fun getFadeDuration(context: Context): Float {
        return getPrefs(context).getFloat(KEY_FADE_DURATION, 2f)
    }
}
