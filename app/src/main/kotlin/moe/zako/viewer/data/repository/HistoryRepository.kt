package moe.zako.viewer.data.repository

import kotlinx.coroutines.flow.Flow
import moe.zako.viewer.ZakoApplication
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.data.local.dao.SearchHistoryDao
import moe.zako.viewer.data.local.dao.WatchHistoryDao
import moe.zako.viewer.data.local.dao.WatchLaterDao
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.data.local.entities.SearchHistoryEntity
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.data.local.entities.WatchLaterEntity

/**
 * 观看历史与稍后再看业务仓储 (HistoryRepository)
 * 统一承接播放历史记录、查询、多选批量转存收藏夹、稍后再看队列及封面持久化触发
 */
class HistoryRepository(
    private val watchHistoryDao: WatchHistoryDao,
    private val watchLaterDao: WatchLaterDao,
    private val favoriteRepository: FavoriteRepository
) {
    fun getAllHistory(): Flow<List<WatchHistoryEntity>> = watchHistoryDao.getAllHistoryFlow()

    suspend fun getHistory(videoId: String): WatchHistoryEntity? = watchHistoryDao.getHistoryByVideoId(videoId)

    fun searchHistory(query: String): Flow<List<WatchHistoryEntity>> =
        watchHistoryDao.searchHistoryFlow(query)

    suspend fun recordWatch(
        videoId: String,
        title: String,
        coverUrl: String,
        duration: String = "",
        isVip: Boolean = false,
        positionMs: Long = 0L,
        totalDurationMs: Long = 0L
    ) {
        watchHistoryDao.insertOrUpdate(
            WatchHistoryEntity(
                videoId = videoId,
                title = title,
                coverUrl = coverUrl,
                duration = duration,
                isVip = isVip,
                watchedAt = System.currentTimeMillis(),
                playbackPositionMs = positionMs,
                totalDurationMs = totalDurationMs
            )
        )
        try {
            CoverCacheManager.ensureCoverPersistedAsync(ZakoApplication.instance, videoId, coverUrl)
        } catch (_: Exception) {}
    }

    suspend fun deleteHistory(videoId: String) = watchHistoryDao.deleteByVideoId(videoId)

    suspend fun deleteHistoryList(videoIds: List<String>) = watchHistoryDao.deleteByVideoIds(videoIds)

    suspend fun clearAllHistory() = watchHistoryDao.clearAll()

    /**
     * Batch add selected history items to a target favorite folder
     */
    suspend fun batchAddToFavorites(items: List<WatchHistoryEntity>, targetFolderId: Long) {
        val favoriteItems = items.map {
            FavoriteItemEntity(
                folderId = targetFolderId,
                videoId = it.videoId,
                title = it.title,
                coverUrl = it.coverUrl,
                duration = it.duration,
                isVip = it.isVip,
                addedAt = System.currentTimeMillis()
            )
        }
        favoriteRepository.batchCopy(favoriteItems, targetFolderId)
    }

    // --- Watch Later ---
    fun getWatchLaterFlow(): Flow<List<WatchLaterEntity>> = watchLaterDao.getAllFlow()

    suspend fun addToWatchLater(
        videoId: String,
        title: String,
        coverUrl: String,
        duration: String = "",
        isVip: Boolean = false
    ) {
        watchLaterDao.insert(
            WatchLaterEntity(
                videoId = videoId,
                title = title,
                coverUrl = coverUrl,
                duration = duration,
                isVip = isVip
            )
        )
        try {
            CoverCacheManager.ensureCoverPersistedAsync(ZakoApplication.instance, videoId, coverUrl)
        } catch (_: Exception) {}
    }

    suspend fun removeFromWatchLater(videoId: String) = watchLaterDao.deleteByVideoId(videoId)

    suspend fun removeFromWatchLaterList(videoIds: List<String>) = watchLaterDao.deleteByVideoIds(videoIds)

    suspend fun isWatchLater(videoId: String): Boolean = watchLaterDao.isWatchLater(videoId)

    fun isWatchLaterFlow(videoId: String): Flow<Boolean> = watchLaterDao.isWatchLaterFlow(videoId)
}

/**
 * 搜索关键词历史记录业务仓储 (SearchHistoryRepository)
 * 限制最多保留 50 条最近搜索记录
 */
class SearchHistoryRepository(
    private val searchHistoryDao: SearchHistoryDao
) {
    val searchHistoryFlow: Flow<List<SearchHistoryEntity>> = searchHistoryDao.getAllFlow()

    suspend fun saveSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchHistoryDao.insertOrUpdate(
            SearchHistoryEntity(query = trimmed, searchedAt = System.currentTimeMillis())
        )
        searchHistoryDao.trimToMax50()
    }

    suspend fun deleteSearchQuery(query: String) {
        searchHistoryDao.delete(query.trim())
    }

    suspend fun deleteSearchQueries(queries: List<String>) {
        if (queries.isNotEmpty()) {
            searchHistoryDao.deleteByQueries(queries.map { it.trim() })
        }
    }

    suspend fun clearAll() {
        searchHistoryDao.clearAll()
    }
}
