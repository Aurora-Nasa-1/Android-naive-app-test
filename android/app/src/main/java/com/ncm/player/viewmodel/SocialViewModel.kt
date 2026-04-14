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
    var commentSortType by mutableIntStateOf(1) // 1: Recommend, 2: Hot, 3: Time
    var commentCursor by mutableStateOf("")

    var floorComments by mutableStateOf<List<Comment>>(emptyList())
    var floorCommentTotal by mutableIntStateOf(0)
    var floorHasMore by mutableStateOf(false)
    var floorCursor by mutableStateOf(0L)
    var activeParentComment by mutableStateOf<Comment?>(null)

    var contacts by mutableStateOf<List<Contact>>(emptyList())
    var chatMessages by mutableStateOf<List<Message>>(emptyList())
    var unreadCount by mutableIntStateOf(0)

    fun fetchComments(id: String, type: String = "music", sortType: Int = 1, page: Int = 1) {
        viewModelScope.launch {
            isLoading = true
            try {
                val t = when (type) { "music" -> "0"; "mv" -> "1"; "playlist" -> "2"; "album" -> "3"; "dj" -> "4"; "video" -> "5"; "event" -> "6"; else -> "0" }
                val params = mutableMapOf(
                    "id" to id,
                    "type" to t,
                    "sortType" to sortType.toString(),
                    "pageNo" to page.toString(),
                    "pageSize" to "20"
                )
                if (sortType == 3 && page > 1 && commentCursor.isNotEmpty()) {
                    params["cursor"] = commentCursor
                }

                val body = withContext(Dispatchers.IO) { callApi("comment/new", params) }
                val data = body.get("data")?.asJsonObject
                val commentsArr = data?.get("comments")?.asJsonArray
                val newComments = commentsArr?.mapNotNull { JsonUtils.parseComment(it) } ?: emptyList()

                if (page == 1) {
                    newestComments = newComments
                    // Also try to get hot comments if on first page of recommend/new
                    if (sortType != 2) {
                        val hotArr = data?.get("hotComments")?.asJsonArray
                        hotComments = hotArr?.mapNotNull { JsonUtils.parseComment(it) } ?: emptyList()
                    } else {
                        hotComments = emptyList()
                    }
                } else {
                    newestComments = newestComments + newComments
                }

                commentTotal = data?.get("totalCount")?.asInt ?: 0
                hasMoreComments = data?.get("hasMore")?.asBoolean ?: false
                commentCursor = data?.get("cursor")?.asString ?: ""
                currentCommentPage = page
                commentSortType = sortType
            } finally { isLoading = false }
        }
    }

    fun fetchFloorComments(id: String, parentCommentId: Long, type: String = "music", time: Long = 0L) {
        viewModelScope.launch {
            isLoading = true
            try {
                val t = when (type) { "music" -> "0"; "mv" -> "1"; "playlist" -> "2"; "album" -> "3"; "dj" -> "4"; "video" -> "5"; "event" -> "6"; else -> "0" }
                val params = mutableMapOf("id" to id, "parentCommentId" to parentCommentId.toString(), "type" to t, "limit" to "20")
                if (time > 0) params["time"] = time.toString()

                val body = withContext(Dispatchers.IO) { callApi("comment/floor", params) }
                val data = body.get("data")?.asJsonObject
                val commentsArr = data?.get("comments")?.asJsonArray
                val newComments = commentsArr?.mapNotNull { JsonUtils.parseComment(it) } ?: emptyList()

                floorComments = if (time == 0L) newComments else floorComments + newComments
                floorCommentTotal = data?.get("totalCount")?.asInt ?: 0
                floorHasMore = data?.get("hasMore")?.asBoolean ?: false
                floorCursor = data?.get("time")?.asLong ?: 0L
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
                val recent = body.get("recentcontacts")?.asJsonArray?.mapNotNull { JsonUtils.parseContact(it) }
                val data = body.get("data")?.asJsonObject?.get("recentcontacts")?.asJsonArray?.mapNotNull { JsonUtils.parseContact(it) }
                contacts = recent ?: data ?: emptyList()
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
