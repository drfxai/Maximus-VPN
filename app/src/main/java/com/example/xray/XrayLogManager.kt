package com.example.xray

import com.example.core.SecretRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object XrayLogManager {

    private const val MAX_LOGS = 250
    private val logQueue = ArrayDeque<String>(MAX_LOGS + 10)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _logsFlow = MutableStateFlow<List<String>>(emptyList())
    val logsFlow: StateFlow<List<String>> = _logsFlow.asStateFlow()

    @Synchronized
    fun appendLog(message: String, tag: String = "XRAY") {
        val timestamp = dateFormat.format(Date())
        val sanitized = SecretRedactor.redact(message)
        val formatted = "[$timestamp] [$tag] $sanitized"

        if (logQueue.size >= MAX_LOGS) {
            logQueue.pollFirst()
        }
        logQueue.addLast(formatted)

        _logsFlow.value = ArrayList(logQueue)
    }

    @Synchronized
    fun getLogs(): List<String> = ArrayList(logQueue)

    @Synchronized
    fun clear() {
        logQueue.clear()
        _logsFlow.value = emptyList()
    }
}
