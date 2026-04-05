package com.ncm.player.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

object LogManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    data class LogEntry(
        val time: String,
        val level: String,
        val message: String,
        val throwable: String? = null
    )

    fun log(level: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            time = dateFormat.format(Date()),
            level = level,
            message = message,
            throwable = throwable?.let { android.util.Log.getStackTraceString(it) }
        )
        val currentList = _logs.value.toMutableList()
        currentList.add(entry)
        if (currentList.size > 500) {
            currentList.removeAt(0)
        }
        _logs.value = currentList
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
