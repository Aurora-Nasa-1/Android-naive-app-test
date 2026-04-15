package com.ncm.player.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ncm.player.util.RustServerManager
import com.ncm.player.util.UserPreferences

open class BaseViewModel(application: Application) : AndroidViewModel(application) {
    var isLoading by mutableStateOf(false)
    var cookie: String? = deduplicateCookie(UserPreferences.getCookie(getApplication()))

    protected fun callApi(method: String, params: Map<String, String> = emptyMap()): JsonObject {
        val finalParams = if (!cookie.isNullOrEmpty() && !params.containsKey("cookie")) {
            params + ("cookie" to cookie!!)
        } else {
            params
        }
        val result = RustServerManager.callApi(method, finalParams)
        return try {
            JsonParser.parseString(result).asJsonObject
        } catch (e: Exception) {
            JsonObject().apply { addProperty("code", 500); addProperty("msg", "Parse error") }
        }
    }

    private fun deduplicateCookie(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        val items = cookie.split(";").map { it.trim() }.filter { it.contains("=") }
        val cookieMap = mutableMapOf<String, String>()
        items.forEach { item ->
            val parts = item.split("=", limit = 2)
            if (parts.size == 2) {
                cookieMap[parts[0]] = parts[1]
            }
        }
        return cookieMap.map { "${it.key}=${it.value}" }.joinToString("; ")
    }
}
