package com.ncm.player.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

object RustServerManager {
    private var process: Process? = null
    private const val TAG = "RustServer"

    fun extractServer(context: Context): String? {
        val abi = Build.SUPPORTED_ABIS[0]
        val assetName = when {
            abi.contains("arm64") || abi.contains("aarch64") -> "ncm-server-aarch64"
            abi.contains("armeabi-v7a") || abi.contains("armv7") -> "ncm-server-armv7"
            abi.contains("x86_64") -> "ncm-server-x86_64"
            abi.contains("x86") -> "ncm-server-i686"
            else -> return null
        }

        val outFile = File(context.filesDir, "ncm-server")
        try {
            context.assets.open("bin/$assetName").use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile.setExecutable(true)
            return outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract server", e)
            return null
        }
    }

    fun startServer(context: Context, port: Int = 3000) {
        if (process != null) return

        val serverPath = extractServer(context) ?: return

        Thread {
            try {
                val pb = ProcessBuilder(serverPath)
                val env = pb.environment()
                env["NCM_PORT"] = port.toString()
                env["NCM_HOST"] = "127.0.0.1"
                env["RUST_LOG"] = "info"

                pb.redirectErrorStream(true)
                process = pb.start()

                val reader = BufferedReader(InputStreamReader(process?.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.i(TAG, "[Rust] $line")
                }

                process?.waitFor()
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
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
