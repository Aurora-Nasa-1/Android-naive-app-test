package com.ncm.player.model

data class Event(
    val id: Long,
    val userId: Long,
    val nickname: String,
    val avatarUrl: String,
    val eventTime: Long,
    val type: Int,
    val json: String,
    val msg: String,
    val pics: List<String> = emptyList(),
    val song: Song? = null,
    val playlist: Playlist? = null
)
