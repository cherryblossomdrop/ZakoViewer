package moe.zako.viewer.core.logger

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.zako.viewer.core.storage.ExportResult
import moe.zako.viewer.core.storage.PublicStorageHelper
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

object ZakoLog {
    private const val TAG = "ZakoViewer"
    private const val MAX_LOGS = 1000
    const val MAX_LOG_SIZE_BYTES = 5L * 1024 * 1024 // 5 MB 体积上限
    private const val LOG_FILE_NAME = "zako_persistent.log"

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

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var lastClearTimestamp: Long = 0L
    private val writeLock = Any()
    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ZakoLog-Writer").apply { isDaemon = true }
    }

    /**
     * 初始化日志系统，恢复历史日志，绝不在启动时覆盖或清空历史文件。
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        loadRecentHistoryAsync()
    }

    private fun getPersistentLogFile(): File? {
        val ctx = appContext ?: return null
        val logsDir = File(ctx.filesDir, "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        return File(logsDir, LOG_FILE_NAME)
    }

    /**
     * 异步预热加载历史持久化日志尾部内容到内存队列，使界面可查阅先前运行状态。
     */
    private fun loadRecentHistoryAsync() {
        writeExecutor.execute {
            try {
                val file = getPersistentLogFile() ?: return@execute
                if (!file.exists() || file.length() == 0L) return@execute
                synchronized(writeLock) {
                    val length = file.length()
                    val readBytes = minOf(length, 256 * 1024L).toInt() // 读取尾部最多 256KB
                    val buffer = ByteArray(readBytes)
                    RandomAccessFile(file, "r").use { raf ->
                        raf.seek(length - readBytes)
                        raf.readFully(buffer)
                    }
                    val text = String(buffer, Charsets.UTF_8)
                    val lines = text.lines()
                    // 过滤掉第一行可能被阶段性截断的内容
                    val validLines = if (lines.size > 1) lines.drop(1) else lines
                    val recentEntries = mutableListOf<LogEntry>()
                    for (line in validLines) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        // 解析格式: [yyyy-MM-dd HH:mm:ss.SSS] [LEVEL] TAG: Message
                        if (trimmed.startsWith("[") && trimmed.contains("] [")) {
                            val levelStart = trimmed.indexOf("] [") + 3
                            val levelEnd = trimmed.indexOf("]", levelStart)
                            if (levelEnd > levelStart) {
                                val level = trimmed.substring(levelStart, levelEnd)
                                val tagStart = levelEnd + 2
                                val colonIdx = trimmed.indexOf(":", tagStart)
                                if (colonIdx > tagStart) {
                                    val tag = trimmed.substring(tagStart, colonIdx).trim()
                                    val msg = trimmed.substring(colonIdx + 1).trim()
                                    recentEntries.add(LogEntry(System.currentTimeMillis(), level, tag, msg))
                                    continue
                                }
                            }
                        }
                        recentEntries.add(LogEntry(System.currentTimeMillis(), "INFO", "History", trimmed))
                    }
                    if (recentEntries.isNotEmpty()) {
                        val takeCount = minOf(recentEntries.size, MAX_LOGS)
                        val subList = recentEntries.takeLast(takeCount)
                        logQueue.clear()
                        logQueue.addAll(subList)
                        _logsFlow.value = logQueue.toList()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load recent history log", e)
            }
        }
    }

    /**
     * 检查日志文件是否超过 5 MB 阈值，超过时保留最新一半（约 2.5 MB）并按换行符对齐。
     */
    private fun checkAndTrimLogFile(file: File) {
        if (!file.exists() || file.length() <= MAX_LOG_SIZE_BYTES) return
        try {
            val length = file.length()
            val targetKeepBytes = (MAX_LOG_SIZE_BYTES / 2).toInt() // 约 2.5 MB
            val startOffset = length - targetKeepBytes
            if (startOffset <= 0) return

            val bytesToRead = (length - startOffset).toInt()
            val buffer = ByteArray(bytesToRead)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(startOffset)
                raf.readFully(buffer)
            }

            // 对齐到下一个完整换行符开头
            var newlineIdx = 0
            while (newlineIdx < buffer.size && buffer[newlineIdx] != '\n'.code.toByte()) {
                newlineIdx++
            }
            val validStart = if (newlineIdx < buffer.size) newlineIdx + 1 else 0

            // 覆盖写回最新的一半日志
            FileOutputStream(file, false).use { fos ->
                if (validStart < buffer.size) {
                    fos.write(buffer, validStart, buffer.size - validStart)
                }
                fos.flush()
            }
            Log.i(TAG, "Trimmed log file: reduced from $length to ${file.length()} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming log file", e)
        }
    }

    private fun addLog(level: String, tag: String, message: String) {
        val now = System.currentTimeMillis()
        if (now < lastClearTimestamp) return
        val entry = LogEntry(now, level, tag, message)
        logQueue.add(entry)
        while (logQueue.size > MAX_LOGS) {
            logQueue.poll()
        }
        _logsFlow.value = logQueue.toList()

        val formattedLine = entry.formatted() + "\n"
        writeExecutor.execute {
            if (entry.timestamp < lastClearTimestamp) return@execute
            val file = getPersistentLogFile() ?: return@execute
            synchronized(writeLock) {
                if (entry.timestamp < lastClearTimestamp) return@synchronized
                try {
                    checkAndTrimLogFile(file)
                    FileOutputStream(file, true).use { fos ->
                        fos.write(formattedLine.toByteArray(Charsets.UTF_8))
                        fos.flush()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing persistent log", e)
                }
            }
        }
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

    /**
     * 崩溃发生时在当前调用线程以同步方式落盘 [FATAL] 堆栈信息并立刻 flush()
     */
    fun logFatalSync(tag: String, throwable: Throwable) {
        val time = timeFormat.format(Date())
        val stackTrace = Log.getStackTraceString(throwable)
        val fatalMsg = "Uncaught Exception\n$stackTrace"
        val entry = LogEntry(System.currentTimeMillis(), "FATAL", tag, fatalMsg)

        logQueue.add(entry)
        while (logQueue.size > MAX_LOGS) {
            logQueue.poll()
        }
        _logsFlow.value = logQueue.toList()

        val fatalLine = "[$time] [FATAL] $tag: $fatalMsg\n"
        val file = getPersistentLogFile() ?: return
        synchronized(writeLock) {
            try {
                checkAndTrimLogFile(file)
                FileOutputStream(file, true).use { fos ->
                    fos.write(fatalLine.toByteArray(Charsets.UTF_8))
                    fos.flush()
                    fos.fd.sync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing fatal persistent log synchronously", e)
            }
        }
    }

    /**
     * 清空内存与磁盘上的全部持久化运行日志，并清理历史旧日志与历史导出临时文件。
     */
    fun clear() {
        synchronized(writeLock) {
            lastClearTimestamp = System.currentTimeMillis()
            logQueue.clear()
            _logsFlow.value = emptyList()

            try {
                // 1. 关闭/截断持久化日志文件为 0 字节
                val file = getPersistentLogFile()
                if (file != null && file.exists()) {
                    FileOutputStream(file, false).use { fos ->
                        fos.flush()
                    }
                }

                // 2. 清理 filesDir/logs 下其他旧日志文件
                val ctx = appContext
                if (ctx != null) {
                    val logsDir = File(ctx.filesDir, "logs")
                    if (logsDir.exists() && logsDir.isDirectory) {
                        logsDir.listFiles()?.forEach { f ->
                            if (f.name != LOG_FILE_NAME) {
                                try { f.delete() } catch (_: Exception) {}
                            }
                        }
                    }

                    // 3. 清理 cacheDir/logs 下历史导出临时文件
                    val cacheLogsDir = File(ctx.cacheDir, "logs")
                    if (cacheLogsDir.exists() && cacheLogsDir.isDirectory) {
                        cacheLogsDir.listFiles()?.forEach { f ->
                            try { f.delete() } catch (_: Exception) {}
                        }
                    }
                }
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing persistent log files", e)
            }
        }
    }

    /**
     * 将持久化日志文件导出到公共目录 Download/ZakoViewer/Logs/ZakoViewer_log_yyyyMMdd_HHmmss.txt，
     * 严格只导出清空之后的新记录（或若未清空则是全部日志），不再在私有 cache/logs/ 中残留导出文件。
     */
    fun exportToPublicDownloads(context: Context): ExportResult {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ZakoViewer_log_${timeStamp}.txt"
        val persistentFile = getPersistentLogFile()

        val exportResult = PublicStorageHelper.createPublicDownloadOutputStream(
            context = context,
            subDir = PublicStorageHelper.LOGS_SUB_DIR,
            fileName = fileName,
            mimeType = "text/plain"
        ) { outputStream ->
            synchronized(writeLock) {
                if (persistentFile != null && persistentFile.exists() && persistentFile.length() > 0L) {
                    persistentFile.inputStream().use { input ->
                        input.copyTo(outputStream)
                    }
                } else {
                    outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write("=== ZakoViewer Execution Log Export ===\n")
                        writer.write("Exported At: ${timeFormat.format(Date())}\n")
                        writer.write("Total Entries: 0\n\n")
                        writer.flush()
                    }
                }
            }
        }

        // 清理可能遗留的历史 cache/logs 导出临时文件
        try {
            val cacheLogsDir = File(context.cacheDir, "logs")
            if (cacheLogsDir.exists()) {
                cacheLogsDir.deleteRecursively()
            }
        } catch (_: Exception) {}

        return exportResult
    }

    /**
     * 兼容性保留：若需导出为本地临时 File
     */
    fun exportToFile(context: Context): File {
        val exportDir = File(context.cacheDir, "logs")
        if (!exportDir.exists()) exportDir.mkdirs()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ZakoViewer_log_${timeStamp}.txt"
        val exportFile = File(exportDir, fileName)

        val persistentFile = getPersistentLogFile()
        synchronized(writeLock) {
            if (persistentFile != null && persistentFile.exists() && persistentFile.length() > 0L) {
                persistentFile.copyTo(exportFile, overwrite = true)
            } else {
                exportFile.bufferedWriter().use { writer ->
                    writer.write("=== ZakoViewer Execution Log Export ===\n")
                    writer.write("Exported At: ${timeFormat.format(Date())}\n")
                    writer.write("Total Entries: 0\n\n")
                }
            }
        }
        return exportFile
    }
}

typealias ZakoLogger = ZakoLog
typealias NeoLog = ZakoLog
typealias NeoLogger = ZakoLog
