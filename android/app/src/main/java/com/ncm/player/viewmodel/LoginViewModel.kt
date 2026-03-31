package com.ncm.player.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.net.URLEncoder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ncm.player.util.UserPreferences
import com.ncm.player.util.RustServerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    var qrCodeBitmap by mutableStateOf<Bitmap?>(null)
    var qrUrl by mutableStateOf<String?>(null)
    var loginStatus by mutableStateOf("Initializing...")
    var isLogged by mutableStateOf(false)
    var cookie by mutableStateOf<String?>(null)

    private var checkJob: Job? = null
    private var fetchJob: Job? = null

    init {
        cookie = UserPreferences.getCookie(application)
        if (cookie != null) {
            isLogged = true
            loginStatus = "Already logged in"
        }
    }

    fun fetchQrCode() {
        if (fetchJob?.isActive == true) return

        loginStatus = "Fetching QR Code..."
        qrCodeBitmap = null
        qrUrl = null

        fetchJob = viewModelScope.launch {
            try {
                Log.d("LoginVM", "Fetching QR key...")
                val keyResult = withContext(Dispatchers.IO) { RustServerManager.callApi("login/qr/key", emptyMap()) }
                val keyBody = JsonParser.parseString(keyResult).asJsonObject
                val key = keyBody?.get("data")?.asJsonObject?.get("unikey")?.asString
                    ?: keyBody?.get("unikey")?.asString
                    ?: run {
                        Log.e("LoginVM", "Failed to parse unikey from: $keyBody")
                        loginStatus = "Failed to get QR key"
                        return@launch
                    }

                Log.d("LoginVM", "Creating QR image for key: $key")
                val qrResult = withContext(Dispatchers.IO) { RustServerManager.callApi("login/qr/create", mapOf("key" to key, "qrimg" to "true")) }
                val qrBody = JsonParser.parseString(qrResult).asJsonObject
                val qrData = qrBody?.get("data")?.asJsonObject
                val qrImg = qrData?.get("qrimg")?.asString
                val qrUrlFromApi = qrData?.get("qrurl")?.asString

                if (qrUrlFromApi != null) {
                    val encodedUrl = URLEncoder.encode(qrUrlFromApi, "UTF-8")
                    qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=256x256&data=$encodedUrl"
                }

                if (!qrImg.isNullOrEmpty()) {
                    Log.d("LoginVM", "Decoding QR image (length: ${qrImg.length})...")
                    val base64Data = qrImg.substringAfter(",")
                    val decodedString: ByteArray = Base64.decode(base64Data, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)

                    if (bitmap != null) {
                        qrCodeBitmap = bitmap
                        loginStatus = "Waiting for scan..."
                        startChecking(key)
                        return@launch
                    }
                }

                if (qrUrl != null) {
                    Log.d("LoginVM", "Using fallback QR URL: $qrUrl")
                    loginStatus = "Waiting for scan..."
                    startChecking(key)
                } else {
                    Log.e("LoginVM", "No QR image or URL available")
                    loginStatus = "Failed to generate QR"
                }
            } catch (e: Exception) {
                Log.e("LoginVM", "Error fetching QR code", e)
                loginStatus = "Error: ${e.message}"
            }
        }
    }

    private fun startChecking(key: String) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            Log.d("LoginVM", "Starting QR status check loop for key: $key")
            while (true) {
                delay(3000)
                try {
                    val statusResult = withContext(Dispatchers.IO) { RustServerManager.callApi("login/qr/check", mapOf("key" to key)) }
                    val body = JsonParser.parseString(statusResult).asJsonObject
                    val code = body?.get("code")?.asInt ?: 0
                    Log.d("LoginVM", "QR status code: $code")
                    when (code) {
                        800 -> {
                            loginStatus = "QR Code expired"
                            break
                        }
                        801 -> loginStatus = "Waiting for scan..."
                        802 -> loginStatus = "Waiting for confirmation..."
                        803 -> {
                            Log.d("LoginVM", "Login successful")
                            loginStatus = "Login success!"
                            val newCookie = body?.get("cookie")?.asString
                            cookie = newCookie
                            newCookie?.let { UserPreferences.saveCookie(getApplication(), it) }
                            isLogged = true
                            break
                        }
                        else -> {
                            Log.w("LoginVM", "Unexpected status code: $code")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LoginVM", "Error checking QR status", e)
                    loginStatus = "Check error: ${e.message}"
                }
            }
        }
    }
}
