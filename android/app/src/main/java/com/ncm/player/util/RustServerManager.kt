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

        // Optimistic check: if it exists and version is same (could add version check later)
        // For now, let's at least check if it exists and is executable
        if (outFile.exists() && outFile.canExecute()) {
            Log.d(TAG, "Server already extracted and executable")
            return outFile.absolutePath
        }

        try {
            val assetExists = context.assets.list("bin")?.contains(assetName) ?: false
            if (!assetExists) {
                Log.e(TAG, "Asset not found: bin/$assetName")
                return null
            }
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
        if (process != null) {
            Log.d(TAG, "Server is already running.")
            return
        }

        val serverPath = extractServer(context) ?: run {
            Log.e(TAG, "Failed to extract server.")
            return
        }

        // Run the server in a separate thread to avoid blocking the main thread.
        Thread {
            try {
                Log.d(TAG, "Starting Rust server from $serverPath on port $port")
                val pb = ProcessBuilder(serverPath)
                val env = pb.environment()
                env["NCM_PORT"] = port.toString()
                env["NCM_HOST"] = "127.0.0.1"
                env["RUST_LOG"] = "info"

                // Merge stdout and stderr for easier logging.
                pb.redirectErrorStream(true)

                val startedProcess = pb.start()
                process = startedProcess

                // Efficiently read logs to prevent process hanging due to pipe filling.
                startedProcess.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.i(TAG, "[Rust] $line")
                    }
                }

                val exitCode = startedProcess.waitFor()
                Log.d(TAG, "Rust server process exited with code $exitCode")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to manage Rust server process", e)
            } finally {
                process = null
            }
        }.apply {
            name = "RustServerThread"
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    fun stopServer() {
        process?.destroy()
        process = null
    }
}
