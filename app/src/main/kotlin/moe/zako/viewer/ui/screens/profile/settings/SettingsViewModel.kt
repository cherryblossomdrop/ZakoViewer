package moe.zako.viewer.ui.screens.profile.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.ZakoApplication
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.storage.StorageConstants
import moe.zako.viewer.data.local.dao.DownloadDao
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.ui.components.ZakoToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * 分享设置子页面可配置项枚举（可扩展架构定义）
 */
enum class ShareSettingItem {
    /** 开启长按分享复制完整链接 */
    LONG_PRESS_SHARE_URL
}

data class SettingsUiState(
    val longPressSpeed: Float = 2.0f,
    val externalPlayerPkg: String = "",
    val externalDownloaderPkg: String = "",
    val downloadPath: String = "",
    val playerEngine: String = "mpv",
    val hwDecMode: String = "mediacodec-copy",
    val dynamicColor: Boolean = true,
    val customTheme: String = "#686BDD",
    val coverBlurEnabled: Boolean = true, // 默认开启
    val appIconFollowTheme: Boolean = false, // 图标颜色跟随应用主题色（默认关闭）
    val savedCustomThemes: List<String> = emptyList(),
    val isLongPressShareUnlocked: Boolean = false,
    val isLongPressShareEnabled: Boolean = false,
    val autoSelectCdn: Boolean = true,
    val defaultCdnIndex: Int = 0,
    val showMigrationDialog: Boolean = false,
    val pendingNewDownloadPath: String = "",
    val filesToMigrateCount: Int = 0,
    val isMigrating: Boolean = false,
    val migrationProgressText: String = "",
    val cacheSizeFormatted: String = "计算中..."
) {
    /**
     * 动态汇总分享设置子页面当前所有实际可见的配置项集合
     * 架构解耦：防硬绑定单个布尔值，未来新增分享配置项直接在此集合追加判定即可自动生效
     */
    val visibleShareSettingItems: Set<ShareSettingItem>
        get() = buildSet {
            if (isLongPressShareUnlocked) {
                add(ShareSettingItem.LONG_PRESS_SHARE_URL)
            }
            // 未来新增其他分享设置项时直接在此追加判定
        }

    /**
     * 是否存在任何可见的分享设置配置项
     */
    val hasVisibleShareSettings: Boolean
        get() = visibleShareSettingItems.isNotEmpty()
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val downloadDao: DownloadDao? = null,
    private val downloadManager: DownloadManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                listOf(
                    settingsRepository.longPressSpeedFlow,
                    settingsRepository.externalPlayerFlow,
                    settingsRepository.externalDownloaderFlow,
                    settingsRepository.downloadPathFlow,
                    settingsRepository.playerEngineFlow,
                    settingsRepository.hwDecModeFlow,
                    settingsRepository.dynamicColorFlow,
                    settingsRepository.customThemeFlow,
                    settingsRepository.savedCustomThemesFlow,
                    settingsRepository.coverBlurFlow,
                    settingsRepository.appIconFollowThemeFlow,
                    settingsRepository.longPressShareUnlockedFlow,
                    settingsRepository.longPressShareEnabledFlow,
                    settingsRepository.autoSelectCdnFlow,
                    settingsRepository.defaultCdnIndexFlow
                )
            ) { args: Array<Any> ->
                @Suppress("UNCHECKED_CAST")
                SettingsUiState(
                    longPressSpeed = args[0] as Float,
                    externalPlayerPkg = args[1] as String,
                    externalDownloaderPkg = args[2] as String,
                    downloadPath = args[3] as String,
                    playerEngine = args[4] as String,
                    hwDecMode = args[5] as String,
                    dynamicColor = args[6] as Boolean,
                    customTheme = args[7] as String,
                    savedCustomThemes = args[8] as List<String>,
                    coverBlurEnabled = args[9] as Boolean,
                    appIconFollowTheme = args[10] as Boolean,
                    isLongPressShareUnlocked = args[11] as Boolean,
                    isLongPressShareEnabled = args[12] as Boolean,
                    autoSelectCdn = args[13] as Boolean,
                    defaultCdnIndex = args[14] as Int,
                    cacheSizeFormatted = _uiState.value.cacheSizeFormatted
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setSpeed(speed: Float) {
        viewModelScope.launch { settingsRepository.setLongPressSpeed(speed) }
    }

    fun setExternalPlayer(pkg: String) {
        viewModelScope.launch { settingsRepository.setExternalPlayer(pkg) }
    }

    fun setExternalDownloader(pkg: String) {
        viewModelScope.launch { settingsRepository.setExternalDownloader(pkg) }
    }

    fun setDownloadPath(path: String) {
        viewModelScope.launch { settingsRepository.setDownloadPath(path) }
    }

    fun setPlayerEngine(engine: String) {
        viewModelScope.launch { settingsRepository.setPlayerEngine(engine) }
    }

    fun setHwDecMode(mode: String) {
        viewModelScope.launch { settingsRepository.setHwDecMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setCustomTheme(theme: String) {
        viewModelScope.launch { settingsRepository.setCustomTheme(theme) }
    }

    fun addCustomTheme(hex: String) {
        viewModelScope.launch { settingsRepository.addCustomTheme(hex) }
    }

    fun removeCustomTheme(hex: String) {
        viewModelScope.launch { settingsRepository.removeCustomTheme(hex) }
    }

    fun setCoverBlur(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCoverBlur(enabled) }
    }

    fun setAppIconFollowTheme(follow: Boolean) {
        viewModelScope.launch { settingsRepository.setAppIconFollowTheme(follow) }
    }

    fun setLongPressShareEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLongPressShareEnabled(enabled) }
    }

    fun setAutoSelectCdn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoSelectCdn(enabled) }
    }

    fun setDefaultCdnIndex(index: Int) {
        viewModelScope.launch { settingsRepository.setDefaultCdnIndex(index) }
    }

    fun requestChangeDownloadPath(newPath: String, currentDefaultPath: String) {
        val oldPath = _uiState.value.downloadPath.ifEmpty { currentDefaultPath }
        if (newPath == oldPath || newPath.isBlank()) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val dao = downloadDao
            val downloads = dao?.getAllDownloadsList() ?: emptyList()
            val targetCanonical = try { File(newPath).canonicalPath } catch (_: Exception) { File(newPath).absolutePath }
            val existingFiles = downloads.filter { entity ->
                val f = File(entity.savePath)
                if (f.exists() && f.isFile && f.length() > 0) {
                    val srcCanonical = try { f.parentFile?.canonicalPath } catch (_: Exception) { f.parentFile?.absolutePath }
                    srcCanonical != targetCanonical
                } else {
                    false
                }
            }
            if (existingFiles.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        showMigrationDialog = true,
                        pendingNewDownloadPath = newPath,
                        filesToMigrateCount = existingFiles.size
                    )
                }
            } else {
                val mgr = downloadManager ?: DownloadManager.getInstance(ZakoApplication.instance)
                mgr.migrateDownloadsDirectory(newPath) { _, _, _ -> }
            }
        }
    }

    fun confirmMigration() {
        val newPath = _uiState.value.pendingNewDownloadPath
        if (newPath.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isMigrating = true,
                    migrationProgressText = "准备迁移..."
                )
            }
            val mgr = downloadManager ?: DownloadManager.getInstance(ZakoApplication.instance)
            val result = mgr.migrateDownloadsDirectory(newPath) { current, total, fileName ->
                _uiState.update {
                    it.copy(
                        migrationProgressText = "正在迁移 ($current/$total): $fileName"
                    )
                }
            }
            _uiState.update {
                it.copy(
                    showMigrationDialog = false,
                    isMigrating = false,
                    pendingNewDownloadPath = "",
                    filesToMigrateCount = 0
                )
            }
            if (result.errorMessage != null) {
                ZakoToast.show(result.errorMessage)
            } else {
                ZakoToast.show("已迁移 ${result.successCount} 个视频至新目录")
            }
        }
    }

    fun skipMigrationAndSetPath() {
        val newPath = _uiState.value.pendingNewDownloadPath
        viewModelScope.launch {
            if (newPath.isNotBlank()) {
                settingsRepository.setDownloadPath(newPath)
                val mgr = downloadManager ?: DownloadManager.getInstance(ZakoApplication.instance)
                mgr.reconcileLocalDownloadFiles()
            }
            _uiState.update {
                it.copy(
                    showMigrationDialog = false,
                    isMigrating = false,
                    pendingNewDownloadPath = "",
                    filesToMigrateCount = 0
                )
            }
        }
    }

    fun dismissMigrationDialog() {
        _uiState.update {
            it.copy(
                showMigrationDialog = false,
                isMigrating = false,
                pendingNewDownloadPath = "",
                filesToMigrateCount = 0
            )
        }
    }

    // --- 应用缓存统计与清理 ---
    fun calculateCacheSize(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val downloadDir = try {
                DownloadManager.getInstance(context).getDownloadDir().canonicalFile
            } catch (_: Exception) {
                null
            }
            val totalSize = getFolderSize(context.cacheDir, downloadDir) + getFolderSize(context.externalCacheDir, downloadDir)
            _uiState.update { it.copy(cacheSizeFormatted = formatBytes(totalSize)) }
        }
    }

    fun clearAppCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadDir = try {
                    DownloadManager.getInstance(context).getDownloadDir().canonicalFile
                } catch (_: Exception) {
                    null
                }

                // 1. Coil 内存缓存与磁盘缓存彻底清理
                try {
                    coil.Coil.imageLoader(context).apply {
                        memoryCache?.clear()
                        diskCache?.clear()
                    }
                } catch (_: Exception) {}

                // 2. 播放器临时流媒体缓存彻底清理
                try {
                    moe.zako.viewer.core.player.MultiNodeStreamBuffer.getInstance(context).clearCache()
                } catch (_: Exception) {}

                // 3. context.cacheDir 彻底递归清理子文件（保留目录本身，严格排除已下载视频目录）
                deleteDirContents(context.cacheDir, downloadDir)

                // 4. context.externalCacheDir 彻底递归清理子文件（保留目录本身，严格排除已下载视频目录）
                deleteDirContents(context.externalCacheDir, downloadDir)

                // 5. 刷新缓存显示为 0.00 B
                _uiState.update { it.copy(cacheSizeFormatted = "0.00 B") }

                launch(Dispatchers.Main) {
                    ZakoToast.show("应用缓存已彻底清理")
                }
            } catch (e: Exception) {
                ZakoLog.e("SettingsViewModel", "Failed to clear app cache", e)
                calculateCacheSize(context)
            }
        }
    }

    private fun getFolderSize(file: File?, excludedDir: File?): Long {
        if (file == null || !file.exists()) return 0L
        val canonical = try { file.canonicalFile } catch (_: Exception) { file.absoluteFile }
        if (excludedDir != null && (canonical == excludedDir || canonical.startsWith(excludedDir))) {
            return 0L
        }
        if (file.isFile) {
            if (file.name == "temp_restore_file") return 0L
            return file.length()
        }
        var size = 0L
        val files = file.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) getFolderSize(f, excludedDir) else {
                if (f.name == "temp_restore_file") 0L else f.length()
            }
        }
        return size
    }

    private fun deleteDirContents(dir: File?, excludedDir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        val canonical = try { dir.canonicalFile } catch (_: Exception) { dir.absoluteFile }
        if (excludedDir != null && (canonical == excludedDir || canonical.startsWith(excludedDir))) {
            return
        }
        dir.listFiles()?.forEach { file ->
            val fileCanonical = try { file.canonicalFile } catch (_: Exception) { file.absoluteFile }
            if (excludedDir != null && (fileCanonical == excludedDir || fileCanonical.startsWith(excludedDir))) {
                return@forEach
            }
            if (file.isDirectory) {
                deleteDirContents(file, excludedDir)
                file.delete()
            } else {
                file.delete()
            }
        }
    }

    private fun formatBytes(bytes: Long): String = StorageConstants.formatBytes(bytes)
}
