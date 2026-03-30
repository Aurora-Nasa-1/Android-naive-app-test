package com.ncm.player.util

import android.content.Context
import android.util.Log

object RustServerManager {
    private var process: Process? = null
    private const val TAG = "RustServer"

    fun extractServer(context: android.content.Context): java.io.File? {
        val abi = android.os.Build.SUPPORTED_ABIS[0]
        val assetName = when {
            abi.contains("arm64") || abi.contains("aarch64") -> "ncm-server-aarch64"
            else -> return null
        }

        val outFile = java.io.File(context.filesDir, "ncm-server")
        if (outFile.exists() && outFile.canExecute()) return outFile

        try {
            Log.d(TAG, "Extracting binary: bin/$assetName -> ${outFile.absolutePath}")
            context.assets.open("bin/$assetName").use { input ->
                java.io.FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile.setExecutable(true, true)
            Log.d(TAG, "Extraction complete and executable set")
            return outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract binary", e)
            return null
        }
    }

    fun startServer(context: android.content.Context, port: Int = 3000) {
        if (process != null) return

        Thread {
            try {
                val binary = extractServer(context) ?: run {
                    Log.e(TAG, "Fatal: No binary available for extraction")
                    return@Thread
                }

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
                Log.e(TAG, "Binary execution or extraction failed", e)
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
