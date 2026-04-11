package com.ncm.player.util

import android.content.Context
import android.util.Log

object RustServerManager {
    private var process: Process? = null
    private const val TAG = "RustServer"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("ncm_api")
            isNativeLoaded = true
            Log.i(TAG, "Native JNI library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library ncm_api not found, JNI will not be available")
        }
    }

    @JvmStatic
    private external fun startNativeServer(host: String, port: Int)

    @JvmStatic
    private external fun nativeCallApi(method: String, paramsJson: String): String

    @JvmStatic
    private external fun analyzeAudioFile(path: String): String

    fun analyzeAudio(path: String): String {
        if (!isNativeLoaded) {
            DebugLog.e("JNI: native library not loaded")
            return "{\"code\": 500, \"msg\": \"Native library not loaded\"}"
        }
        return try {
            val result = analyzeAudioFile(path)
            DebugLog.d("JNI: analyzeAudio result length: ${result.length}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "JNI analyzeAudio failed", e)
            "{\"code\": 500, \"msg\": \"${e.message}\"}"
        }
    }

    fun callApi(method: String, params: Map<String, String>): String {
        if (!isNativeLoaded) {
            DebugLog.e("JNI: native library not loaded")
            return "{\"code\": 500, \"msg\": \"Native library not loaded\"}"
        }
        val json = com.google.gson.Gson().toJson(params)
        DebugLog.d("JNI: calling $method with $json")
        val result = nativeCallApi(method, json)
        // Log a summary or head of result to avoid flooding but still provide info
        val logResult = if (result.length > 200) result.take(200) + "..." else result
        DebugLog.d("JNI: $method result: $logResult")
        return result
    }

    fun startServer(context: android.content.Context, port: Int = 3000) {
        if (isNativeLoaded) {
            Log.d(TAG, "Starting server via JNI on port $port")
            try {
                startNativeServer("127.0.0.1", port)
                return
            } catch (e: Exception) {
                Log.e(TAG, "JNI start failed", e)
            }
        }

        Log.e(TAG, "JNI is not loaded, server cannot be started")
    }

    fun stopServer() {
        process?.destroy()
        process = null
    }
}
