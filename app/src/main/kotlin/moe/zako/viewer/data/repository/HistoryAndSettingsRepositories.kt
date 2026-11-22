package moe.zako.viewer.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import moe.zako.viewer.data.local.dao.SearchHistoryDao
import moe.zako.viewer.data.local.dao.WatchHistoryDao
import moe.zako.viewer.data.local.dao.WatchLaterDao
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.data.local.entities.SearchHistoryEntity
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.ZakoApplication
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.data.local.entities.WatchLaterEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.zako.viewer.ui.theme.ThemePreset
import org.json.JSONArray

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

private fun migratePreferencesIfNeeded(context: Context) {
    try {
        val oldFile = java.io.File(context.filesDir, "datastore/neoviewer_settings.preferences_pb")
        val newFile = java.io.File(context.filesDir, "datastore/zakoviewer_settings.preferences_pb")
        if (oldFile.exists() && !newFile.exists()) {
            newFile.parentFile?.mkdirs()
            oldFile.copyTo(newFile, overwrite = true)
        }
    } catch (_: Exception) {}
}

private val Context.dataStore by preferencesDataStore(
    name = "zakoviewer_settings",
    produceMigrations = { context ->
        migratePreferencesIfNeeded(context)
        emptyList()
    }
)

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
        val KEY_SAVED_CUSTOM_THEMES = stringPreferencesKey("saved_custom_themes")
        val KEY_COVER_BLUR = booleanPreferencesKey("cover_blur_enabled")
        val KEY_APP_ICON_FOLLOW_THEME = booleanPreferencesKey("app_icon_follow_theme")
        val KEY_LONG_PRESS_SHARE_UNLOCKED = booleanPreferencesKey("long_press_share_unlocked")
        val KEY_LONG_PRESS_SHARE_ENABLED = booleanPreferencesKey("long_press_share_enabled")
        val KEY_LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val KEY_AUTO_SELECT_CDN = booleanPreferencesKey("auto_select_cdn")
        val KEY_DEFAULT_CDN_INDEX = intPreferencesKey("default_cdn_index")
    }

    val autoSelectCdnFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_AUTO_SELECT_CDN] ?: true // 默认开启自动测速选线
    }

    val defaultCdnIndexFlow: Flow<Int> = context.dataStore.data.map {
        it[KEY_DEFAULT_CDN_INDEX] ?: 0 // 默认使用线路 1 (索引 0)
    }

    val layoutModeFlow: Flow<LayoutMode> = context.dataStore.data.map {
        LayoutMode.fromKey(it[KEY_LAYOUT_MODE])
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_MONET_DYNAMIC_COLOR] ?: false // 默认关闭莫奈动态取色
    }

    val customThemeFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_CUSTOM_THEME] ?: "#686BDD" // 默认使用堇紫色
    }

    val savedCustomThemesFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[KEY_SAVED_CUSTOM_THEMES] ?: "[]"
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val hex = jsonArray.optString(i)?.trim()?.uppercase()
                if (!hex.isNullOrEmpty() && hex.startsWith("#")) {
                    list.add(hex)
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
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
        it[KEY_PLAYER_ENGINE] ?: "mpv"
    }

    val hwDecModeFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_HWDEC_MODE] ?: "mediacodec-copy"
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

    suspend fun addCustomTheme(hex: String) {
        val cleanHex = if (hex.startsWith("#")) hex.uppercase() else "#${hex.uppercase()}"
        context.dataStore.edit { prefs ->
            val currentJson = prefs[KEY_SAVED_CUSTOM_THEMES] ?: "[]"
            val list = mutableListOf<String>()
            try {
                val array = JSONArray(currentJson)
                for (i in 0 until array.length()) {
                    val item = array.optString(i)?.trim()?.uppercase()
                    if (!item.isNullOrEmpty()) list.add(item)
                }
            } catch (_: Exception) {}
            if (!list.contains(cleanHex)) {
                list.add(cleanHex)
            }
            val outArray = JSONArray()
            list.forEach { outArray.put(it) }
            prefs[KEY_SAVED_CUSTOM_THEMES] = outArray.toString()
            prefs[KEY_CUSTOM_THEME] = cleanHex
        }
    }

    suspend fun removeCustomTheme(hex: String) {
        val cleanHex = if (hex.startsWith("#")) hex.uppercase() else "#${hex.uppercase()}"
        if (ThemePreset.isBuiltinPreset(cleanHex)) return

        context.dataStore.edit { prefs ->
            val currentJson = prefs[KEY_SAVED_CUSTOM_THEMES] ?: "[]"
            val list = mutableListOf<String>()
            try {
                val array = JSONArray(currentJson)
                for (i in 0 until array.length()) {
                    val item = array.optString(i)?.trim()?.uppercase()
                    if (!item.isNullOrEmpty() && item != cleanHex) {
                        list.add(item)
                    }
                }
            } catch (_: Exception) {}
            val outArray = JSONArray()
            list.forEach { outArray.put(it) }
            prefs[KEY_SAVED_CUSTOM_THEMES] = outArray.toString()

            val active = prefs[KEY_CUSTOM_THEME]?.trim()?.uppercase()
            if (active == cleanHex) {
                prefs[KEY_CUSTOM_THEME] = "#686BDD"
            }
        }
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

    suspend fun setAutoSelectCdn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SELECT_CDN] = enabled }
    }

    suspend fun setDefaultCdnIndex(index: Int) {
        context.dataStore.edit { it[KEY_DEFAULT_CDN_INDEX] = index.coerceAtLeast(0) }
    }

    suspend fun setLayoutMode(mode: LayoutMode) {
        context.dataStore.edit { it[KEY_LAYOUT_MODE] = mode.name }
    }

    suspend fun toggleLayoutMode() {
        context.dataStore.edit {
            val current = LayoutMode.fromKey(it[KEY_LAYOUT_MODE])
            val next = if (current == LayoutMode.WATERFALL) LayoutMode.PAGINATED else LayoutMode.WATERFALL
            it[KEY_LAYOUT_MODE] = next.name
        }
    }
}

enum class LayoutMode(val key: String) {
    WATERFALL("WATERFALL"),
    PAGINATED("PAGINATED");

    companion object {
        fun fromKey(key: String?): LayoutMode =
            if (key == PAGINATED.name) PAGINATED else WATERFALL
    }
}
