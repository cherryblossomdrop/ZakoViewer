package moe.zako.viewer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val coverUrl: String,
    val duration: String = "",
    val isVip: Boolean = false,
    val watchedAt: Long = System.currentTimeMillis(),
    val playbackPositionMs: Long = 0,
    val totalDurationMs: Long = 0
) {
    val timeExactString: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(watchedAt))

    val dayGroupString: String
        get() {
            val dateForm = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = dateForm.format(Date())
            val targetDay = dateForm.format(Date(watchedAt))
            return when {
                targetDay == today -> "今天"
                // Check yesterday
                System.currentTimeMillis() - watchedAt < 86400000L * 2 && targetDay != today -> "昨天"
                else -> targetDay
            }
        }
}

@Entity(tableName = "watch_later")
data class WatchLaterEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val coverUrl: String,
    val duration: String = "",
    val isVip: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val coverUrl: String,
    val savePath: String,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: String = STATUS_PENDING, // PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED
    val errorMessage: String? = null,
    val speedBps: Long = 0,
    val activeNodesCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val cdnUrlsJson: String = "",
    val duration: String = ""
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
    }

    val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey
    val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)
