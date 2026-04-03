package com.ncm.player.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ncm.player.model.Song
import com.ncm.player.model.Contact
import com.ncm.player.model.Message

object JsonUtils {
    fun parseSong(it: JsonElement): Song? {
        return try {
            val item = it.asJsonObject
            val obj = if (item.has("songInfo")) item.get("songInfo").asJsonObject else item

            val artists = obj.get("ar")?.asJsonArray ?: obj.get("artists")?.asJsonArray
            val artistObj = artists?.get(0)?.asJsonObject
            val artistName = artistObj?.get("name")?.asString ?: "Unknown"
            val artistId = artistObj?.get("id")?.asString
            val album = obj.get("al")?.asJsonObject ?: obj.get("album")?.asJsonObject
            val albumName = album?.get("name")?.asString ?: "Unknown"
            val picUrl = album?.get("picUrl")?.asString

            Song(
                id = obj.get("id").asJsonPrimitive.asString,
                name = obj.get("name").asString,
                artist = artistName,
                artistId = artistId,
                album = albumName,
                albumArtUrl = picUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseContact(it: JsonElement): Contact? {
        return try {
            val obj = it.asJsonObject
            val fromUser = obj.get("fromUser").asJsonObject
            val lastMsgStr = obj.get("lastMsg")?.asString ?: "{}"
            val lastMsg = try { JsonParser.parseString(lastMsgStr).asJsonObject } catch (e: Exception) { JsonObject() }

            Contact(
                userId = fromUser.get("userId").asLong,
                nickname = fromUser.get("nickname").asString,
                avatarUrl = fromUser.get("avatarUrl").asString,
                lastMessage = lastMsg.get("msg")?.asString ?: "",
                lastMessageTime = obj.get("lastMsgTime")?.asLong ?: 0L,
                unreadCount = obj.get("newMsgCount")?.asInt ?: 0
            )
        } catch (e: Exception) {
            android.util.Log.e("JsonUtils", "parseContact error: ${e.message}")
            null
        }
    }

    fun parseMessage(it: JsonElement, myUserId: Long): Message? {
        return try {
            val obj = it.asJsonObject
            val fromUser = obj.get("fromUser").asJsonObject
            val msgStr = obj.get("msg")?.asString ?: "{}"
            val msgContent = try { JsonParser.parseString(msgStr).asJsonObject } catch (e: Exception) { JsonObject() }
            val userId = fromUser.get("userId").asLong

            Message(
                id = obj.get("id").asLong,
                fromUserId = userId,
                fromNickname = fromUser.get("nickname").asString,
                fromAvatarUrl = fromUser.get("avatarUrl").asString,
                text = msgContent.get("msg")?.asString ?: "",
                time = obj.get("time").asLong,
                isMe = userId == myUserId
            )
        } catch (e: Exception) {
            android.util.Log.e("JsonUtils", "parseMessage error: ${e.message}")
            null
        }
    }

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
