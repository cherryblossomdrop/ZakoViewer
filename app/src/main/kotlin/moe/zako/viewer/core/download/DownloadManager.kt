package moe.zako.viewer.core.download

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.permission.StoragePermissionHelper
import moe.zako.viewer.core.storage.StorageConstants
import moe.zako.viewer.data.local.ZakoDatabase
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.data.source.cosav.CosApiClient
import java.io.File

/**
 * 离线下载全生命周期管理中枢门面 (DownloadManager)
 *
 * 【跨模块门面契约】：
 * 1. 任务调度与状态机核心：统一调度多节点并发下载引擎 MultiNodeDownloader，响应式维护下载任务全生命周期状态机；
 * 2. 前台服务协同：统筹调度 DownloadService 前台服务保活与常驻通知栏刷新，支持暂停、恢复、重下及批量删除任务；
 * 3. 本地完成态文件校验与寻址：提供 findLocalCompletedVideo 与 reconcileLocalDownloadFiles，负责本地磁盘文件校验、对齐与封面离线化；
 * 4. 物理存储迁移委托：对外保留 migrateDownloadsDirectory 兼容契约，底层委托至 DownloadMigrationHelper 实施流式物理搬迁与旧版目录兼容；
 * 5. 协程安全与生命周期：所有耗时 I/O 均调度于 Dispatchers.IO，提供 Flow 响应式数据流，严禁主线程阻塞。
 */
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

    private val migrationHelper = DownloadMigrationHelper(
        downloadDao = downloadDao,
        settingsRepository = settingsRepository,
        onReconcile = { reconcileLocalDownloadFiles() },
        onPauseDownload = { videoId -> pauseDownload(videoId) }
    )

    fun getCoverFile(videoId: String): File = CoverCacheManager.getCoverFile(context, videoId)

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
                    return@withContext LocalVideoInfo(f, record?.title ?: f.nameWithoutExtension, record?.coverUrl ?: "")
                }
            } catch (_: Exception) {}
        }

        // 2. 检查数据库 COMPLETED 记录
        val record = downloadDao.getDownloadByVideoId(videoId)
        if (record != null && record.status == DownloadEntity.STATUS_COMPLETED) {
            val file = File(record.savePath)
            if (file.exists() && file.isFile && file.canRead() && file.length() > 0L) {
                return@withContext LocalVideoInfo(file, record.title, record.coverUrl)
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
                val parsedId = if (fn.contains("-")) fn.substringBeforeLast(".mp4").substringAfterLast("-").trim() else fn.substringBeforeLast(".mp4").trim()
                parsedId.equals(videoId, ignoreCase = true) || fn.contains(videoId)
            }

            if (matchedFile != null && matchedFile.canRead() && matchedFile.length() > 0L) {
                val title = record?.title ?: matchedFile.nameWithoutExtension
                val cover = record?.coverUrl ?: ""
                val duration = record?.duration ?: ""
                val updated = (record ?: DownloadEntity(
                    videoId = videoId,
                    title = title,
                    coverUrl = cover,
                    savePath = matchedFile.absolutePath,
                    totalBytes = matchedFile.length(),
                    downloadedBytes = matchedFile.length(),
                    status = DownloadEntity.STATUS_COMPLETED,
                    createdAt = matchedFile.lastModified(),
                    completedAt = matchedFile.lastModified(),
                    duration = duration
                )).copy(
                    savePath = matchedFile.absolutePath,
                    status = DownloadEntity.STATUS_COMPLETED,
                    totalBytes = matchedFile.length(),
                    downloadedBytes = matchedFile.length(),
                    duration = duration.ifEmpty { record?.duration ?: "" }
                )
                downloadDao.insertOrUpdate(updated)
                ZakoLog.i(TAG, "Found local video in directory scan for $videoId: ${matchedFile.absolutePath}, updated database.")
                return@withContext LocalVideoInfo(matchedFile, updated.title, updated.coverUrl)
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
        if (coverFile.exists() && coverFile.length() > 0L) return@withContext coverFile.absolutePath
        CoverCacheManager.ensureCoverPersisted(context, videoId, sourceUrl)
        if (coverFile.exists() && coverFile.length() > 0L) coverFile.absolutePath else sourceUrl
    }

    val activeDownloadsFlow: Flow<List<DownloadEntity>> = downloadDao.getActiveDownloadsFlow()
    val completedDownloadsFlow: Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloadsFlow()

    /**
     * 响应式暴露当前已在本地完成下载且文件真实有效的视频 ID 集合
     */
    val completedVideoIdsFlow: Flow<Set<String>> = completedDownloadsFlow.map { list ->
        list.asSequence()
            .filter { entity ->
                val file = File(entity.savePath)
                file.exists() && file.isFile && file.length() > 0L
            }
            .map { it.videoId }
            .toSet()
    }

    fun getDownloadStatusFlow(videoId: String): Flow<DownloadEntity?> = downloadDao.getDownloadFlow(videoId)

    fun initialize() {
        scope.launch {
            migrationHelper.migrateLegacyNeoViewerDirectory()
            reconcileLocalDownloadFiles()
            try {
                val list = downloadDao.getAllDownloadsList()
                for (item in list) {
                    if (item.status == DownloadEntity.STATUS_DOWNLOADING) {
                        downloadDao.insertOrUpdate(item.copy(status = DownloadEntity.STATUS_PAUSED, speedBps = 0L))
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

                        val sourceCover = matchedFav?.coverUrl ?: matchedHist?.coverUrl ?: matchedLater?.coverUrl ?: ""
                        val cleanSourceCover = if (sourceCover.startsWith("http://", true) || sourceCover.startsWith("https://", true)) sourceCover else ""
                        val sourceDuration = matchedFav?.duration ?: matchedHist?.duration ?: matchedLater?.duration ?: ""

                        val existing = downloadDao.getDownloadByVideoId(videoId)
                        if (existing == null) {
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
                                completedAt = file.lastModified(),
                                duration = sourceDuration
                            )
                            downloadDao.insertOrUpdate(newEntity)
                            ZakoLog.i(TAG, "Reconciled local video file into downloads: ${file.name} ($videoId)")
                        } else {
                            var updated = existing
                            if (sourceDuration.isNotBlank() && existing.duration.isBlank()) {
                                updated = updated.copy(duration = sourceDuration)
                            }
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
                                updated = updated.copy(coverUrl = cleanSourceCover)
                            }
                            val cand = updated.coverUrl.ifBlank { sourceCover }
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

    private fun canWriteToDir(dir: File): Boolean = DownloadMigrationHelper.canWriteToDir(dir)

    fun getDownloadDir(): File {
        val hasPermission = StoragePermissionHelper.hasAllFilesAccess()
        val customPath = runBlocking {
            try { settingsRepository.downloadPathFlow.first() } catch (_: Exception) { "" }
        }
        if (hasPermission && customPath.isNotBlank()) {
            val customDir = File(customPath)
            if (canWriteToDir(customDir)) return customDir
        }
        val defaultDir = File(StorageConstants.DEFAULT_DOWNLOAD_DIR)
        if (hasPermission && defaultDir.parentFile?.exists() == true && canWriteToDir(defaultDir)) {
            return defaultDir
        }
        val dir = File(context.getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 启动前台下载通知服务
     */
    private fun startForegroundDownloadService(videoId: String, title: String) {
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
    }

    /**
     * 统一绑定多节点下载引擎事件监听与数据库持久化更新
     */
    private suspend fun startMultiNodeDownloadSession(
        videoId: String,
        title: String,
        cdnUrls: List<String>,
        outputFile: File
    ) {
        multiNodeDownloader.startDownload(
            videoId = videoId,
            title = title,
            cdnUrls = cdnUrls,
            outputFile = outputFile,
            onProgress = { downloaded, total, speed, _ ->
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

    /**
     * 安全清理目标视频在本地磁盘上的完整产物及分片中间文件
     */
    private fun deletePhysicalDownloadArtifacts(savePath: String) {
        try {
            val file = File(savePath)
            if (file.exists()) file.delete()
            val partFile = File(file.parentFile, "${file.name}.part")
            if (partFile.exists()) partFile.delete()
            val metaFile = File(file.parentFile, "${file.name}.meta")
            if (metaFile.exists()) metaFile.delete()
        } catch (e: Exception) {
            ZakoLog.w(TAG, "Error deleting physical download artifacts: ${e.message}")
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
            val cleanCoverUrl = if (coverUrl.startsWith("http://", true) || coverUrl.startsWith("https://", true)) coverUrl else ""

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

            startForegroundDownloadService(videoId, title)
            startMultiNodeDownloadSession(videoId, title, cdnUrls, outputFile)
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

            startForegroundDownloadService(videoId, entity.title)
            startMultiNodeDownloadSession(videoId, entity.title, cdnUrls, outputFile)
        }
    }

    fun redownload(videoId: String) {
        multiNodeDownloader.pauseDownload(videoId)
        scope.launch {
            val entity = downloadDao.getDownloadByVideoId(videoId) ?: return@launch
            deletePhysicalDownloadArtifacts(entity.savePath)

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
            downloadDao.getDownloadByVideoId(videoId)?.let { deletePhysicalDownloadArtifacts(it.savePath) }
            downloadDao.deleteByVideoId(videoId)
        }
    }

    fun deleteDownloads(videoIds: List<String>) {
        if (videoIds.isEmpty()) return
        videoIds.forEach { id -> multiNodeDownloader.pauseDownload(id) }
        scope.launch {
            for (id in videoIds) {
                downloadDao.getDownloadByVideoId(id)?.let { deletePhysicalDownloadArtifacts(it.savePath) }
            }
            downloadDao.deleteByVideoIds(videoIds)
        }
    }

    /**
     * 切换下载目录时安全迁移已有视频文件（平滑委托至 DownloadMigrationHelper）
     */
    suspend fun migrateDownloadsDirectory(
        newPath: String,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit
    ): MigrationResult = migrationHelper.migrateDownloadsDirectory(newPath, onProgress)
}
