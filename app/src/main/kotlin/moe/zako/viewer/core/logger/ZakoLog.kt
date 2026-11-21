package moe.zako.viewer.core.logger

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.zako.viewer.BuildConfig
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
    const val MAX_LOG_SIZE_BYTES = 5L * 1024 * 1024 // 5 MB 单文件体积上限
    private const val LOG_FILE_NAME = "zako_persistent.log"
    private const val ARCHIVE_PREFIX = "zako_archive_"
    private const val PREFS_NAME = "zako_logger_meta"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
    private const val KEY_LAST_VERSION_NAME = "last_version_name"
    private const val KEY_LAST_BUILD_TIME = "last_build_time"

    val CURRENT_VERSION_TAG: String = "v${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String,
        val versionTag: String = CURRENT_VERSION_TAG
    ) {
        fun formatted(): String {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
            return "[$time] [$versionTag] [$level] $tag: $message"
        }
    }

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private val _includeHistoryFlow = MutableStateFlow(false)
    val includeHistoryFlow: StateFlow<Boolean> = _includeHistoryFlow.asStateFlow()

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var lastClearTimestamp: Long = 0L
    private val writeLock = Any()
    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ZakoLog-Writer").apply { isDaemon = true }
    }

    /**
     * 初始化日志系统：
     * 1. 检查版本/构建变更，若发生升级则自动归档旧版日志文件；
     * 2. 打印冷启动横幅及版本升级提示；
     * 3. 异步恢复当前版本的历史日志。
     */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app

        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastVersionCode = prefs.getInt(KEY_LAST_VERSION_CODE, -1)
        val lastVersionName = prefs.getString(KEY_LAST_VERSION_NAME, "") ?: ""
        val lastBuildTime = prefs.getString(KEY_LAST_BUILD_TIME, "") ?: ""

        val curVersionCode = BuildConfig.VERSION_CODE
        val curVersionName = BuildConfig.VERSION_NAME
        val curBuildTime = BuildConfig.BUILD_TIME
        val curBuildType = if (BuildConfig.DEBUG) "debug" else "release"

        val isUpdated = (lastVersionCode != -1) && (lastVersionCode != curVersionCode || lastBuildTime != curBuildTime)

        if (isUpdated) {
            // 版本或构建变化：归档当前日志文件
            archiveCurrentLogFile(lastVersionName, lastVersionCode, lastBuildTime)
        }

        // 保存当前版本元数据
        prefs.edit()
            .putInt(KEY_LAST_VERSION_CODE, curVersionCode)
            .putString(KEY_LAST_VERSION_NAME, curVersionName)
            .putString(KEY_LAST_BUILD_TIME, curBuildTime)
            .apply()

        // 异步预加载持久化日志
        loadRecentHistoryAsync()

        // 输出醒目的启动分隔横幅
        if (isUpdated) {
            val updateBanner = "========== App Updated: v$lastVersionName($lastVersionCode) [build $lastBuildTime] -> v$curVersionName($curVersionCode) [build $curBuildTime] ($curBuildType) =========="
            i("AppLifecycle", "============================================================")
            i("AppLifecycle", updateBanner)
            i("AppLifecycle", "============================================================")
        } else {
            val startBanner = "========== App Started: v$curVersionName($curVersionCode) [build $curBuildTime] ($curBuildType) =========="
            i("AppLifecycle", startBanner)
        }
    }

    private fun getLogsDir(): File? {
        val ctx = appContext ?: return null
        val dir = File(ctx.filesDir, "logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getPersistentLogFile(): File? {
        val logsDir = getLogsDir() ?: return null
        return File(logsDir, LOG_FILE_NAME)
    }

    private fun getArchiveFiles(): List<File> {
        val logsDir = getLogsDir() ?: return emptyList()
        return logsDir.listFiles { file ->
            file.isFile && file.name.startsWith(ARCHIVE_PREFIX) && file.name.endsWith(".log")
        }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }

    /**
     * 将当前的 persistent log 自动重命名归档，为新版本开辟干净的日志上下文。
     */
    private fun archiveCurrentLogFile(oldVersionName: String, oldVersionCode: Int, oldBuildTime: String) {
        synchronized(writeLock) {
            try {
                val currentFile = getPersistentLogFile() ?: return
                if (!currentFile.exists() || currentFile.length() == 0L) return

                val logsDir = getLogsDir() ?: return
                val safeVerName = if (oldVersionName.isNotEmpty()) oldVersionName else "legacy"
                val safeBuildTime = if (oldBuildTime.isNotEmpty()) oldBuildTime else "prev"
                val archiveName = "${ARCHIVE_PREFIX}v${safeVerName}_${oldVersionCode}_${safeBuildTime}.log"
                val archiveFile = File(logsDir, archiveName)

                if (archiveFile.exists()) {
                    // 若归档文件已存在，追加写入
                    FileOutputStream(archiveFile, true).use { fos ->
                        currentFile.inputStream().use { fis ->
                            fis.copyTo(fos)
                        }
                    }
                    currentFile.delete()
                } else {
                    currentFile.renameTo(archiveFile)
                }
                Log.i(TAG, "Archived old version log to ${archiveFile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to archive current log file", e)
            }
        }
    }

    /**
     * 设置是否在查看时包含历史版本归档日志。
     */
    fun setIncludeHistory(include: Boolean) {
        if (_includeHistoryFlow.value == include) return
        _includeHistoryFlow.value = include
        loadRecentHistoryAsync()
    }

    /**
     * 异步预热加载持久化日志到内存队列：
     * 默认仅加载当前版本日志；若 includeHistory=true 则顺带合并历史归档日志。
     */
    private fun loadRecentHistoryAsync() {
        val includeHistory = _includeHistoryFlow.value
        writeExecutor.execute {
            try {
                val persistentFile = getPersistentLogFile() ?: return@execute
                val targetFiles = mutableListOf<File>()
                if (includeHistory) {
                    targetFiles.addAll(getArchiveFiles().reversed())
                }
                if (persistentFile.exists()) {
                    targetFiles.add(persistentFile)
                }

                if (targetFiles.isEmpty()) return@execute

                synchronized(writeLock) {
                    val allParsedEntries = mutableListOf<LogEntry>()
                    for (file in targetFiles) {
                        if (!file.exists() || file.length() == 0L) continue
                        val length = file.length()
                        val readBytes = minOf(length, 256 * 1024L).toInt() // 每个文件读取尾部最多 256KB
                        val buffer = ByteArray(readBytes)
                        RandomAccessFile(file, "r").use { raf ->
                            raf.seek(length - readBytes)
                            raf.readFully(buffer)
                        }
                        val text = String(buffer, Charsets.UTF_8)
                        val lines = text.lines()
                        val validLines = if (lines.size > 1) lines.drop(1) else lines

                        for (line in validLines) {
                            val trimmed = line.trim()
                            if (trimmed.isEmpty()) continue
                            parseLineToEntry(trimmed)?.let { allParsedEntries.add(it) }
                        }
                    }

                    if (allParsedEntries.isNotEmpty()) {
                        val takeCount = minOf(allParsedEntries.size, MAX_LOGS)
                        val subList = allParsedEntries.takeLast(takeCount)
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
     * 兼容解析不同版本的单行日志：
     * 新格式: [yyyy-MM-dd HH:mm:ss.SSS] [v1.x.x(12)] [LEVEL] TAG: Message
     * 旧格式: [yyyy-MM-dd HH:mm:ss.SSS] [LEVEL] TAG: Message
     */
    private fun parseLineToEntry(line: String): LogEntry? {
        try {
            if (!line.startsWith("[")) {
                return LogEntry(System.currentTimeMillis(), "INFO", "History", line, "legacy")
            }
            val firstClose = line.indexOf("]")
            if (firstClose < 0) return null

            val secondOpen = line.indexOf("[", firstClose + 1)
            val secondClose = line.indexOf("]", secondOpen + 1)
            if (secondOpen < 0 || secondClose < 0) return null

            val thirdOpen = line.indexOf("[", secondClose + 1)
            val thirdClose = line.indexOf("]", thirdOpen + 1)

            if (thirdOpen in 0..<thirdClose) {
                // 拥有 3 个方括号: [time] [version] [level] tag: message
                val versionTag = line.substring(secondOpen + 1, secondClose).trim()
                val level = line.substring(thirdOpen + 1, thirdClose).trim()
                val rest = line.substring(thirdClose + 1).trim()
                val colonIdx = rest.indexOf(":")
                return if (colonIdx > 0) {
                    val tag = rest.substring(0, colonIdx).trim()
                    val msg = rest.substring(colonIdx + 1).trim()
                    LogEntry(System.currentTimeMillis(), level, tag, msg, versionTag)
                } else {
                    LogEntry(System.currentTimeMillis(), level, "History", rest, versionTag)
                }
            } else {
                // 旧格式 2 个方括号: [time] [level] tag: message
                val level = line.substring(secondOpen + 1, secondClose).trim()
                val rest = line.substring(secondClose + 1).trim()
                val colonIdx = rest.indexOf(":")
                return if (colonIdx > 0) {
                    val tag = rest.substring(0, colonIdx).trim()
                    val msg = rest.substring(colonIdx + 1).trim()
                    LogEntry(System.currentTimeMillis(), level, tag, msg, "legacy")
                } else {
                    LogEntry(System.currentTimeMillis(), level, "History", rest, "legacy")
                }
            }
        } catch (_: Exception) {
            return LogEntry(System.currentTimeMillis(), "INFO", "History", line, "legacy")
        }
    }

    /**
     * 检查日志文件是否超过单文件 5 MB 阈值，超过时保留最新一半（约 2.5 MB）并按换行符对齐。
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

            var newlineIdx = 0
            while (newlineIdx < buffer.size && buffer[newlineIdx] != '\n'.code.toByte()) {
                newlineIdx++
            }
            val validStart = if (newlineIdx < buffer.size) newlineIdx + 1 else 0

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
        val entry = LogEntry(
            timestamp = now,
            level = level,
            tag = tag,
            message = message,
            versionTag = CURRENT_VERSION_TAG
        )
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
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = "FATAL",
            tag = tag,
            message = fatalMsg,
            versionTag = CURRENT_VERSION_TAG
        )

        logQueue.add(entry)
        while (logQueue.size > MAX_LOGS) {
            logQueue.poll()
        }
        _logsFlow.value = logQueue.toList()

        val fatalLine = "[$time] [$CURRENT_VERSION_TAG] [FATAL] $tag: $fatalMsg\n"
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
     * 清空当前版本及全部历史归档日志，并清理导出临时文件。
     */
    fun clear() {
        synchronized(writeLock) {
            lastClearTimestamp = System.currentTimeMillis()
            logQueue.clear()
            _logsFlow.value = emptyList()

            try {
                // 1. 截断当前持久化日志文件为 0 字节
                val file = getPersistentLogFile()
                if (file != null && file.exists()) {
                    FileOutputStream(file, false).use { fos ->
                        fos.flush()
                    }
                }

                // 2. 清理 logs 目录下所有历史归档文件
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

                    // 3. 清理 cacheDir/logs 下临时文件
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
     * 将持久化日志文件导出到公共目录 Download/ZakoViewer/Logs/ZakoViewer_log_v{version}_{timestamp}.txt，
     * 顶部写入结构化元数据头信息（版本、构建时间、机型、系统等）；
     * 可选择仅导出当前版本日志，或合并导出历史版本归档。
     */
    fun exportToPublicDownloads(context: Context, includeHistory: Boolean = false): ExportResult {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ZakoViewer_log_v${BuildConfig.VERSION_NAME}_${timeStamp}.txt"
        val persistentFile = getPersistentLogFile()

        val exportResult = PublicStorageHelper.createPublicDownloadOutputStream(
            context = context,
            subDir = PublicStorageHelper.LOGS_SUB_DIR,
            fileName = fileName,
            mimeType = "text/plain"
        ) { outputStream ->
            outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                // 写入当前应用版本与设备环境基础信息头
                writer.write("======================================================================\n")
                writer.write("ZakoViewer Execution Log Export\n")
                writer.write("App Version: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})\n")
                writer.write("Build Time: ${BuildConfig.BUILD_TIME}\n")
                writer.write("Build Type: ${if (BuildConfig.DEBUG) "debug" else "release"}\n")
                writer.write("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n")
                writer.write("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")
                writer.write("Exported At: ${timeFormat.format(Date())}\n")
                writer.write("Include Historical Logs: $includeHistory\n")
                writer.write("======================================================================\n\n")
                writer.flush()

                synchronized(writeLock) {
                    // 若需要包含历史归档日志，先写入历史归档
                    if (includeHistory) {
                        val archives = getArchiveFiles().reversed()
                        for (archive in archives) {
                            if (archive.exists() && archive.length() > 0L) {
                                writer.write("----- [ARCHIVE LOG: ${archive.name}] -----\n")
                                writer.flush()
                                archive.inputStream().use { input ->
                                    input.copyTo(outputStream)
                                }
                                writer.write("\n\n")
                                writer.flush()
                            }
                        }
                    }

                    // 写入当前版本活动日志
                    if (persistentFile != null && persistentFile.exists() && persistentFile.length() > 0L) {
                        if (includeHistory) {
                            writer.write("----- [CURRENT VERSION LOG: ${persistentFile.name}] -----\n")
                            writer.flush()
                        }
                        persistentFile.inputStream().use { input ->
                            input.copyTo(outputStream)
                        }
                    } else if (!includeHistory) {
                        writer.write("Total Current Version Entries: 0\n")
                    } else {
                        // persistentFile empty but history was included
                    }
                    Unit
                }
                writer.flush()
            }
        }

        // 清理历史 cache/logs 导出临时文件
        try {
            val cacheLogsDir = File(context.cacheDir, "logs")
            if (cacheLogsDir.exists()) {
                cacheLogsDir.deleteRecursively()
            }
        } catch (_: Exception) {}

        return exportResult
    }
}

typealias ZakoLogger = ZakoLog
typealias NeoLog = ZakoLog
typealias NeoLogger = ZakoLog
