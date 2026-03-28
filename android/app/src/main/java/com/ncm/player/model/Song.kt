package com.ncm.player.model

data class Song(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val albumArtUrl: String? = null
)
