package com.ncm.player.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.ncm.player.BuildConfig

object DebugLog {
    private const val TAG = "NCMPlayerDebug"

    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, message, throwable)
        }
    }

    fun i(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, message)
        }
    }

    fun toast(context: Context, message: String) {
        if (BuildConfig.DEBUG) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
