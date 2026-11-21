package moe.zako.viewer.ui.screens.profile.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.data.local.dao.DownloadDao
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.ui.components.ZakoToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

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
    val showMigrationDialog: Boolean = false,
    val pendingNewDownloadPath: String = "",
    val filesToMigrateCount: Int = 0,
    val isMigrating: Boolean = false,
    val migrationProgressText: String = "",
    val cacheSizeFormatted: String = "计算中..."
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val downloadDao: DownloadDao? = null
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
                    settingsRepository.longPressShareEnabledFlow
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

    fun requestChangeDownloadPath(newPath: String, currentDefaultPath: String) {
        val oldPath = _uiState.value.downloadPath.ifEmpty { currentDefaultPath }
        if (newPath == oldPath || newPath.isBlank()) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val dao = downloadDao
            val downloads = dao?.getAllDownloadsList() ?: emptyList()
            val existingFiles = downloads.filter { entity ->
                val f = File(entity.savePath)
                f.exists() && f.isFile && f.length() > 0
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
                settingsRepository.setDownloadPath(newPath)
            }
        }
    }

    fun confirmMigration() {
        val newPath = _uiState.value.pendingNewDownloadPath
        if (newPath.isBlank()) return
        val targetDir = File(newPath)
        if (!targetDir.exists()) targetDir.mkdirs()

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isMigrating = true,
                    migrationProgressText = "准备迁移..."
                )
            }
            val dao = downloadDao
            val downloads = dao?.getAllDownloadsList() ?: emptyList()
            val existingFiles = downloads.filter { entity ->
                val f = File(entity.savePath)
                f.exists() && f.isFile && f.length() > 0
            }
            var successCount = 0
            for ((idx, entity) in existingFiles.withIndex()) {
                val src = File(entity.savePath)
                val dest = File(targetDir, src.name)
                _uiState.update {
                    it.copy(
                        migrationProgressText = "正在迁移 (${idx + 1}/${existingFiles.size}): ${src.name}"
                    )
                }
                try {
                    src.copyTo(dest, overwrite = true)
                    if (dest.exists() && dest.length() == src.length()) {
                        src.delete()
                        dao?.updateSavePath(entity.videoId, dest.absolutePath)
                        successCount++
                    }
                } catch (e: Exception) {
                    ZakoLog.e("SettingsViewModel", "Failed to migrate ${src.name}", e)
                }
            }
            settingsRepository.setDownloadPath(newPath)
            _uiState.update {
                it.copy(
                    showMigrationDialog = false,
                    isMigrating = false,
                    pendingNewDownloadPath = "",
                    filesToMigrateCount = 0
                )
            }
            ZakoToast.show("已迁移 $successCount 个视频文件至新目录")
        }
    }

    fun skipMigrationAndSetPath() {
        val newPath = _uiState.value.pendingNewDownloadPath
        viewModelScope.launch {
            if (newPath.isNotBlank()) {
                settingsRepository.setDownloadPath(newPath)
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
            val totalSize = getFolderSize(context.cacheDir) + getFolderSize(context.externalCacheDir)
            _uiState.update { it.copy(cacheSizeFormatted = formatBytes(totalSize)) }
        }
    }

    fun clearAppCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
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

                // 3. context.cacheDir 彻底递归清理子文件（保留目录本身）
                deleteDirContents(context.cacheDir)

                // 4. context.externalCacheDir 彻底递归清理子文件（保留目录本身）
                deleteDirContents(context.externalCacheDir)

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

    private fun getFolderSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val files = file.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) getFolderSize(f) else f.length()
        }
        return size
    }

    private fun deleteDirContents(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteDirContents(file)
                file.delete()
            } else {
                file.delete()
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0.00 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val i = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(java.util.Locale.getDefault(), "%.2f %s", bytes / Math.pow(1024.0, i.toDouble()), units[i])
    }
}
