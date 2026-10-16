package com.remotebot.core.common.logger

import android.content.Context
import android.util.Log
import com.remotebot.domain.model.LogEntry
import com.remotebot.domain.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

class AppLogger(
    context: Context? = null,
    initialMaxCapacity: Int = 1000,
) {
    @Volatile
    var maxCapacity: Int = initialMaxCapacity
        private set

    fun setMaxCapacity(newCapacity: Int) {
        if (newCapacity <= 0) return
        synchronized(entries) {
            maxCapacity = newCapacity
            while (entries.size > newCapacity) {
                entries.removeAt(0)
            }
            _logsFlow.value = ArrayList(entries)
        }
    }

    private val entries = Collections.synchronizedList(ArrayList<LogEntry>(initialMaxCapacity))
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private val logFile: File? =
        context?.let {
            val dir = File(it.filesDir, "logs").apply { mkdirs() }
            File(dir, "app_events.log")
        }
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    init {
        instance = this
        loadLogsFromDisk()
    }

    private fun loadLogsFromDisk() {
        if (logFile == null || !logFile.exists()) return
        try {
            val lines = logFile.readLines()
            val loaded = lines.takeLast(maxCapacity).mapNotNull { parseLine(it) }
            synchronized(entries) {
                entries.clear()
                entries.addAll(loaded)
                _logsFlow.value = ArrayList(entries)
            }
        } catch (e: Exception) {
            Log.e("AppLogger", "Сбой чтения логов с диска", e)
        }
    }

    private fun parseLine(line: String): LogEntry? {
        // Формат: 1728374829102|INFO|Tag|Message
        val parts = line.split("|", limit = 4)
        if (parts.size < 4) return null
        val ts = parts[0].toLongOrNull() ?: return null
        val level =
            try {
                LogLevel.valueOf(parts[1])
            } catch (e: Exception) {
                LogLevel.INFO
            }
        val tag = parts[2]
        val msg = parts[3]
        return LogEntry(timestampMs = ts, level = level, tag = tag, message = msg)
    }

    private fun addEntry(entry: LogEntry) {
        synchronized(entries) {
            if (entries.size >= maxCapacity) {
                entries.removeAt(0)
            }
            entries.add(entry)
            _logsFlow.value = ArrayList(entries)
        }

        // Асинхронная запись на диск
        if (logFile != null) {
            ioScope.launch {
                try {
                    val line = "${entry.timestampMs}|${entry.level.name}|${entry.tag}|${entry.message}\n"
                    logFile.appendText(line)
                    // Ротация если файл больше 2 МБ
                    if (logFile.length() > 2 * 1024 * 1024) {
                        val currentLines = logFile.readLines().takeLast(maxCapacity)
                        logFile.writeText(currentLines.joinToString("\n") + "\n")
                    }
                } catch (e: Exception) {
                    // Игнорируем сбой записи лога
                }
            }
        }
    }

    fun t(
        tag: String,
        message: String,
    ) {
        Log.v(tag, message)
        addEntry(LogEntry(level = LogLevel.TRACE, tag = tag, message = message))
    }

    fun d(
        tag: String,
        message: String,
    ) {
        Log.d(tag, message)
        addEntry(LogEntry(level = LogLevel.DEBUG, tag = tag, message = message))
    }

    fun i(
        tag: String,
        message: String,
    ) {
        Log.i(tag, message)
        addEntry(LogEntry(level = LogLevel.INFO, tag = tag, message = message))
    }

    fun w(
        tag: String,
        message: String,
    ) {
        Log.w(tag, message)
        addEntry(LogEntry(level = LogLevel.WARN, tag = tag, message = message))
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val fullMsg = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        Log.e(tag, fullMsg, throwable)
        addEntry(LogEntry(level = LogLevel.ERROR, tag = tag, message = fullMsg))
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
            _logsFlow.value = emptyList()
        }
        if (logFile != null && logFile.exists()) {
            ioScope.launch {
                try {
                    logFile.delete()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    fun getLogFileSize(): Long = if (logFile != null && logFile.exists()) logFile.length() else 0L

    fun getLast(count: Int = 50): List<LogEntry> {
        synchronized(entries) {
            val start = maxOf(0, entries.size - count)
            return entries.subList(start, entries.size).toList()
        }
    }

    fun getFiltered(
        count: Int = 50,
        minLevel: LogLevel? = null,
    ): List<LogEntry> {
        synchronized(entries) {
            val filtered =
                if (minLevel != null) {
                    entries.filter { it.level.ordinal >= minLevel.ordinal }
                } else {
                    entries
                }
            val start = maxOf(0, filtered.size - count)
            return filtered.subList(start, filtered.size).toList()
        }
    }

    fun getFormattedLogsForExport(count: Int = Int.MAX_VALUE): String {
        val entries = getLast(count)
        return entries.joinToString("\n") { entry ->
            val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(java.util.Date(entry.timestampMs))
            "$time [${entry.level.name}] ${entry.tag}: ${entry.message}"
        }
    }

    companion object {
        @Volatile
        var instance: AppLogger? = null

        fun t(
            tag: String,
            message: String,
        ) {
            instance?.t(tag, message) ?: Log.v(tag, message)
        }

        fun d(
            tag: String,
            message: String,
        ) {
            instance?.d(tag, message) ?: Log.d(tag, message)
        }

        fun i(
            tag: String,
            message: String,
        ) {
            instance?.i(tag, message) ?: Log.i(tag, message)
        }

        fun w(
            tag: String,
            message: String,
        ) {
            instance?.w(tag, message) ?: Log.w(tag, message)
        }

        fun e(
            tag: String,
            message: String,
            throwable: Throwable? = null,
        ) {
            instance?.e(tag, message, throwable) ?: Log.e(tag, message, throwable)
        }
    }
}
