package com.ncm.player.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

object JsonUtils {
    fun getString(element: JsonElement?, key: String, default: String? = null): String? {
        val obj = if (element != null && element.isJsonObject) element.asJsonObject else return default
        val field = obj.get(key)
        return if (field != null && !field.isJsonNull && field.isJsonPrimitive) {
            field.asString
        } else {
            default
        }
    }

    fun getArray(element: JsonElement?, key: String): JsonArray? {
        val obj = if (element != null && element.isJsonObject) element.asJsonObject else return null
        val field = obj.get(key)
        return if (field != null && field.isJsonArray) {
            field.asJsonArray
        } else {
            null
        }
    }

    fun getObject(element: JsonElement?, key: String): JsonObject? {
        val obj = if (element != null && element.isJsonObject) element.asJsonObject else return null
        val field = obj.get(key)
        return if (field != null && field.isJsonObject) {
            field.asJsonObject
        } else {
            null
        }
    }

    fun findUrl(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null

        // If it's a primitive string, it might be the URL directly (unlikely but possible)
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            val s = element.asString
            if (s.startsWith("http") && s.length > 12 && !s.contains("null")) {
                android.util.Log.d("JsonUtils", "Found URL in primitive: $s")
                return s
            }
        }

        // If it's the data object itself
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val url = getString(obj, "url")
            if (url != null && url.isNotBlank() && url.startsWith("http") && url.length > 12 && !url.contains("null")) {
                android.util.Log.d("JsonUtils", "Found URL in object: $url")
                return url
            }

            // Search all keys recursively if not found immediately
            for (entry in obj.entrySet()) {
                if (entry.key == "data" || entry.key == "result" || entry.key == "songs") {
                    val found = findUrl(entry.value)
                    if (found != null) return found
                }
            }

            // Exhaustive search as last resort
            for (entry in obj.entrySet()) {
                if (entry.key != "data" && entry.key != "result" && entry.key != "songs") {
                    val found = findUrl(entry.value)
                    if (found != null) return found
                }
            }
        }

        // If it's an array
        if (element.isJsonArray) {
            val arr = element.asJsonArray
            for (i in 0 until arr.size()) {
                val found = findUrl(arr.get(i))
                if (found != null) return found
            }
        }

        return null
    }
}
