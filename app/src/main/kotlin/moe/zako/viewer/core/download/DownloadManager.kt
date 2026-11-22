package moe.zako.viewer.core.download

import android.content.Context
import android.content.Intent
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.permission.StoragePermissionHelper
import moe.zako.viewer.core.storage.StorageConstants
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
import java.io.FileOutputStream
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
        return CoverCacheManager.getCoverFile(context, videoId)
    }

    data class LocalVideoInfo(
        val file: File,
        val title: String,
        val coverUrl: String
    )

    /**
     * 统一检测并校验本地是否存在对应 videoId 的完整已下载视频：
     * 1. 优先查校验 candidatePath；
     * 2. 查询 downloads 表中状态为 COMPLETED 的记录并核验文件非空可读；
     * 3. 若失效或未记录，扫描各下载目录寻找并回写更新数据库；
     * 4. 若彻底丢失，更新数据库状态避免误判，返回 null。
     */
    suspend fun findLocalCompletedVideo(videoId: String, candidatePath: String? = null): LocalVideoInfo? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        // 1. 若候选路径非空且文件真实存在可读
        if (!candidatePath.isNullOrBlank()) {
            try {
                val f = File(candidatePath)
                if (f.exists() && f.isFile && f.canRead() && f.length() > 0L) {
                    val record = downloadDao.getDownloadByVideoId(videoId)
                    return@withContext LocalVideoInfo(
                        file = f,
                        title = record?.title ?: f.nameWithoutExtension,
                        coverUrl = record?.coverUrl ?: ""
                    )
                }
            } catch (_: Exception) {}
        }

        // 2. 检查数据库 COMPLETED 记录
        val record = downloadDao.getDownloadByVideoId(videoId)
        if (record != null && record.status == DownloadEntity.STATUS_COMPLETED) {
            val file = File(record.savePath)
            if (file.exists() && file.isFile && file.canRead() && file.length() > 0L) {
                return@withContext LocalVideoInfo(
                    file = file,
                    title = record.title,
                    coverUrl = record.coverUrl
                )
            } else {
                ZakoLog.w(TAG, "Record exists for $videoId with status COMPLETED, but file missing or empty at: ${record.savePath}")
            }
        }

        // 3. 扫描各下载目录挽救寻找
        val targetDir = getDownloadDir()
        val appDir = File(context.getExternalFilesDir(null), "downloads")
        val dirsToScan = listOfNotNull(targetDir, appDir).distinct()

        for (dir in dirsToScan) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4", ignoreCase = true) } ?: emptyList()
            val matchedFile = files.firstOrNull { f ->
                val fn = f.name
                val parsedId = if (fn.contains("-")) {
                    fn.substringBeforeLast(".mp4").substringAfterLast("-").trim()
                } else {
                    fn.substringBeforeLast(".mp4").trim()
                }
                parsedId.equals(videoId, ignoreCase = true) || fn.contains(videoId)
            }

            if (matchedFile != null && matchedFile.canRead() && matchedFile.length() > 0L) {
                val title = record?.title ?: matchedFile.nameWithoutExtension
                val cover = record?.coverUrl ?: ""
                val updated = (record ?: DownloadEntity(
                    videoId = videoId,
                    title = title,
                    coverUrl = cover,
                    savePath = matchedFile.absolutePath,
                    totalBytes = matchedFile.length(),
                    downloadedBytes = matchedFile.length(),
                    status = DownloadEntity.STATUS_COMPLETED,
                    createdAt = matchedFile.lastModified(),
                    completedAt = matchedFile.lastModified()
                )).copy(
                    savePath = matchedFile.absolutePath,
                    status = DownloadEntity.STATUS_COMPLETED,
                    totalBytes = matchedFile.length(),
                    downloadedBytes = matchedFile.length()
                )
                downloadDao.insertOrUpdate(updated)
                ZakoLog.i(TAG, "Found local video in directory scan for $videoId: ${matchedFile.absolutePath}, updated database.")
                return@withContext LocalVideoInfo(
                    file = matchedFile,
                    title = updated.title,
                    coverUrl = updated.coverUrl
                )
            }
        }

        // 4. 若最终未找到有效文件，但数据库仍标为 COMPLETED，则标记为 FAILED 防止持续误判
        if (record != null && record.status == DownloadEntity.STATUS_COMPLETED) {
            ZakoLog.w(TAG, "Local video file for $videoId not found in any download dirs, updating COMPLETED state to FAILED.")
            downloadDao.insertOrUpdate(record.copy(status = DownloadEntity.STATUS_FAILED))
        }

        null
    }

    suspend fun cacheCoverLocally(videoId: String, sourceUrl: String): String = withContext(Dispatchers.IO) {
        if (sourceUrl.isEmpty()) return@withContext ""
        val coverFile = getCoverFile(videoId)
        if (coverFile.exists() && coverFile.length() > 0L) {
            return@withContext coverFile.absolutePath
        }
        CoverCacheManager.ensureCoverPersisted(context, videoId, sourceUrl)
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

                        val existing = downloadDao.getDownloadByVideoId(videoId)
                        if (existing == null) {
                            val cleanSourceCover = if (sourceCover.startsWith("http://", true) || sourceCover.startsWith("https://", true)) {
                                sourceCover
                            } else {
                                ""
                            }
                            CoverCacheManager.ensureCoverPersistedAsync(context, videoId, cleanSourceCover, file.absolutePath)

                            val newEntity = DownloadEntity(
                                videoId = videoId,
                                title = title.ifEmpty { "视频 $videoId" },
                                coverUrl = cleanSourceCover,
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
                            // 净化数据库中的死路径，保留有效网络 URL
                            if (existing.coverUrl.startsWith("/data/user/") || existing.coverUrl.startsWith("/data/data/") ||
                                (existing.coverUrl.startsWith("/") && !File(existing.coverUrl).exists())
                            ) {
                                val cleanUrl = if (sourceCover.startsWith("http://", true) || sourceCover.startsWith("https://", true)) {
                                    sourceCover
                                } else {
                                    ""
                                }
                                updated = updated.copy(coverUrl = cleanUrl)
                            }
                            val cand = if (updated.coverUrl.isNotBlank()) updated.coverUrl else sourceCover
                            CoverCacheManager.ensureCoverPersistedAsync(context, videoId, cand, file.absolutePath)
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
            val newDir = File(StorageConstants.DEFAULT_DOWNLOAD_DIR)

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
        val hasPermission = StoragePermissionHelper.hasAllFilesAccess()
        val customPath = runBlocking {
            try {
                settingsRepository.downloadPathFlow.first()
            } catch (_: Exception) {
                ""
            }
        }
        if (hasPermission && customPath.isNotBlank()) {
            val customDir = File(customPath)
            if (canWriteToDir(customDir)) {
                return customDir
            }
        }
        val defaultDir = File(StorageConstants.DEFAULT_DOWNLOAD_DIR)
        if (hasPermission && defaultDir.parentFile?.exists() == true) {
            if (canWriteToDir(defaultDir)) {
                return defaultDir
            }
        }
        val dir = File(context.getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun canWriteToDir(dir: File): Boolean {
        return try {
            if (!dir.exists() && !dir.mkdirs()) return false
            val testFile = File(dir, ".test_write_${System.currentTimeMillis()}")
            if (testFile.createNewFile()) {
                testFile.delete()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Enqueue a new download task across all available CDN nodes
     */
    fun enqueueDownload(
        videoId: String,
        title: String,
        coverUrl: String,
        cdnUrls: List<String>,
        customStorageDir: File? = null,
        duration: String = ""
    ) {
        scope.launch {
            val targetDir = customStorageDir ?: getDownloadDir()
            val sanitizedName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
            val outputFile = File(targetDir, "$sanitizedName-$videoId.mp4")

            // 保持数据库中存储原始网络 URL，不使用本地沙盒路径覆盖；封面由 CoverCacheManager 统一管理
            val cleanCoverUrl = if (coverUrl.startsWith("http://", true) || coverUrl.startsWith("https://", true)) {
                coverUrl
            } else {
                ""
            }

            val entity = DownloadEntity(
                videoId = videoId,
                title = title,
                coverUrl = cleanCoverUrl,
                savePath = outputFile.absolutePath,
                status = DownloadEntity.STATUS_DOWNLOADING,
                createdAt = System.currentTimeMillis(),
                cdnUrlsJson = cdnUrls.joinToString("\n"),
                duration = duration
            )
            downloadDao.insertOrUpdate(entity)

            // 离线持久化封面到统一目录
            CoverCacheManager.ensureCoverPersistedAsync(context, videoId, coverUrl, outputFile.absolutePath)

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

    /**
     * 切换下载目录时安全迁移已有视频文件
     */
    suspend fun migrateDownloadsDirectory(
        newPath: String,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit
    ): MigrationResult = withContext(Dispatchers.IO) {
        if (newPath.isBlank()) {
            return@withContext MigrationResult(0, 0, "目标路径不能为空")
        }
        val targetDir = File(newPath)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return@withContext MigrationResult(0, 0, "无法创建目标目录，请检查文件写入权限")
        }

        // 1. 检查新目录写入权限
        val testFile = File(targetDir, ".write_test_${System.currentTimeMillis()}")
        val canWrite = try {
            if (testFile.createNewFile()) {
                testFile.delete()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
        if (!canWrite) {
            return@withContext MigrationResult(0, 0, "新目录无写入权限，请重新授权或选择其他目录")
        }

        // 2. 优雅暂停所有正在下载的任务
        val allDownloads = downloadDao.getAllDownloadsList()
        for (d in allDownloads) {
            if (d.status == DownloadEntity.STATUS_DOWNLOADING) {
                pauseDownload(d.videoId)
            }
        }

        // 3. 统计待迁移文件
        val targetCanonical = try { targetDir.canonicalPath } catch (_: Exception) { targetDir.absolutePath }
        val existingFiles = allDownloads.mapNotNull { entity ->
            val f = File(entity.savePath)
            if (f.exists() && f.isFile && f.length() > 0L) {
                entity to f
            } else {
                null
            }
        }.filter { (_, f) ->
            val srcCanonical = try { f.parentFile?.canonicalPath } catch (_: Exception) { f.parentFile?.absolutePath }
            srcCanonical != targetCanonical
        }

        if (existingFiles.isEmpty()) {
            settingsRepository.setDownloadPath(newPath)
            reconcileLocalDownloadFiles()
            return@withContext MigrationResult(0, 0, null)
        }

        // 4. 存储空间检查
        val totalBytesNeeded = existingFiles.sumOf { (_, f) -> f.length() }
        val usableSpace = targetDir.usableSpace
        if (usableSpace in 1 until totalBytesNeeded) {
            val neededMb = totalBytesNeeded / (1024 * 1024)
            val usableMb = usableSpace / (1024 * 1024)
            return@withContext MigrationResult(
                0,
                existingFiles.size,
                "新目录可用存储空间不足（可用: ${usableMb}MB，所需: ${neededMb}MB）"
            )
        }

        // 5. 流式迁移 (统一 64KB 缓冲块)
        var successCount = 0
        val buffer = ByteArray(StorageConstants.DEFAULT_IO_BUFFER_SIZE)
        ZakoLog.i(TAG, "Starting downloads migration to: $newPath. Total files to migrate: ${existingFiles.size}")

        for ((idx, pair) in existingFiles.withIndex()) {
            val (entity, srcFile) = pair
            val currentNumber = idx + 1
            onProgress(currentNumber, existingFiles.size, srcFile.name)
            ZakoLog.i(TAG, "Migrating file [$currentNumber/${existingFiles.size}]: ${srcFile.name} (${srcFile.length()} bytes)")

            val destFile = File(targetDir, srcFile.name)

            // 若新目录已存在同名且大小一致的文件，直接重用并对齐数据库记录
            if (destFile.exists() && destFile.length() == srcFile.length()) {
                downloadDao.updateSavePath(entity.videoId, destFile.absolutePath)
                val deleted = try {
                    srcFile.delete()
                } catch (e: Exception) {
                    ZakoLog.w(TAG, "Exception deleting source file ${srcFile.absolutePath}: ${e.message}")
                    false
                }
                if (!deleted) {
                    ZakoLog.w(TAG, "Failed to delete source file after reuse: ${srcFile.absolutePath}")
                } else {
                    ZakoLog.i(TAG, "Deleted source file successfully (reused): ${srcFile.absolutePath}")
                }
                successCount++
                continue
            }

            val tmpFile = File(targetDir, "${srcFile.name}.migrating_tmp")
            try {
                if (tmpFile.exists()) tmpFile.delete()

                srcFile.inputStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }

                if (tmpFile.exists() && tmpFile.length() == srcFile.length()) {
                    if (destFile.exists()) destFile.delete()
                    if (tmpFile.renameTo(destFile)) {
                        downloadDao.updateSavePath(entity.videoId, destFile.absolutePath)
                        val deleted = try {
                            srcFile.delete()
                        } catch (e: Exception) {
                            ZakoLog.w(TAG, "Exception deleting source file ${srcFile.absolutePath}: ${e.message}")
                            false
                        }
                        if (!deleted) {
                            ZakoLog.w(TAG, "Failed to delete source file after migration: ${srcFile.absolutePath}")
                        } else {
                            ZakoLog.i(TAG, "Deleted source file successfully: ${srcFile.absolutePath}")
                        }
                        successCount++
                    } else {
                        ZakoLog.e(TAG, "Failed to rename temp file to ${destFile.name}")
                        tmpFile.delete()
                    }
                } else {
                    ZakoLog.e(TAG, "Migrated file size mismatch for ${srcFile.name}: expected ${srcFile.length()}, got ${tmpFile.length()}")
                    tmpFile.delete()
                }
            } catch (e: Exception) {
                ZakoLog.e(TAG, "Failed migrating file ${srcFile.name}: ${e.message}", e)
                try { if (tmpFile.exists()) tmpFile.delete() } catch (_: Exception) {}
            }
        }

        ZakoLog.i(TAG, "Migration completed: $successCount/${existingFiles.size} files migrated successfully to $newPath")

        // 6. 更新配置并刷新
        settingsRepository.setDownloadPath(newPath)
        reconcileLocalDownloadFiles()

        MigrationResult(successCount, existingFiles.size, null)
    }
}

data class MigrationResult(
    val successCount: Int,
    val totalCount: Int,
    val errorMessage: String? = null
)
