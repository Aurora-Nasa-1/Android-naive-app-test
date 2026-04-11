package com.ncm.player.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.ncm.player.BuildConfig

object DebugLog {
    private const val TAG = "NCMPlayerDebug"

    fun d(message: String) {
  feature/song-comments-and-dynamics-13655779103225213096
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
            LogManager.log("D", message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        LogManager.log("E", message, throwable)
    }

    fun i(message: String) {
feature/song-comments-and-dynamics-13655779103225213096
        if (BuildConfig.DEBUG) {
            Log.i(TAG, message)
            LogManager.log("I", message)
        }
    }

    fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
