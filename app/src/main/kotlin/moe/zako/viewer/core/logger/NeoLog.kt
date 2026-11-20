package moe.zako.viewer.core.logger

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object NeoLog {
    private const val TAG = "NeoViewer"
    private const val MAX_LOGS = 1000
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    ) {
        fun formatted(): String {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
            return "[$time] [$level] $tag: $message"
        }
    }

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private fun addLog(level: String, tag: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        logQueue.add(entry)
        while (logQueue.size > MAX_LOGS) {
            logQueue.poll()
        }
        _logsFlow.value = logQueue.toList()
    }

    fun d(tag: String = TAG, message: String) {
        Log.d(tag, message)
        addLog("DEBUG", tag, message)
    }

    fun i(tag: String = TAG, message: String) {
        Log.i(tag, message)
        addLog("INFO", tag, message)
    }

    fun w(tag: String = TAG, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        Log.w(tag, msg)
        addLog("WARN", tag, msg)
    }

    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        Log.e(tag, msg)
        addLog("ERROR", tag, msg)
    }

    fun clear() {
        logQueue.clear()
        _logsFlow.value = emptyList()
    }

    fun exportToFile(context: Context): File {
        val exportDir = File(context.cacheDir, "logs")
        if (!exportDir.exists()) exportDir.mkdirs()
        val fileName = "NeoViewer_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
        val logFile = File(exportDir, fileName)
        logFile.bufferedWriter().use { writer ->
            writer.write("=== NeoViewer Execution Log Export ===\n")
            writer.write("Exported At: ${timeFormat.format(Date())}\n")
            writer.write("Total Entries: ${logQueue.size}\n\n")
            for (entry in logQueue) {
                writer.write(entry.formatted())
                writer.newLine()
            }
        }
        return logFile
    }
}

typealias NeoLogger = NeoLog

