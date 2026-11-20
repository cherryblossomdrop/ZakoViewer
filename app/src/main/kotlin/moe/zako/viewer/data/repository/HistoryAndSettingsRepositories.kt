package moe.zako.viewer.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import moe.zako.viewer.data.local.dao.WatchHistoryDao
import moe.zako.viewer.data.local.dao.WatchLaterDao
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.data.local.entities.WatchLaterEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepository(
    private val watchHistoryDao: WatchHistoryDao,
    private val watchLaterDao: WatchLaterDao,
    private val favoriteRepository: FavoriteRepository
) {
    fun getAllHistory(): Flow<List<WatchHistoryEntity>> = watchHistoryDao.getAllHistoryFlow()

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
    }

    suspend fun removeFromWatchLater(videoId: String) = watchLaterDao.deleteByVideoId(videoId)

    suspend fun isWatchLater(videoId: String): Boolean = watchLaterDao.isWatchLater(videoId)

    fun isWatchLaterFlow(videoId: String): Flow<Boolean> = watchLaterDao.isWatchLaterFlow(videoId)
}

private val Context.dataStore by preferencesDataStore(name = "neoviewer_settings")

class SettingsRepository(private val context: Context) {
    companion object {
        val KEY_LONG_PRESS_SPEED = floatPreferencesKey("long_press_speed")
        val KEY_EXTERNAL_PLAYER = stringPreferencesKey("external_player_pkg")
        val KEY_EXTERNAL_DOWNLOADER = stringPreferencesKey("external_downloader_pkg")
        val KEY_FILTER_VIP = booleanPreferencesKey("filter_vip_early_access")
        val KEY_INCLUDE_VIP = booleanPreferencesKey("include_vip_early_access")
        val KEY_DOWNLOAD_PATH = stringPreferencesKey("custom_download_path")
        val KEY_PLAYER_ENGINE = stringPreferencesKey("player_engine")
        val KEY_HWDEC_MODE = stringPreferencesKey("hwdec_mode")
        val KEY_MONET_DYNAMIC_COLOR = booleanPreferencesKey("monet_dynamic_color")
        val KEY_CUSTOM_THEME = stringPreferencesKey("custom_theme_color")
        val KEY_COVER_BLUR = booleanPreferencesKey("cover_blur_enabled")
        val KEY_APP_ICON_FOLLOW_THEME = booleanPreferencesKey("app_icon_follow_theme")
        val KEY_LONG_PRESS_SHARE_UNLOCKED = booleanPreferencesKey("long_press_share_unlocked")
        val KEY_LONG_PRESS_SHARE_ENABLED = booleanPreferencesKey("long_press_share_enabled")
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_MONET_DYNAMIC_COLOR] ?: false // 默认关闭莫奈动态取色
    }

    val customThemeFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_CUSTOM_THEME] ?: "#5BB974" // 默认使用浅绿色
    }

    val coverBlurFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_COVER_BLUR] ?: true // 默认必须为 true (开启模糊保护)
    }

    val appIconFollowThemeFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_APP_ICON_FOLLOW_THEME] ?: false // 默认关闭
    }

    val longPressShareUnlockedFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_LONG_PRESS_SHARE_UNLOCKED] ?: false
    }

    val longPressShareEnabledFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_LONG_PRESS_SHARE_ENABLED] ?: false
    }

    val playerEngineFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_PLAYER_ENGINE] ?: "system"
    }

    val hwDecModeFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_HWDEC_MODE] ?: "mediacodec"
    }

    val longPressSpeedFlow: Flow<Float> = context.dataStore.data.map {
        it[KEY_LONG_PRESS_SPEED] ?: 2.0f
    }

    val externalPlayerFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_EXTERNAL_PLAYER] ?: ""
    }

    val externalDownloaderFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_EXTERNAL_DOWNLOADER] ?: ""
    }

    val filterVipFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_FILTER_VIP] ?: true
    }

    val includeVipFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_INCLUDE_VIP] ?: true // 默认包含VIP抢先看视频
    }

    val downloadPathFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_DOWNLOAD_PATH] ?: ""
    }

    suspend fun setLongPressSpeed(speed: Float) {
        context.dataStore.edit { it[KEY_LONG_PRESS_SPEED] = speed }
    }

    suspend fun setExternalPlayer(pkg: String) {
        context.dataStore.edit { it[KEY_EXTERNAL_PLAYER] = pkg }
    }

    suspend fun setExternalDownloader(pkg: String) {
        context.dataStore.edit { it[KEY_EXTERNAL_DOWNLOADER] = pkg }
    }

    suspend fun setFilterVip(filter: Boolean) {
        context.dataStore.edit { it[KEY_FILTER_VIP] = filter }
    }

    suspend fun setIncludeVip(include: Boolean) {
        context.dataStore.edit { it[KEY_INCLUDE_VIP] = include }
    }

    suspend fun setDownloadPath(path: String) {
        context.dataStore.edit { it[KEY_DOWNLOAD_PATH] = path }
    }

    suspend fun setPlayerEngine(engine: String) {
        context.dataStore.edit { it[KEY_PLAYER_ENGINE] = engine }
    }

    suspend fun setHwDecMode(mode: String) {
        context.dataStore.edit { it[KEY_HWDEC_MODE] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MONET_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setCustomTheme(theme: String) {
        context.dataStore.edit { it[KEY_CUSTOM_THEME] = theme }
    }

    suspend fun setCoverBlur(enabled: Boolean) {
        context.dataStore.edit { it[KEY_COVER_BLUR] = enabled }
    }

    suspend fun setAppIconFollowTheme(follow: Boolean) {
        context.dataStore.edit { it[KEY_APP_ICON_FOLLOW_THEME] = follow }
    }

    suspend fun setLongPressShareUnlocked(unlocked: Boolean) {
        context.dataStore.edit { it[KEY_LONG_PRESS_SHARE_UNLOCKED] = unlocked }
    }

    suspend fun setLongPressShareEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LONG_PRESS_SHARE_ENABLED] = enabled }
    }
}
