package moe.zako.viewer.core.download

import android.content.Context
import android.content.Intent
import moe.zako.viewer.core.logger.NeoLog
import moe.zako.viewer.data.local.NeoDatabase
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

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

    private val db = NeoDatabase.getInstance(context)
    private val downloadDao = db.downloadDao()
    private val multiNodeDownloader = MultiNodeDownloader()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val settingsRepository = SettingsRepository(context)

    val activeDownloadsFlow: Flow<List<DownloadEntity>> = downloadDao.getActiveDownloadsFlow()
    val completedDownloadsFlow: Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloadsFlow()

    fun getDownloadStatusFlow(videoId: String): Flow<DownloadEntity?> = downloadDao.getDownloadFlow(videoId)

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

            val entity = DownloadEntity(
                videoId = videoId,
                title = title,
                coverUrl = coverUrl,
                savePath = outputFile.absolutePath,
                status = DownloadEntity.STATUS_DOWNLOADING,
                createdAt = System.currentTimeMillis()
            )
            downloadDao.insertOrUpdate(entity)

            // Start foreground service
            try {
                val serviceIntent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_START_DOWNLOAD
                    putExtra("videoId", videoId)
                    putExtra("title", title)
                }
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                NeoLog.w(TAG, "Could not start foreground service: ${e.message}")
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
            }
            downloadDao.deleteByVideoId(videoId)
        }
    }
}
