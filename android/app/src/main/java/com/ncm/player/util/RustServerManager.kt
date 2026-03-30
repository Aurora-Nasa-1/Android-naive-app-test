package com.ncm.player.util

import android.content.Context
import android.util.Log

object RustServerManager {
    private const val TAG = "RustServer"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("ncm_api")
            isNativeLoaded = true
            Log.i(TAG, "Native JNI library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Fatal: Native library ncm_api not found")
        }
    }

    private external fun startNativeServer(host: String, port: Int)

    fun startServer(context: Context, port: Int = 3000) {
        if (isNativeLoaded) {
            Log.d(TAG, "Starting server via JNI on port $port")
            try {
                startNativeServer("127.0.0.1", port)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start native server", e)
            }
        } else {
            Log.e(TAG, "Cannot start server: JNI library not loaded")
        }
    }

    fun stopServer() {
        // JNI server typically runs until process exits or has internal shutdown logic
        Log.d(TAG, "Stop request ignored (JNI mode)")
    }
}
