package moe.zako.viewer.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.zako.viewer.data.source.cosav.CosAvDataSource
import moe.zako.viewer.ui.theme.ThemePreset
import org.json.JSONArray
import java.io.File

private fun migratePreferencesIfNeeded(context: Context) {
    try {
        val oldFile = File(context.filesDir, "datastore/neoviewer_settings.preferences_pb")
        val newFile = File(context.filesDir, "datastore/zakoviewer_settings.preferences_pb")
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

/**
 * 全局用户偏好与设置仓储 (SettingsRepository)
 * 基于 AndroidX Preferences DataStore 提供响应式配置读取与持久化修改
 */
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
        val KEY_DATA_SOURCE_SECTION_UNLOCKED = booleanPreferencesKey("data_source_section_unlocked")
        val KEY_ENABLED_DATA_SOURCES = stringSetPreferencesKey("enabled_data_source_ids")
        val KEY_INITIALIZED_DATA_SOURCE_PREFS = booleanPreferencesKey("initialized_data_source_prefs")

        fun checkIsLegacyUpgrade(context: Context, prefs: androidx.datastore.preferences.core.Preferences): Boolean {
            val logPrefs = context.getSharedPreferences("zakoviewer_log_prefs", Context.MODE_PRIVATE)
            val hasLegacyLog = logPrefs.contains("last_version_code")
            val dbFile = context.getDatabasePath("zako_database")
            val hasExistingDb = dbFile.exists() && dbFile.length() > 0L
            val hasExistingSettings = prefs.contains(KEY_PLAYER_ENGINE) || prefs.contains(KEY_CUSTOM_THEME) || prefs.contains(KEY_COVER_BLUR)
            return hasLegacyLog || hasExistingDb || hasExistingSettings
        }
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ensureDataSourcePreferencesInitialized()
            } catch (_: Exception) {}
        }
    }

    suspend fun ensureDataSourcePreferencesInitialized() {
        context.dataStore.edit { prefs ->
            if (!prefs.contains(KEY_INITIALIZED_DATA_SOURCE_PREFS)) {
                val isLegacy = checkIsLegacyUpgrade(context, prefs)
                if (isLegacy) {
                    prefs[KEY_DATA_SOURCE_SECTION_UNLOCKED] = true
                    prefs[KEY_ENABLED_DATA_SOURCES] = setOf(CosAvDataSource.ID)
                } else {
                    prefs[KEY_DATA_SOURCE_SECTION_UNLOCKED] = false
                    prefs[KEY_ENABLED_DATA_SOURCES] = emptySet()
                }
                prefs[KEY_INITIALIZED_DATA_SOURCE_PREFS] = true
            }
        }
    }

    val isDataSourceSectionUnlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        if (prefs.contains(KEY_INITIALIZED_DATA_SOURCE_PREFS)) {
            prefs[KEY_DATA_SOURCE_SECTION_UNLOCKED] ?: false
        } else {
            checkIsLegacyUpgrade(context, prefs)
        }
    }

    val enabledDataSourceIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        if (prefs.contains(KEY_INITIALIZED_DATA_SOURCE_PREFS)) {
            prefs[KEY_ENABLED_DATA_SOURCES] ?: emptySet()
        } else {
            if (checkIsLegacyUpgrade(context, prefs)) setOf(CosAvDataSource.ID) else emptySet()
        }
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

    suspend fun setDataSourceSectionUnlocked(unlocked: Boolean) {
        context.dataStore.edit { it[KEY_DATA_SOURCE_SECTION_UNLOCKED] = unlocked }
    }

    suspend fun setDataSourceEnabled(sourceId: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_ENABLED_DATA_SOURCES] ?: emptySet()
            val updated = if (enabled) current + sourceId else current - sourceId
            prefs[KEY_ENABLED_DATA_SOURCES] = updated
        }
    }

    suspend fun isDataSourceSectionUnlockedSnapshot(): Boolean {
        val prefs = context.dataStore.data.first()
        return if (prefs.contains(KEY_INITIALIZED_DATA_SOURCE_PREFS)) {
            prefs[KEY_DATA_SOURCE_SECTION_UNLOCKED] ?: false
        } else {
            checkIsLegacyUpgrade(context, prefs)
        }
    }

    suspend fun getEnabledDataSourceIdsSnapshot(): Set<String> {
        val prefs = context.dataStore.data.first()
        return if (prefs.contains(KEY_INITIALIZED_DATA_SOURCE_PREFS)) {
            prefs[KEY_ENABLED_DATA_SOURCES] ?: emptySet()
        } else {
            if (checkIsLegacyUpgrade(context, prefs)) setOf(CosAvDataSource.ID) else emptySet()
        }
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
