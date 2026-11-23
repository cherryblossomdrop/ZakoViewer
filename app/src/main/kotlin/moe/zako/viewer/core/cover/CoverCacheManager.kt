package moe.zako.viewer.core.cover

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.network.NetworkClientProvider
import moe.zako.viewer.core.network.isHttpUrl
import moe.zako.viewer.core.storage.StorageConstants
import moe.zako.viewer.data.local.ZakoDatabase
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.data.source.DataSourceRegistry
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object CoverCacheManager {
    private const val TAG = "CoverCacheManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadSemaphore = Semaphore(3)
    private val inFlightTasks = ConcurrentHashMap<String, Deferred<Boolean>>()

    private val httpClient: OkHttpClient = NetworkClientProvider.coverCacheClient

    fun getCoversDir(context: Context): File {
        val dir = File(context.filesDir, "covers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCoverFile(context: Context, videoId: String): File {
        return File(getCoversDir(context), "$videoId.jpg")
    }

    fun hasValidCover(context: Context, videoId: String): Boolean {
        if (videoId.isBlank()) return false
        val file = getCoverFile(context, videoId)
        return file.exists() && file.length() > 0L
    }

    /**
     * UI 渲染封面统一入口。
     * 若本地持久化封面存在且有效（>0 字节），优先返回本地 File；
     * 若本地无缓存且存在有效网络 URL，回退返回网络 URL；
     * 仅当 [autoFetch] 为 true 时，才异步触发后台持久化保存至 covers 目录（仅限收藏夹、下载列表、稍后再看、历史记录 4 个来源触发）。
     * 首页推荐流、搜索列表等普通视频卡片默认 autoFetch = false，走常规图片缓存（Coil），避免无限制持久化和备份包膨胀。
     */
    fun getCoverModel(
        context: Context,
        videoId: String?,
        fallbackUrl: String?,
        autoFetch: Boolean = false
    ): Any? {
        val safeId = videoId?.trim() ?: ""
        if (safeId.isNotEmpty() && hasValidCover(context, safeId)) {
            return getCoverFile(context, safeId)
        }

        val safeFallback = fallbackUrl?.trim() ?: ""
        val isNetworkFallback = safeFallback.isHttpUrl()

        if (autoFetch && safeId.isNotEmpty()) {
            ensureCoverPersistedAsync(context, safeId, if (isNetworkFallback) safeFallback else null)
        }

        return when {
            isNetworkFallback -> safeFallback
            safeFallback.isNotEmpty() && !safeFallback.startsWith("/data/user/") && File(safeFallback).exists() -> File(safeFallback)
            else -> null
        }
    }

    /**
     * 异步触发封面持久化（去重且防并发抖动）
     */
    fun ensureCoverPersistedAsync(
        context: Context,
        videoId: String,
        candidateUrl: String? = null,
        localVideoPath: String? = null
    ) {
        if (videoId.isBlank()) return
        val appContext = context.applicationContext
        inFlightTasks.compute(videoId) { _, existing ->
            if (existing != null && existing.isActive) {
                existing
            } else {
                scope.async {
                    try {
                        doEnsureCoverPersisted(appContext, videoId, candidateUrl, localVideoPath)
                    } catch (e: Exception) {
                        ZakoLog.w(TAG, "Error in async cover persist for $videoId: ${e.message}")
                        false
                    } finally {
                        inFlightTasks.remove(videoId)
                    }
                }
            }
        }
    }

    /**
     * 挂起式确保封面持久化落盘，严格按照 4 步回退优先级链执行并去重。
     */
    suspend fun ensureCoverPersisted(
        context: Context,
        videoId: String,
        candidateUrl: String? = null,
        localVideoPath: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext false
        if (hasValidCover(context, videoId)) return@withContext true

        val appContext = context.applicationContext
        val deferred = inFlightTasks.compute(videoId) { _, existing ->
            if (existing != null && existing.isActive) {
                existing
            } else {
                scope.async {
                    try {
                        doEnsureCoverPersisted(appContext, videoId, candidateUrl, localVideoPath)
                    } catch (e: Exception) {
                        ZakoLog.w(TAG, "Error in cover persist for $videoId: ${e.message}")
                        false
                    } finally {
                        inFlightTasks.remove(videoId)
                    }
                }
            }
        }
        deferred?.await() ?: false
    }

    private suspend fun doEnsureCoverPersisted(
        context: Context,
        videoId: String,
        candidateUrl: String?,
        localVideoPath: String?
    ): Boolean {
        if (hasValidCover(context, videoId)) return true
        val targetFile = getCoverFile(context, videoId)

        // 优先级 1: 当前记录自身的有效 HTTP(S) URL
        val candidate = candidateUrl?.trim()
        if (candidate.isHttpUrl()) {
            val ok = downloadSemaphore.withPermit {
                downloadUrlToFile(candidate, targetFile)
            }
            if (ok && hasValidCover(context, videoId)) {
                ZakoLog.d(TAG, "Persisted cover for $videoId via candidateUrl")
                return true
            }
        }

        // 优先级 2: 跨表查询其余 3 张表中的有效 HTTP(S) coverUrl
        val db = ZakoDatabase.getInstance(context)
        val crossTableUrl = findHttpCoverUrlAcrossTables(db, videoId)
        if (!crossTableUrl.isNullOrEmpty()) {
            val ok = downloadSemaphore.withPermit {
                downloadUrlToFile(crossTableUrl, targetFile)
            }
            if (ok && hasValidCover(context, videoId)) {
                ZakoLog.d(TAG, "Persisted cover for $videoId via cross-table lookup ($crossTableUrl)")
                return true
            }
        }

        // 优先级 3: 调用视频详情接口获取最新封面并回填数据库
        try {
            val settingsRepo = SettingsRepository(context)
            val activeSource = DataSourceRegistry.getActiveDataSource(settingsRepo)
            if (activeSource != null) {
                val detail = downloadSemaphore.withPermit {
                    activeSource.getVideoDetail(videoId)
                }
                val netCover = detail?.displayCover?.trim()
                if (netCover.isHttpUrl()) {
                    val ok = downloadSemaphore.withPermit {
                        downloadUrlToFile(netCover, targetFile)
                    }
                    if (ok && hasValidCover(context, videoId)) {
                        ZakoLog.i(TAG, "Persisted cover for $videoId via API detail and backfilled DB ($netCover)")
                        // 顺带回填数据库里的 coverUrl
                        db.favoriteDao().updateCoverUrl(videoId, netCover)
                        db.downloadDao().updateCoverUrl(videoId, netCover)
                        db.watchLaterDao().updateCoverUrl(videoId, netCover)
                        db.watchHistoryDao().updateCoverUrl(videoId, netCover)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            ZakoLog.w(TAG, "API detail query failed for cover $videoId: ${e.message}")
        }

        // 优先级 4: 若该视频存在本地视频文件，使用 MediaMetadataRetriever 截取一帧压缩为 JPEG
        val videoFile = findLocalVideoFile(context, db, videoId, localVideoPath)
        if (videoFile != null && videoFile.exists() && videoFile.length() > 0L) {
            val ok = extractFrameFromVideoFile(videoFile, targetFile)
            if (ok && hasValidCover(context, videoId)) {
                ZakoLog.i(TAG, "Persisted cover for $videoId via video frame extraction (${videoFile.name})")
                return true
            }
        }

        return false
    }

    private suspend fun findHttpCoverUrlAcrossTables(db: ZakoDatabase, videoId: String): String? {
        val fav = db.favoriteDao().getItemByVideoId(videoId)?.coverUrl?.trim()
        if (isValidHttpUrl(fav)) return fav

        val dl = db.downloadDao().getDownloadByVideoId(videoId)?.coverUrl?.trim()
        if (isValidHttpUrl(dl)) return dl

        val wl = db.watchLaterDao().getByVideoId(videoId)?.coverUrl?.trim()
        if (isValidHttpUrl(wl)) return wl

        val wh = db.watchHistoryDao().getHistoryByVideoId(videoId)?.coverUrl?.trim()
        if (isValidHttpUrl(wh)) return wh

        return null
    }

    fun isValidHttpUrl(url: String?): Boolean = url.isHttpUrl()

    private suspend fun findLocalVideoFile(
        context: Context,
        db: ZakoDatabase,
        videoId: String,
        candidatePath: String?
    ): File? {
        if (!candidatePath.isNullOrBlank()) {
            val f = File(candidatePath)
            if (f.exists() && f.isFile && f.length() > 0L) return f
        }

        val dlEntity = db.downloadDao().getDownloadByVideoId(videoId)
        if (dlEntity != null && dlEntity.savePath.isNotBlank()) {
            val f = File(dlEntity.savePath)
            if (f.exists() && f.isFile && f.length() > 0L) return f
        }

        // 扫描应用专属下载目录
        val appDownloads = File(context.getExternalFilesDir(null), "downloads")
        if (appDownloads.exists() && appDownloads.isDirectory) {
            val matched = appDownloads.listFiles()?.firstOrNull {
                it.isFile && it.name.endsWith(".mp4", ignoreCase = true) &&
                        (it.name.contains("-$videoId.mp4") || it.name == "$videoId.mp4")
            }
            if (matched != null && matched.length() > 0L) return matched
        }

        return null
    }

    private fun extractFrameFromVideoFile(videoFile: File, targetFile: File): Boolean {
        val retriever = MediaMetadataRetriever()
        val tmpFile = File(targetFile.parentFile, ".tmp_frame_${targetFile.name}_${System.currentTimeMillis()}")
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (bitmap != null) {
                tmpFile.outputStream().use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                    fos.flush()
                }
                bitmap.recycle()
                if (tmpFile.exists() && tmpFile.length() > 0L) {
                    if (targetFile.exists()) targetFile.delete()
                    return tmpFile.renameTo(targetFile)
                }
            }
        } catch (e: Exception) {
            ZakoLog.w(TAG, "extractFrameFromVideoFile failed for ${videoFile.name}: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { if (tmpFile.exists()) tmpFile.delete() } catch (_: Exception) {}
        }
        return false
    }

    private suspend fun downloadUrlToFile(url: String, targetFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!isValidHttpUrl(url)) return@withContext false
        val tmpFile = File(targetFile.parentFile, ".tmp_${targetFile.name}_${System.currentTimeMillis()}")
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/120.0.0.0")
                .header("Referer", "https://api.cos-dkop.net/")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                body.byteStream().use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output, StorageConstants.STREAM_IO_BUFFER_SIZE)
                        output.flush()
                    }
                }
            }
            if (tmpFile.exists() && tmpFile.length() > 0L) {
                if (targetFile.exists()) targetFile.delete()
                tmpFile.renameTo(targetFile)
                true
            } else {
                tmpFile.delete()
                false
            }
        } catch (e: Exception) {
            ZakoLog.w(TAG, "Download cover failed for $url: ${e.message}")
            try { tmpFile.delete() } catch (_: Exception) {}
            false
        }
    }

    /**
     * 将旧版本残留在 files/download_covers/ 目录中的封面迁移至 files/covers/
     */
    fun migrateLegacyDownloadCovers(context: Context) {
        scope.launch {
            try {
                val legacyDir = File(context.filesDir, "download_covers")
                if (legacyDir.exists() && legacyDir.isDirectory) {
                    val files = legacyDir.listFiles() ?: return@launch
                    val targetDir = getCoversDir(context)
                    var migratedCount = 0
                    for (file in files) {
                        if (file.isFile && file.length() > 0L) {
                            val target = File(targetDir, file.name)
                            if (!target.exists() || target.length() == 0L) {
                                file.copyTo(target, overwrite = true)
                                migratedCount++
                            }
                        }
                    }
                    if (migratedCount > 0) {
                        ZakoLog.i(TAG, "Migrated $migratedCount legacy covers from download_covers to covers")
                    }
                }
            } catch (e: Exception) {
                ZakoLog.w(TAG, "migrateLegacyDownloadCovers error: ${e.message}")
            }
        }
    }

    /**
     * 全量检查 4 个来源中的视频记录，清洗死本地路径并按优先级自动补齐缺失封面
     */
    fun syncMissingCovers(context: Context) {
        scope.launch {
            try {
                val appContext = context.applicationContext
                val db = ZakoDatabase.getInstance(appContext)

                val favs = db.favoriteDao().getAllItemsList()
                val dls = db.downloadDao().getAllDownloadsList()
                val later = db.watchLaterDao().getAllList()
                val hist = db.watchHistoryDao().getAllHistoryList()

                // 1. 净化数据库中的死路径：若 coverUrl 为应用私有死路径，清空以待自动补全
                for (f in favs) {
                    if (isDeadLocalPath(f.coverUrl)) {
                        db.favoriteDao().updateCoverUrl(f.videoId, "")
                    }
                }
                for (d in dls) {
                    if (isDeadLocalPath(d.coverUrl)) {
                        db.downloadDao().updateCoverUrl(d.videoId, "")
                    }
                }
                for (w in later) {
                    if (isDeadLocalPath(w.coverUrl)) {
                        db.watchLaterDao().updateCoverUrl(w.videoId, "")
                    }
                }
                for (h in hist) {
                    if (isDeadLocalPath(h.coverUrl)) {
                        db.watchHistoryDao().updateCoverUrl(h.videoId, "")
                    }
                }

                // 2. 聚合 4 个表的唯一 videoId 与最佳候选
                data class SyncCandidate(val videoId: String, val candidateUrl: String?, val localPath: String?)
                val candidates = mutableMapOf<String, SyncCandidate>()

                fun register(videoId: String, coverUrl: String?, localPath: String? = null) {
                    if (videoId.isBlank()) return
                    val existing = candidates[videoId]
                    val bestUrl = when {
                        isValidHttpUrl(coverUrl) -> coverUrl
                        isValidHttpUrl(existing?.candidateUrl) -> existing?.candidateUrl
                        else -> coverUrl?.takeIf { it.isNotBlank() } ?: existing?.candidateUrl
                    }
                    val bestPath = localPath ?: existing?.localPath
                    candidates[videoId] = SyncCandidate(videoId, bestUrl, bestPath)
                }

                for (f in favs) register(f.videoId, f.coverUrl)
                for (d in dls) register(d.videoId, d.coverUrl, d.savePath)
                for (w in later) register(w.videoId, w.coverUrl)
                for (h in hist) register(h.videoId, h.coverUrl)

                // 3. 逐个补齐缺失或 0 字节的封面
                var missingCount = 0
                for ((videoId, cand) in candidates) {
                    if (!hasValidCover(appContext, videoId)) {
                        missingCount++
                        ensureCoverPersisted(appContext, videoId, cand.candidateUrl, cand.localPath)
                    }
                }
                if (missingCount > 0) {
                    ZakoLog.i(TAG, "syncMissingCovers completed for $missingCount missing items")
                }
            } catch (e: Exception) {
                ZakoLog.w(TAG, "syncMissingCovers error: ${e.message}")
            }
        }
    }

    private fun isDeadLocalPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val trimmed = path.trim()
        if (trimmed.startsWith("/data/user/") || trimmed.startsWith("/data/data/")) return true
        if (trimmed.startsWith("/") && !File(trimmed).exists()) return true
        return false
    }
}
