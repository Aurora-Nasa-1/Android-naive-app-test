package com.ncm.player.model

data class Playlist(
    val id: Long,
    val name: String,
    val coverImgUrl: String? = null,
    val trackCount: Int = 0
)
