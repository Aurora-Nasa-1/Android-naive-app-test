package com.ncm.player.model

data class UserProfile(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val signature: String? = null,
    val vipType: Int = 0
)
