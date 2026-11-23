package moe.zako.viewer

import android.app.Application
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.core.duration.VideoDurationManager
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.core.icon.AppIconManager
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.network.NetworkClientProvider
import moe.zako.viewer.data.local.ZakoDatabase
import moe.zako.viewer.data.repository.FavoriteRepository
import moe.zako.viewer.data.repository.HistoryRepository
import moe.zako.viewer.data.repository.SearchHistoryRepository
import moe.zako.viewer.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ZakoApplication : Application(), ImageLoaderFactory {
    companion object {
        private const val TAG = "ZakoApplication"
        lateinit var instance: ZakoApplication
            private set
    }

    val database: ZakoDatabase by lazy { ZakoDatabase.getInstance(this) }
    val favoriteRepository: FavoriteRepository by lazy { FavoriteRepository(database.favoriteDao()) }
    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.watchHistoryDao(), database.watchLaterDao(), favoriteRepository)
    }
    val searchHistoryRepository: SearchHistoryRepository by lazy {
        SearchHistoryRepository(database.searchHistoryDao())
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val downloadManager: DownloadManager by lazy { DownloadManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ZakoLog.init(this)
        setupCrashHandler()
        ZakoLog.i(TAG, "ZakoViewer Application started")
        // 同步应用图标别名（重启冷启动时生效）
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppIconManager.syncIcon(this@ZakoApplication, settingsRepository)
            } catch (e: Exception) {
                ZakoLog.e(TAG, "Failed to sync app icon alias", e)
            }
        }
        // 预建根收藏夹：必须存在，即使用户从未创建过
        CoroutineScope(Dispatchers.IO).launch {
            try {
                favoriteRepository.ensureDefaultRootFolderExists()
            } catch (e: Exception) {
                ZakoLog.e(TAG, "Failed to ensure default root folder", e)
            }
        }
        // 初始化下载任务状态，清理异常中断残留
        downloadManager.initialize()
        // 迁移旧版离线封面并全量检查自动补全
        CoverCacheManager.migrateLegacyDownloadCovers(this)
        CoverCacheManager.syncMissingCovers(this)
        // 存量空时长静默自愈补齐
        VideoDurationManager.syncMissingDurations(this, settingsRepository)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(NetworkClientProvider.imageClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // 崩溃前同步落盘：在当前线程以同步方式将 [FATAL] 时间戳与完整堆栈追加写入 zako_persistent.log 并 flush()
                ZakoLog.logFatalSync("CrashHandler", throwable)

                val logsDir = getExternalFilesDir("logs") ?: File(filesDir, "logs")
                if (!logsDir.exists()) {
                    logsDir.mkdirs()
                }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val crashFile = File(logsDir, "ZakoViewer_crash_${timeStamp}.txt")

                val sb = StringBuilder()
                sb.append("=== ZakoViewer Crash Report ===\n")
                sb.append("Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())).append("\n")
                sb.append("Thread: ").append(thread.name).append(" (id=").append(thread.id).append(")\n")
                sb.append("App Version: ").append(BuildConfig.VERSION_NAME).append(" (code=").append(BuildConfig.VERSION_CODE).append(")\n")
                sb.append("Device Manufacturer: ").append(Build.MANUFACTURER).append("\n")
                sb.append("Device Model: ").append(Build.MODEL).append("\n")
                sb.append("Device Brand: ").append(Build.BRAND).append("\n")
                sb.append("Android Version: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                sb.append("CPU ABI: ").append(Build.SUPPORTED_ABIS?.joinToString(", ") ?: "unknown").append("\n\n")
                sb.append("=== Stack Trace ===\n")
                sb.append(Log.getStackTraceString(throwable)).append("\n\n")

                sb.append("=== Recent In-Memory Logs ===\n")
                val recentLogs = ZakoLog.logsFlow.value.takeLast(50)
                for (entry in recentLogs) {
                    sb.append(entry.formatted()).append("\n")
                }

                crashFile.writeText(sb.toString())
                ZakoLog.e(TAG, "Uncaught exception logged to: ${crashFile.absolutePath}", throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist crash report", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}

typealias NeoApplication = ZakoApplication
