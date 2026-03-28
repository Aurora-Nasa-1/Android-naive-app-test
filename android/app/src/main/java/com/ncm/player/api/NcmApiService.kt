package com.ncm.player.api

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface NcmApiService {
    @GET("login/qr/key")
    suspend fun getQrKey(@Query("timestamp") timestamp: Long = System.currentTimeMillis()): Response<JsonObject>

    @GET("login/qr/create")
    suspend fun createQr(@Query("key") key: String, @Query("qrimg") qrimg: Boolean = true, @Query("timestamp") timestamp: Long = System.currentTimeMillis()): Response<JsonObject>

    @GET("login/qr/check")
    suspend fun checkQr(@Query("key") key: String, @Query("timestamp") timestamp: Long = System.currentTimeMillis()): Response<JsonObject>

    @GET("login/status")
    suspend fun loginStatus(@Query("timestamp") timestamp: Long = System.currentTimeMillis()): Response<JsonObject>

    @GET("recommend/songs")
    suspend fun getRecommendSongs(@Query("cookie") cookie: String? = null): Response<JsonObject>

    @GET("user/playlist")
    suspend fun getUserPlaylist(@Query("uid") uid: Long, @Query("cookie") cookie: String? = null): Response<JsonObject>

    @GET("likelist")
    suspend fun getLikeList(@Query("uid") uid: Long, @Query("cookie") cookie: String? = null): Response<JsonObject>

    @GET("song/url/v1")
    suspend fun getSongUrl(@Query("id") id: String, @Query("level") level: String = "standard"): Response<JsonObject>
}
