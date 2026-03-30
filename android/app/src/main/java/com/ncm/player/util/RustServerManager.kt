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
            Log.w(TAG, "Native library ncm_api not found, will fallback to binary")
        }
    }

    @JvmStatic
    private external fun startNativeServer(host: String, port: Int)

    fun extractServer(context: android.content.Context): java.io.File? {
        val abi = android.os.Build.SUPPORTED_ABIS[0]
        val assetName = when {
            abi.contains("arm64") || abi.contains("aarch64") -> "ncm-server-aarch64"
            else -> return null
        }

        val outFile = java.io.File(context.filesDir, "ncm-server")
        if (outFile.exists() && outFile.canExecute()) return outFile

        try {
            context.assets.open("bin/$assetName").use { input ->
                java.io.FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile.setExecutable(true, true)
            return outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract binary", e)
            return null
        }
    }

    fun startServer(context: android.content.Context, port: Int = 3000) {
        if (isNativeLoaded) {
            Log.d(TAG, "Starting server via JNI on port $port")
            try {
                startNativeServer("127.0.0.1", port)
                return
            } catch (e: Exception) {
                Log.e(TAG, "JNI start failed, trying binary fallback", e)
            }
        }

        if (process != null) return

        val binary = extractServer(context) ?: run {
            Log.e(TAG, "Fatal: No JNI and no binary available")
            return
        }

        Thread {
            try {
                Log.d(TAG, "Starting server via binary: ${binary.absolutePath}")
                val pb = ProcessBuilder(binary.absolutePath)
                pb.environment()["NCM_PORT"] = port.toString()
                pb.environment()["NCM_HOST"] = "127.0.0.1"
                pb.redirectErrorStream(true)
                val p = pb.start()
                process = p
                p.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.i(TAG, "[Rust] $line")
                    }
                }
                p.waitFor()
            } catch (e: Exception) {
                Log.e(TAG, "Binary execution failed", e)
            } finally {
                process = null
            }
        }.start()
    }

    fun stopServer() {
        process?.destroy()
        process = null
    }
}
