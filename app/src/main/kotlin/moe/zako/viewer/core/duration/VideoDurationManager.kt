package moe.zako.viewer.core.duration

import android.content.Context
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.data.local.ZakoDatabase
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.data.source.DataSourceRegistry
import moe.zako.viewer.ui.components.DurationFormatter
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 视频时长静默自愈补齐与多表联动中枢 (VideoDurationManager)
 *
 * 核心机制（参考 CoverCacheManager.syncMissingCovers）：
 * 1. 跨表有效时长快速检索与同步广播
 * 2. 存量空时长静默自愈补齐（去重、低并发限流、3 级回退策略）：
 *    - 优先级 1（跨表补齐）：从其余 3 张表中查找同一 videoId 的有效时长直接回填；
 *    - 优先级 2（本地视频解析）：若存在本地已下载文件，使用 MediaMetadataRetriever 提取时长并回填；
 *    - 优先级 3（远端详情回填）：若当前已启用数据源，低并发调用详情接口获取时长并回填数据库。
 */
object VideoDurationManager {
    private const val TAG = "VideoDurationManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isSyncing = AtomicBoolean(false)

    /** 判断时长字符串是否无效（为空、0、00:00 等） */
    fun isInvalidDuration(duration: String?): Boolean {
        if (duration.isNullOrBlank()) return true
        val formatted = DurationFormatter.format(duration)
        return formatted.isEmpty()
    }

    /**
     * 将有效时长同步回填至所有包含该 videoId 的 4 张数据表中
     */
    suspend fun updateDurationAcrossAllTables(context: Context, videoId: String, duration: String) {
        if (videoId.isBlank() || isInvalidDuration(duration)) return
        val formatted = DurationFormatter.format(duration)
        if (formatted.isEmpty()) return

        try {
            val db = ZakoDatabase.getInstance(context.applicationContext)
            db.favoriteDao().updateDuration(videoId, formatted)
            db.watchLaterDao().updateDuration(videoId, formatted)
            db.watchHistoryDao().updateDuration(videoId, formatted)
            db.downloadDao().updateDuration(videoId, formatted)
        } catch (e: Exception) {
            ZakoLog.w(TAG, "updateDurationAcrossAllTables failed for $videoId: ${e.message}")
        }
    }

    /**
     * 全量检查 4 个表中的视频记录，去重限流并按优先级自动静默补齐缺失时长
     */
    fun syncMissingDurations(context: Context, settingsRepository: SettingsRepository? = null) {
        if (!isSyncing.compareAndSet(false, true)) {
            ZakoLog.d(TAG, "syncMissingDurations is already running, skipping")
            return
        }

        scope.launch {
            try {
                val appContext = context.applicationContext
                val db = ZakoDatabase.getInstance(appContext)

                val favs = db.favoriteDao().getAllItemsList()
                val dls = db.downloadDao().getAllDownloadsList()
                val later = db.watchLaterDao().getAllList()
                val hist = db.watchHistoryDao().getAllHistoryList()

                // 1. 收集所有已知的有效时长
                val validDurations = mutableMapOf<String, String>()
                for (f in favs) {
                    if (!isInvalidDuration(f.duration)) validDurations[f.videoId] = DurationFormatter.format(f.duration)
                }
                for (d in dls) {
                    if (!isInvalidDuration(d.duration)) validDurations[d.videoId] = DurationFormatter.format(d.duration)
                }
                for (w in later) {
                    if (!isInvalidDuration(w.duration)) validDurations[w.videoId] = DurationFormatter.format(w.duration)
                }
                for (h in hist) {
                    if (!isInvalidDuration(h.duration)) {
                        validDurations[h.videoId] = DurationFormatter.format(h.duration)
                    } else if (h.totalDurationMs > 0L) {
                        val formatted = DurationFormatter.formatMs(h.totalDurationMs)
                        if (formatted.isNotEmpty()) validDurations[h.videoId] = formatted
                    }
                }

                // 2. 收集缺少时长的 videoId 集合
                val missingVideoIds = mutableSetOf<String>()
                for (f in favs) if (isInvalidDuration(f.duration)) missingVideoIds.add(f.videoId)
                for (d in dls) if (isInvalidDuration(d.duration)) missingVideoIds.add(d.videoId)
                for (w in later) if (isInvalidDuration(w.duration)) missingVideoIds.add(w.videoId)
                for (h in hist) {
                    if (isInvalidDuration(h.duration) && h.totalDurationMs <= 0L) {
                        missingVideoIds.add(h.videoId)
                    }
                }

                if (missingVideoIds.isEmpty()) {
                    ZakoLog.d(TAG, "No missing video durations found")
                    return@launch
                }

                ZakoLog.i(TAG, "Found ${missingVideoIds.size} unique videos with missing duration, starting backfill...")
                var healedCount = 0

                // 优先级 1: 跨表补齐
                val iterator = missingVideoIds.iterator()
                while (iterator.hasNext()) {
                    val id = iterator.next()
                    val knownDuration = validDurations[id]
                    if (!knownDuration.isNullOrBlank()) {
                        updateDurationAcrossAllTables(appContext, id, knownDuration)
                        iterator.remove()
                        healedCount++
                    }
                }

                if (missingVideoIds.isEmpty()) {
                    ZakoLog.i(TAG, "Priority 1 (cross-table) healed $healedCount durations, all done")
                    return@launch
                }

                // 优先级 2: 本地已下载视频文件 MediaMetadataRetriever 解析
                val completedDownloadsMap = dls.filter { it.status == "COMPLETED" && it.savePath.isNotBlank() }
                    .associateBy { it.videoId }

                val p2Iterator = missingVideoIds.iterator()
                while (p2Iterator.hasNext()) {
                    val id = p2Iterator.next()
                    val download = completedDownloadsMap[id]
                    val file = if (download != null) File(download.savePath) else {
                        DownloadManager.getInstance(appContext).findLocalCompletedVideo(id)?.file
                    }
                    if (file != null && file.exists() && file.isFile && file.length() > 0L) {
                        var parsedDuration: String? = null
                        try {
                            MediaMetadataRetriever().use { retriever ->
                                retriever.setDataSource(file.absolutePath)
                                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                val durMs = durStr?.toLongOrNull() ?: 0L
                                if (durMs > 0L) {
                                    parsedDuration = DurationFormatter.formatMs(durMs)
                                }
                            }
                        } catch (e: Exception) {
                            ZakoLog.w(TAG, "Failed to parse local video metadata for $id: ${e.message}")
                        }

                        if (!parsedDuration.isNullOrBlank()) {
                            updateDurationAcrossAllTables(appContext, id, parsedDuration!!)
                            validDurations[id] = parsedDuration!!
                            p2Iterator.remove()
                            healedCount++
                        }
                    }
                }

                if (missingVideoIds.isEmpty()) {
                    ZakoLog.i(TAG, "Priority 2 (local parsing) completed, total healed: $healedCount")
                    return@launch
                }

                // 优先级 3: 远端详情回填（受已启用数据源限制，低并发限流）
                val settingsRepo = settingsRepository ?: SettingsRepository(appContext)
                val activeSource = DataSourceRegistry.getActiveDataSource(settingsRepo)
                if (activeSource != null) {
                    val semaphore = Semaphore(2)
                    for (id in missingVideoIds.toList()) {
                        semaphore.withPermit {
                            try {
                                delay(200L) // 请求间隔防限流
                                val detail = activeSource.getVideoDetail(id)
                                val dur = detail?.duration
                                if (!isInvalidDuration(dur)) {
                                    val formatted = DurationFormatter.format(dur)
                                    if (formatted.isNotEmpty()) {
                                        updateDurationAcrossAllTables(appContext, id, formatted)
                                        validDurations[id] = formatted
                                        healedCount++
                                    }
                                }
                            } catch (e: Exception) {
                                ZakoLog.w(TAG, "Remote detail fetch duration failed for $id: ${e.message}")
                            }
                        }
                    }
                }

                ZakoLog.i(TAG, "syncMissingDurations completed, total healed items: $healedCount")
            } catch (e: Exception) {
                ZakoLog.w(TAG, "syncMissingDurations encountered error: ${e.message}")
            } finally {
                isSyncing.set(false)
            }
        }
    }
}
