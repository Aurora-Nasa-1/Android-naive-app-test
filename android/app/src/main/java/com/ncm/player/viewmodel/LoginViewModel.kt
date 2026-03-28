package com.ncm.player.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ncm.player.api.NcmApiService
import com.ncm.player.util.UserPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:3000/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NcmApiService::class.java)

    var qrCodeBitmap by mutableStateOf<Bitmap?>(null)
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
        fetchJob = viewModelScope.launch {
            try {
                Log.d("LoginVM", "Fetching QR key...")
                val keyResponse = apiService.getQrKey()
                val key = keyResponse.body()?.get("data")?.asJsonObject?.get("unikey")?.asString ?: run {
                    loginStatus = "Failed to get QR key"
                    return@launch
                }

                Log.d("LoginVM", "Creating QR image for key: $key")
                val qrResponse = apiService.createQr(key)
                val qrImg = qrResponse.body()?.get("data")?.asJsonObject?.get("qrimg")?.asString ?: run {
                    loginStatus = "Failed to create QR image"
                    return@launch
                }

                Log.d("LoginVM", "Decoding QR image (length: ${qrImg.length})...")
                val base64Data = qrImg.substringAfter(",")
                val decodedString: ByteArray = Base64.decode(base64Data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)

                if (bitmap != null) {
                    qrCodeBitmap = bitmap
                    loginStatus = "Waiting for scan..."
                    startChecking(key)
                } else {
                    Log.e("LoginVM", "Failed to decode QR bitmap")
                    loginStatus = "Decoding error"
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
                    val statusResponse = apiService.checkQr(key)
                    val body = statusResponse.body()
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
