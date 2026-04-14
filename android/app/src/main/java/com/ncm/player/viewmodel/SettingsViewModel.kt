package com.ncm.player.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import com.ncm.player.util.UserPreferences

class SettingsViewModel(application: Application) : BaseViewModel(application) {
    var qualityWifi by mutableStateOf(UserPreferences.getQualityWifi(application))
        private set
    var qualityCellular by mutableStateOf(UserPreferences.getQualityCellular(application))
        private set
    var fadeDuration by mutableStateOf(UserPreferences.getFadeDuration(application))
        private set
    var cacheSize by mutableIntStateOf(UserPreferences.getCacheSize(application))
        private set
    var useCellularCache by mutableStateOf(UserPreferences.getUseCellularCache(application))
        private set
    var pureBlackMode by mutableStateOf(UserPreferences.getPureBlackMode(application))
        private set
    var downloadDir by mutableStateOf(UserPreferences.getDownloadDir(application))
        private set

    fun updateQualityWifi(q: String) {
        qualityWifi = q
        UserPreferences.saveQualityWifi(getApplication(), q)
    }

    fun updateQualityCellular(q: String) {
        qualityCellular = q
        UserPreferences.saveQualityCellular(getApplication(), q)
    }

    fun updateFade(d: Float) {
        fadeDuration = d
        UserPreferences.saveFadeDuration(getApplication(), d)
    }

    fun updateCache(s: Int) {
        cacheSize = s
        UserPreferences.saveCacheSize(getApplication(), s)
    }

    fun updateUseCellular(u: Boolean) {
        useCellularCache = u
        UserPreferences.saveUseCellularCache(getApplication(), u)
    }

    fun updatePureBlackMode(e: Boolean) {
        pureBlackMode = e
        UserPreferences.savePureBlackMode(getApplication(), e)
    }

    fun updateDownloadPath(p: String) {
        downloadDir = p
        UserPreferences.saveDownloadDir(getApplication(), p)
    }

    fun clearCache() { /* API */ }
}
