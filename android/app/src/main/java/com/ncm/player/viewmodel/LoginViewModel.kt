package com.ncm.player.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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
    var loginStatus by mutableStateOf("Waiting for scan...")
    var isLogged by mutableStateOf(false)
    var cookie by mutableStateOf<String?>(null)

    private var checkJob: Job? = null

    init {
        cookie = UserPreferences.getCookie(application)
        if (cookie != null) {
            isLogged = true
            loginStatus = "Already logged in"
        }
    }

    fun fetchQrCode() {
        viewModelScope.launch {
            try {
                val keyResponse = apiService.getQrKey()
                val key = keyResponse.body()?.get("data")?.asJsonObject?.get("unikey")?.asString ?: return@launch

                val qrResponse = apiService.createQr(key)
                val qrImg = qrResponse.body()?.get("data")?.asJsonObject?.get("qrimg")?.asString ?: return@launch

                val decodedString: ByteArray = Base64.decode(qrImg.substringAfter(","), Base64.DEFAULT)
                qrCodeBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)

                startChecking(key)
            } catch (e: Exception) {
                loginStatus = "Error: ${e.message}"
            }
        }
    }

    private fun startChecking(key: String) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            while (true) {
                delay(3000)
                try {
                    val statusResponse = apiService.checkQr(key)
                    val code = statusResponse.body()?.get("code")?.asInt ?: 0
                    when (code) {
                        800 -> loginStatus = "QR Code expired"
                        801 -> loginStatus = "Waiting for scan..."
                        802 -> loginStatus = "Waiting for confirmation..."
                        803 -> {
                            loginStatus = "Login success!"
                            val newCookie = statusResponse.body()?.get("cookie")?.asString
                            cookie = newCookie
                            newCookie?.let { UserPreferences.saveCookie(getApplication(), it) }
                            isLogged = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    loginStatus = "Check error: ${e.message}"
                }
            }
        }
    }
}
