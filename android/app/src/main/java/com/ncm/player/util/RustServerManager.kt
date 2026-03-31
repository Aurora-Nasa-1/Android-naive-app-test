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

    fun callApi(method: String, params: Map<String, String>): String {
        if (!isNativeLoaded) return "{\"code\": 500, \"msg\": \"Native library not loaded\"}"
        val json = com.google.gson.Gson().toJson(params)
        return nativeCallApi(method, json)
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
