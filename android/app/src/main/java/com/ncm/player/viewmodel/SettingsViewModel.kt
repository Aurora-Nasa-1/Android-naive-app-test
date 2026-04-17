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
    var themeMode by mutableIntStateOf(UserPreferences.getThemeMode(application))
        private set
    var followCoverApp by mutableStateOf(UserPreferences.getFollowCoverApp(application))
        private set
    var followCoverMini by mutableStateOf(UserPreferences.getFollowCoverMini(application))
        private set
    var followCoverPlayer by mutableStateOf(UserPreferences.getFollowCoverPlayer(application))
        private set
    var useFluidBackground by mutableStateOf(UserPreferences.getUseFluidBackground(application))
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

    fun updateThemeMode(m: Int) {
        themeMode = m
        UserPreferences.saveThemeMode(getApplication(), m)
    }

    fun updateFollowCoverApp(e: Boolean) {
        followCoverApp = e
        UserPreferences.saveFollowCoverApp(getApplication(), e)
    }

    fun updateFollowCoverMini(e: Boolean) {
        followCoverMini = e
        UserPreferences.saveFollowCoverMini(getApplication(), e)
    }

    fun updateFollowCoverPlayer(e: Boolean) {
        followCoverPlayer = e
        UserPreferences.saveFollowCoverPlayer(getApplication(), e)
    }

    fun updateUseFluidBackground(e: Boolean) {
        useFluidBackground = e
        UserPreferences.saveUseFluidBackground(getApplication(), e)
    }

    fun updateDownloadPath(p: String) {
        downloadDir = p
        UserPreferences.saveDownloadDir(getApplication(), p)
    }

    fun clearCache() { /* API */ }
}
