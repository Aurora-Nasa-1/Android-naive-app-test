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
    val cookie: String? get() = UserPreferences.getCookie(getApplication())

    protected fun callApi(method: String, params: Map<String, String> = emptyMap()): JsonObject {
        val finalParams = if (cookie != null && !params.containsKey("cookie")) {
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
}
