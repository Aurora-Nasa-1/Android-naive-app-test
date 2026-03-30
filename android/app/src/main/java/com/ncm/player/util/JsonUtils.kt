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

        // If it's the data object itself
        if (element.isJsonObject) {
            val url = getString(element, "url")
            if (url != null) return url

            // Try nested data
            val nestedData = getObject(element, "data")
            if (nestedData != null) return findUrl(nestedData)

            val nestedArray = getArray(element, "data")
            if (nestedArray != null) return findUrl(nestedArray)
        }

        // If it's an array (like in song/url responses)
        if (element.isJsonArray) {
            val arr = element.asJsonArray
            if (arr.size() > 0) {
                return findUrl(arr.get(0))
            }
        }

        return null
    }
}
