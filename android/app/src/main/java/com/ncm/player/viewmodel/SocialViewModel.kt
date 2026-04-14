package com.ncm.player.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.viewModelScope
import com.ncm.player.model.*
import com.ncm.player.util.JsonUtils
import kotlinx.coroutines.*

class SocialViewModel(application: Application) : BaseViewModel(application) {
    var hotComments by mutableStateOf<List<Comment>>(emptyList())
    var newestComments by mutableStateOf<List<Comment>>(emptyList())
    var commentTotal by mutableIntStateOf(0)
    var hasMoreComments by mutableStateOf(true)
    var currentCommentPage by mutableIntStateOf(0)

    var contacts by mutableStateOf<List<Contact>>(emptyList())
    var chatMessages by mutableStateOf<List<Message>>(emptyList())
    var unreadCount by mutableIntStateOf(0)

    fun fetchComments(id: String, type: String = "music", page: Int = 0) {
        viewModelScope.launch {
            isLoading = true
            try {
                val method = "comment/$type"
                val body = withContext(Dispatchers.IO) {
                    callApi(method, mapOf("id" to id, "limit" to "20", "offset" to (page * 20).toString()))
                }
                val hot = body.get("hotComments")?.asJsonArray?.mapNotNull { JsonUtils.parseComment(it) } ?: emptyList()
                val new = body.get("comments")?.asJsonArray?.mapNotNull { JsonUtils.parseComment(it) } ?: emptyList()

                if (page == 0) hotComments = hot
                newestComments = if (page == 0) new else newestComments + new
                commentTotal = body.get("total")?.asInt ?: 0
                hasMoreComments = body.get("more")?.asBoolean ?: false
                currentCommentPage = page
            } finally { isLoading = false }
        }
    }

    fun fetchUnreadCount() {
        if (cookie == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val body = callApi("pl/count")
                val count = body.get("msg")?.asInt ?: body.get("data")?.asJsonObject?.get("msg")?.asInt ?: 0
                withContext(Dispatchers.Main) { unreadCount = count }
            } catch (e: Exception) { }
        }
    }

    fun fetchContacts() {
        if (cookie == null) return
        viewModelScope.launch {
            isLoading = true
            try {
                val body = withContext(Dispatchers.IO) { callApi("msg/recentcontact") }
                contacts = body.get("recentcontacts")?.asJsonArray?.mapNotNull { JsonUtils.parseContact(it) } ?: emptyList()
            } finally { isLoading = false }
        }
    }

    fun fetchMessages(uid: Long) {
        if (cookie == null) return
        viewModelScope.launch {
            isLoading = true
            try {
                val body = withContext(Dispatchers.IO) { callApi("msg/private/history", mapOf("uid" to uid.toString())) }
                chatMessages = body.get("msgs")?.asJsonArray?.mapNotNull { JsonUtils.parseMessage(it, 0L) }?.reversed() ?: emptyList()
            } finally { isLoading = false }
        }
    }

    fun toggleCommentLike(id: String, cid: Long, type: String, liked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = when (type) { "music" -> "0"; "mv" -> "1"; "playlist" -> "2"; "album" -> "3"; "dj" -> "4"; "video" -> "5"; "event" -> "6"; else -> "0" }
            callApi("comment/like", mapOf("id" to id, "cid" to cid.toString(), "t" to t, "type" to if (liked) "1" else "0"))
            withContext(Dispatchers.Main) {
                val update: (Comment) -> Comment = { if (it.id == cid) it.copy(liked = liked, likedCount = it.likedCount + if (liked) 1 else -1) else it }
                hotComments = hotComments.map(update)
                newestComments = newestComments.map(update)
            }
        }
    }

    fun postComment(id: String, type: String, content: String, replyId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = when (type) { "music" -> "0"; "mv" -> "1"; "playlist" -> "2"; "album" -> "3"; "dj" -> "4"; "video" -> "5"; "event" -> "6"; else -> "0" }
            val params = mutableMapOf("id" to id, "type" to t, "content" to content, "op" to if (replyId != null) "reply" else "add")
            if (replyId != null) params["commentId"] = replyId.toString()
            val body = callApi("comment", params)
            if (body.get("code")?.asInt == 200) fetchComments(id, type, 0)
        }
    }

    fun markMessageAsRead(uid: Long) {
        if (cookie == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                callApi("msg/private/mark/read", mapOf("uid" to uid.toString()))
            } catch (e: Exception) { }
        }
    }

    fun sendMessage(uid: Long, text: String) {
        if (cookie == null || text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val body = callApi("send/text", mapOf("user_ids" to uid.toString(), "msg" to text))
            if (body.get("code")?.asInt == 200) {
                fetchMessages(uid)
            }
        }
    }
}
