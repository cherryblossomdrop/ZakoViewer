package moe.zako.viewer.data.local.dao

import androidx.room.*
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.data.local.entities.WatchLaterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun getAllHistoryFlow(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE title LIKE '%' || :query || '%' ORDER BY watchedAt DESC")
    fun searchHistoryFlow(query: String): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    suspend fun getAllHistoryList(): List<WatchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("DELETE FROM watch_history WHERE videoId IN (:videoIds)")
    suspend fun deleteByVideoIds(videoIds: List<String>)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}

@Dao
interface WatchLaterDao {
    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    fun getAllFlow(): Flow<List<WatchLaterEntity>>

    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    suspend fun getAllList(): List<WatchLaterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM watch_later WHERE videoId = :videoId)")
    suspend fun isWatchLater(videoId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM watch_later WHERE videoId = :videoId)")
    fun isWatchLaterFlow(videoId: String): Flow<Boolean>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC, createdAt DESC")
    fun getCompletedDownloadsFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status != 'COMPLETED' ORDER BY createdAt DESC")
    fun getActiveDownloadsFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloadsFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun getAllDownloadsList(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId LIMIT 1")
    suspend fun getDownloadByVideoId(videoId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE videoId = :videoId LIMIT 1")
    fun getDownloadFlow(videoId: String): Flow<DownloadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(download: DownloadEntity)

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, speedBps = :speed, status = :status WHERE videoId = :videoId")
    suspend fun updateProgress(videoId: String, downloaded: Long, total: Long, speed: Long, status: String)

    @Query("UPDATE downloads SET status = :status WHERE videoId = :videoId")
    suspend fun updateStatus(videoId: String, status: String)

    @Query("DELETE FROM downloads WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)
}
