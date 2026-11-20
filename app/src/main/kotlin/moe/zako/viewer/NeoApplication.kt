package moe.zako.viewer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.core.icon.AppIconManager
import moe.zako.viewer.core.logger.NeoLog
import moe.zako.viewer.data.local.NeoDatabase
import moe.zako.viewer.data.repository.FavoriteRepository
import moe.zako.viewer.data.repository.HistoryRepository
import moe.zako.viewer.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NeoApplication : Application(), ImageLoaderFactory {
    companion object {
        private const val TAG = "NeoApplication"
        lateinit var instance: NeoApplication
            private set
    }

    val database: NeoDatabase by lazy { NeoDatabase.getInstance(this) }
    val favoriteRepository: FavoriteRepository by lazy { FavoriteRepository(database.favoriteDao()) }
    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.watchHistoryDao(), database.watchLaterDao(), favoriteRepository)
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val downloadManager: DownloadManager by lazy { DownloadManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NeoLog.i(TAG, "ZakoViewer Application started")
        // 同步应用图标别名（重启冷启动时生效）
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppIconManager.syncIcon(this@NeoApplication, settingsRepository)
            } catch (e: Exception) {
                NeoLog.e(TAG, "Failed to sync app icon alias", e)
            }
        }
        // 预建根收藏夹：必须存在，即使用户从未创建过
        CoroutineScope(Dispatchers.IO).launch {
            try {
                favoriteRepository.ensureDefaultRootFolderExists()
            } catch (e: Exception) {
                NeoLog.e(TAG, "Failed to ensure default root folder", e)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/120.0.0.0")
                    .header("Referer", "https://api.cos-dkop.net/")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
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
}
