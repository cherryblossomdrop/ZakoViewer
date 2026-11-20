package moe.zako.viewer.core.download

import android.content.Context
import android.content.Intent
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.data.local.ZakoDatabase
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.data.source.cosav.CosApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "DownloadManager"
        @Volatile
        private var instance: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val db = ZakoDatabase.getInstance(context)
    private val downloadDao = db.downloadDao()
    private val multiNodeDownloader = MultiNodeDownloader()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val settingsRepository = SettingsRepository(context)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getCoverFile(videoId: String): File {
        val coversDir = File(context.filesDir, "download_covers")
        if (!coversDir.exists()) coversDir.mkdirs()
        return File(coversDir, "$videoId.jpg")
    }

    suspend fun cacheCoverLocally(videoId: String, sourceUrl: String): String = withContext(Dispatchers.IO) {
        if (sourceUrl.isEmpty()) return@withContext ""
        val coverFile = getCoverFile(videoId)
        if (coverFile.exists() && coverFile.length() > 0L) {
            return@withContext coverFile.absolutePath
        }
        if (File(sourceUrl).exists()) {
            return@withContext sourceUrl
        }
        if (sourceUrl.startsWith("http://", ignoreCase = true) || sourceUrl.startsWith("https://", ignoreCase = true)) {
            try {
                val req = Request.Builder()
                    .url(sourceUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            coverFile.writeBytes(bytes)
                        }
                    }
                }
            } catch (e: Exception) {
                ZakoLog.w(TAG, "Failed to download cover for $videoId: ${e.message}")
            }
        }
        if (coverFile.exists() && coverFile.length() > 0L) coverFile.absolutePath else sourceUrl
    }

    val activeDownloadsFlow: Flow<List<DownloadEntity>> = downloadDao.getActiveDownloadsFlow()
    val completedDownloadsFlow: Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloadsFlow()

    fun getDownloadStatusFlow(videoId: String): Flow<DownloadEntity?> = downloadDao.getDownloadFlow(videoId)

    fun initialize() {
        scope.launch {
            migrateLegacyNeoViewerDirectory()
            reconcileLocalDownloadFiles()
            try {
                val list = downloadDao.getAllDownloadsList()
                for (item in list) {
                    if (item.status == DownloadEntity.STATUS_DOWNLOADING) {
                        downloadDao.insertOrUpdate(
                            item.copy(
                                status = DownloadEntity.STATUS_PAUSED,
                                speedBps = 0L
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                ZakoLog.w(TAG, "Error initializing download states: ${e.message}")
            }
        }
    }

    /**
     * 自动扫描并对齐本地已下载的视频文件到 Room 数据库中，并在启动时检测已删除的文件与本地封面缓存
     */
    fun reconcileLocalDownloadFiles() {
        scope.launch {
            try {
                val targetDir = getDownloadDir()
                val appDir = File(context.getExternalFilesDir(null), "downloads")
                val dirsToScan = listOfNotNull(targetDir, appDir).distinct()

                val allFavorites = db.favoriteDao().getAllItemsList()
                val allHistory = db.watchHistoryDao().getAllHistoryList()
                val allWatchLater = db.watchLaterDao().getAllList()

                // 1. 扫描磁盘上存在的 .mp4 文件，与 Room 数据库对齐并确保封面离线化
                for (dir in dirsToScan) {
                    if (!dir.exists() || !dir.isDirectory) continue
                    val videoFiles = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4", ignoreCase = true) } ?: emptyList()
                    for (file in videoFiles) {
                        val fileName = file.name
                        val videoId = if (fileName.contains("-")) {
                            fileName.substringBeforeLast(".mp4").substringAfterLast("-").trim()
                        } else {
                            fileName.substringBeforeLast(".mp4").trim()
                        }
                        if (videoId.isEmpty()) continue

                        val existing = downloadDao.getDownloadByVideoId(videoId)
                        val cachedCover = getCoverFile(videoId)
                        if (existing == null) {
                            val matchedFav = allFavorites.find { it.videoId == videoId }
                            val matchedHist = allHistory.find { it.videoId == videoId }
                            val matchedLater = allWatchLater.find { it.videoId == videoId }

                            val title = matchedFav?.title
                                ?: matchedHist?.title
                                ?: matchedLater?.title
                                ?: if (fileName.contains("-")) fileName.substringBeforeLast(".mp4").substringBeforeLast("-").trim() else fileName.substringBeforeLast(".mp4").trim()

                            val sourceCover = matchedFav?.coverUrl
                                ?: matchedHist?.coverUrl
                                ?: matchedLater?.coverUrl
                                ?: ""

                            val coverUrl = if (cachedCover.exists() && cachedCover.length() > 0L) {
                                cachedCover.absolutePath
                            } else {
                                if (sourceCover.isNotEmpty()) {
                                    scope.launch { cacheCoverLocally(videoId, sourceCover) }
                                }
                                sourceCover
                            }

                            val newEntity = DownloadEntity(
                                videoId = videoId,
                                title = title.ifEmpty { "视频 $videoId" },
                                coverUrl = coverUrl,
                                savePath = file.absolutePath,
                                totalBytes = file.length(),
                                downloadedBytes = file.length(),
                                status = DownloadEntity.STATUS_COMPLETED,
                                createdAt = file.lastModified(),
                                completedAt = file.lastModified()
                            )
                            downloadDao.insertOrUpdate(newEntity)
                            ZakoLog.i(TAG, "Reconciled local video file into downloads: ${file.name} ($videoId)")
                        } else {
                            var updated = existing
                            if (existing.status != DownloadEntity.STATUS_COMPLETED || existing.savePath != file.absolutePath) {
                                updated = updated.copy(
                                    savePath = file.absolutePath,
                                    status = DownloadEntity.STATUS_COMPLETED,
                                    totalBytes = file.length(),
                                    downloadedBytes = file.length(),
                                    completedAt = existing.completedAt ?: file.lastModified()
                                )
                            }
                            if (cachedCover.exists() && cachedCover.length() > 0L && existing.coverUrl != cachedCover.absolutePath) {
                                updated = updated.copy(coverUrl = cachedCover.absolutePath)
                            } else if (!cachedCover.exists() && existing.coverUrl.isNotBlank()) {
                                scope.launch { cacheCoverLocally(videoId, existing.coverUrl) }
                            }
                            if (updated != existing) {
                                downloadDao.insertOrUpdate(updated)
                            }
                        }
                    }
                }

                // 2. 扫描 Room 数据库中所有已下载任务，检查磁盘文件是否依然存在
                val allCompleted = downloadDao.getAllDownloadsList().filter { it.status == DownloadEntity.STATUS_COMPLETED }
                for (item in allCompleted) {
                    val file = File(item.savePath)
                    if (!file.exists() || file.length() == 0L) {
                        // 尝试从各下载目录根据 videoId 寻找是否被移动或重命名
                        val movedFile = dirsToScan.asSequence()
                            .flatMap { d -> d.listFiles()?.asSequence() ?: emptySequence() }
                            .firstOrNull { f -> f.isFile && f.name.endsWith(".mp4", ignoreCase = true) && f.name.contains(item.videoId) }
                        if (movedFile != null) {
                            downloadDao.updateSavePath(item.videoId, movedFile.absolutePath)
                            ZakoLog.i(TAG, "Updated savePath for moved video ${item.videoId} -> ${movedFile.absolutePath}")
                        } else {
                            ZakoLog.w(TAG, "Downloaded video file missing from disk: ${item.savePath} for ${item.videoId}")
                        }
                    }
                }
            } catch (e: Exception) {
                ZakoLog.w(TAG, "reconcileLocalDownloadFiles error: ${e.message}")
            }
        }
    }

    private suspend fun migrateLegacyNeoViewerDirectory() = withContext(Dispatchers.IO) {
        try {
            val oldDir = File("/storage/emulated/0/Download/NeoViewer")
            val newDir = File("/storage/emulated/0/Download/ZakoViewer")

            // 1. 如果旧目录存在且包含文件，迁移文件到新目录
            if (oldDir.exists() && oldDir.isDirectory) {
                if (!newDir.exists()) newDir.mkdirs()
                val oldFiles = oldDir.listFiles() ?: emptyArray()
                for (oldFile in oldFiles) {
                    val newFile = File(newDir, oldFile.name)
                    if (!newFile.exists()) {
                        oldFile.renameTo(newFile)
                    }
                }
                if (oldDir.listFiles()?.isEmpty() == true) {
                    oldDir.delete()
                }
            }

            // 2. 同步更新数据库中保存的旧路径
            val allDownloads = downloadDao.getAllDownloadsList()
            for (item in allDownloads) {
                if (item.savePath.contains("/Download/NeoViewer/")) {
                    val updatedPath = item.savePath.replace("/Download/NeoViewer/", "/Download/ZakoViewer/")
                    downloadDao.insertOrUpdate(item.copy(savePath = updatedPath))
                }
            }

            // 3. 检查并迁移设置中的自定义下载路径
            val currentSettingPath = try { settingsRepository.downloadPathFlow.first() } catch (_: Exception) { "" }
            if (currentSettingPath.contains("/Download/NeoViewer")) {
                val updatedSettingPath = currentSettingPath.replace("/Download/NeoViewer", "/Download/ZakoViewer")
                settingsRepository.setDownloadPath(updatedSettingPath)
            }
        } catch (e: Exception) {
            ZakoLog.w(TAG, "Legacy NeoViewer directory migration warning: ${e.message}")
        }
    }

    fun getDownloadDir(): File {
        val customPath = runBlocking {
            try {
                settingsRepository.downloadPathFlow.first()
            } catch (_: Exception) {
                ""
            }
        }
        if (customPath.isNotBlank()) {
            val customDir = File(customPath)
            if (customDir.exists() || customDir.mkdirs()) {
                return customDir
            }
        }
        val defaultDir = File("/storage/emulated/0/Download/ZakoViewer")
        if (defaultDir.parentFile?.exists() == true && (defaultDir.exists() || defaultDir.mkdirs())) {
            return defaultDir
        }
        val dir = File(context.getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Enqueue a new download task across all available CDN nodes
     */
    fun enqueueDownload(
        videoId: String,
        title: String,
        coverUrl: String,
        cdnUrls: List<String>,
        customStorageDir: File? = null
    ) {
        scope.launch {
            val targetDir = customStorageDir ?: getDownloadDir()
            val sanitizedName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
            val outputFile = File(targetDir, "$sanitizedName-$videoId.mp4")

            val cachedCover = getCoverFile(videoId)
            val initialCover = if (cachedCover.exists() && cachedCover.length() > 0L) {
                cachedCover.absolutePath
            } else {
                coverUrl
            }

            val entity = DownloadEntity(
                videoId = videoId,
                title = title,
                coverUrl = initialCover,
                savePath = outputFile.absolutePath,
                status = DownloadEntity.STATUS_DOWNLOADING,
                createdAt = System.currentTimeMillis(),
                cdnUrlsJson = cdnUrls.joinToString("\n")
            )
            downloadDao.insertOrUpdate(entity)

            // 离线缓存封面到本地存储
            if (!cachedCover.exists() && coverUrl.isNotBlank()) {
                scope.launch {
                    val localCover = cacheCoverLocally(videoId, coverUrl)
                    if (localCover.isNotEmpty() && File(localCover).exists()) {
                        val current = downloadDao.getDownloadByVideoId(videoId)
                        if (current != null) {
                            downloadDao.insertOrUpdate(current.copy(coverUrl = localCover))
                        }
                    }
                }
            }

            // Start foreground service
            try {
                val serviceIntent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_START_DOWNLOAD
                    putExtra("videoId", videoId)
                    putExtra("title", title)
                }
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                ZakoLog.w(TAG, "Could not start foreground service: ${e.message}")
            }

            multiNodeDownloader.startDownload(
                videoId = videoId,
                title = title,
                cdnUrls = cdnUrls,
                outputFile = outputFile,
                onProgress = { downloaded, total, speed, activeNodes ->
                    scope.launch {
                        downloadDao.updateProgress(
                            videoId = videoId,
                            downloaded = downloaded,
                            total = total,
                            speed = speed,
                            status = DownloadEntity.STATUS_DOWNLOADING
                        )
                    }
                },
                onComplete = { file ->
                    scope.launch {
                        val current = downloadDao.getDownloadByVideoId(videoId)
                        if (current != null) {
                            downloadDao.insertOrUpdate(
                                current.copy(
                                    status = DownloadEntity.STATUS_COMPLETED,
                                    downloadedBytes = file.length(),
                                    totalBytes = file.length(),
                                    speedBps = 0L,
                                    completedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                },
                onError = { err ->
                    scope.launch {
                        val current = downloadDao.getDownloadByVideoId(videoId)
                        if (current != null) {
                            downloadDao.insertOrUpdate(
                                current.copy(
                                    status = DownloadEntity.STATUS_FAILED,
                                    errorMessage = err.message,
                                    speedBps = 0L
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    fun resumeDownload(videoId: String) {
        if (multiNodeDownloader.isDownloading(videoId)) return
        scope.launch {
            val entity = downloadDao.getDownloadByVideoId(videoId) ?: return@launch
            val outputFile = File(entity.savePath)
            var cdnUrls = entity.cdnUrlsJson.split("\n").filter { it.isNotBlank() }
            if (cdnUrls.isEmpty()) {
                try {
                    val detail = CosApiClient.instance.getVideoDetail(videoId)
                    cdnUrls = detail?.allMp4Lines?.ifEmpty { detail.allVideoLines } ?: emptyList()
                } catch (e: Exception) {
                    ZakoLog.w(TAG, "Could not fetch video detail to resume $videoId: ${e.message}")
                }
            }

            if (cdnUrls.isEmpty()) {
                downloadDao.insertOrUpdate(
                    entity.copy(
                        status = DownloadEntity.STATUS_FAILED,
                        errorMessage = "未找到可用的下载线路，请检查网络后重试",
                        speedBps = 0L
                    )
                )
                return@launch
            }

            downloadDao.insertOrUpdate(
                entity.copy(
                    status = DownloadEntity.STATUS_DOWNLOADING,
                    cdnUrlsJson = cdnUrls.joinToString("\n"),
                    errorMessage = null,
                    speedBps = 0L
                )
            )

            // Start foreground service
            try {
                val serviceIntent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_START_DOWNLOAD
                    putExtra("videoId", videoId)
                    putExtra("title", entity.title)
                }
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                ZakoLog.w(TAG, "Could not start foreground service: ${e.message}")
            }

            multiNodeDownloader.startDownload(
                videoId = videoId,
                title = entity.title,
                cdnUrls = cdnUrls,
                outputFile = outputFile,
                onProgress = { downloaded, total, speed, activeNodes ->
                    scope.launch {
                        downloadDao.updateProgress(
                            videoId = videoId,
                            downloaded = downloaded,
                            total = total,
                            speed = speed,
                            status = DownloadEntity.STATUS_DOWNLOADING
                        )
                    }
                },
                onComplete = { file ->
                    scope.launch {
                        val current = downloadDao.getDownloadByVideoId(videoId)
                        if (current != null) {
                            downloadDao.insertOrUpdate(
                                current.copy(
                                    status = DownloadEntity.STATUS_COMPLETED,
                                    downloadedBytes = file.length(),
                                    totalBytes = file.length(),
                                    speedBps = 0L,
                                    completedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                },
                onError = { err ->
                    scope.launch {
                        val current = downloadDao.getDownloadByVideoId(videoId)
                        if (current != null) {
                            downloadDao.insertOrUpdate(
                                current.copy(
                                    status = DownloadEntity.STATUS_FAILED,
                                    errorMessage = err.message,
                                    speedBps = 0L
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    fun redownload(videoId: String) {
        multiNodeDownloader.pauseDownload(videoId)
        scope.launch {
            val entity = downloadDao.getDownloadByVideoId(videoId) ?: return@launch
            val outputFile = File(entity.savePath)
            if (outputFile.exists()) outputFile.delete()
            val partFile = File(outputFile.parentFile, "${outputFile.name}.part")
            if (partFile.exists()) partFile.delete()
            val metaFile = File(outputFile.parentFile, "${outputFile.name}.meta")
            if (metaFile.exists()) metaFile.delete()

            downloadDao.insertOrUpdate(
                entity.copy(
                    downloadedBytes = 0L,
                    speedBps = 0L,
                    status = DownloadEntity.STATUS_PENDING,
                    errorMessage = null
                )
            )
            resumeDownload(videoId)
        }
    }

    fun pauseDownload(videoId: String) {
        multiNodeDownloader.pauseDownload(videoId)
        scope.launch {
            downloadDao.updateStatus(videoId, DownloadEntity.STATUS_PAUSED)
        }
    }

    fun deleteDownload(videoId: String) {
        multiNodeDownloader.pauseDownload(videoId)
        scope.launch {
            val entity = downloadDao.getDownloadByVideoId(videoId)
            entity?.let {
                val file = File(it.savePath)
                if (file.exists()) file.delete()
                val partFile = File(file.parentFile, "${file.name}.part")
                if (partFile.exists()) partFile.delete()
                val metaFile = File(file.parentFile, "${file.name}.meta")
                if (metaFile.exists()) metaFile.delete()
            }
            downloadDao.deleteByVideoId(videoId)
        }
    }

    fun deleteDownloads(videoIds: List<String>) {
        if (videoIds.isEmpty()) return
        videoIds.forEach { id ->
            multiNodeDownloader.pauseDownload(id)
        }
        scope.launch {
            for (id in videoIds) {
                val entity = downloadDao.getDownloadByVideoId(id)
                entity?.let {
                    val file = File(it.savePath)
                    if (file.exists()) file.delete()
                    val partFile = File(file.parentFile, "${file.name}.part")
                    if (partFile.exists()) partFile.delete()
                    val metaFile = File(file.parentFile, "${file.name}.meta")
                    if (metaFile.exists()) metaFile.delete()
                }
            }
            downloadDao.deleteByVideoIds(videoIds)
        }
    }
}
