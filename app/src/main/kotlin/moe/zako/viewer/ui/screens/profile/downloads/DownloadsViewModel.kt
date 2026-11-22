package moe.zako.viewer.ui.screens.profile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.repository.FavoriteRepository
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.ui.components.ZakoToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class DownloadsUiState(
    val selectedTab: Int = 0, // 0 = 已下载, 1 = 下载中
    val completedList: List<DownloadEntity> = emptyList(),
    val activeList: List<DownloadEntity> = emptyList(),
    val currentStoragePath: String = "",
    val isMultiSelectMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val showMigrationDialog: Boolean = false,
    val isMigrating: Boolean = false,
    val pendingNewDownloadPath: String = "",
    val filesToMigrateCount: Int = 0,
    val migrationProgressText: String = "",
    val showFolderSelector: Boolean = false,
    val allFolders: List<FavoriteFolderEntity> = emptyList(),
    val pendingFavoriteItem: DownloadEntity? = null
)

class DownloadsViewModel(
    private val downloadManager: DownloadManager,
    private val settingsRepository: SettingsRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        downloadManager.reconcileLocalDownloadFiles()
        viewModelScope.launch {
            downloadManager.completedDownloadsFlow.collect { list ->
                _uiState.update { it.copy(completedList = list) }
            }
        }
        viewModelScope.launch {
            downloadManager.activeDownloadsFlow.collect { list ->
                _uiState.update { it.copy(activeList = list) }
            }
        }
        viewModelScope.launch {
            settingsRepository.downloadPathFlow.collect { path ->
                val actual = path.ifEmpty { downloadManager.getDownloadDir().absolutePath }
                _uiState.update { it.copy(currentStoragePath = actual) }
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex, isMultiSelectMode = false, selectedIds = emptySet()) }
    }

    fun pauseDownload(videoId: String) {
        downloadManager.pauseDownload(videoId)
    }

    fun resumeDownload(videoId: String) {
        downloadManager.resumeDownload(videoId)
    }

    fun redownload(videoId: String) {
        downloadManager.redownload(videoId)
    }

    fun deleteDownload(videoId: String) {
        downloadManager.deleteDownload(videoId)
    }

    fun updateCustomStoragePath(newPath: String) {
        requestChangeDownloadPath(newPath, _uiState.value.currentStoragePath)
    }

    fun requestChangeDownloadPath(newPath: String, currentDefaultPath: String) {
        val oldPath = _uiState.value.currentStoragePath.ifEmpty { currentDefaultPath }
        if (newPath == oldPath || newPath.isBlank()) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val completed = _uiState.value.completedList
            val targetCanonical = try { File(newPath).canonicalPath } catch (_: Exception) { File(newPath).absolutePath }
            val existingFiles = completed.filter { entity ->
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
                downloadManager.migrateDownloadsDirectory(newPath) { _, _, _ -> }
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
            val result = downloadManager.migrateDownloadsDirectory(newPath) { current, total, fileName ->
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
                downloadManager.reconcileLocalDownloadFiles()
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

    // --- 多选模式管理 ---
    fun enterMultiSelect(initialId: String? = null) {
        _uiState.update {
            it.copy(
                isMultiSelectMode = true,
                selectedIds = if (initialId != null) setOf(initialId) else emptySet()
            )
        }
    }

    fun exitMultiSelect() {
        _uiState.update { it.copy(isMultiSelectMode = false, selectedIds = emptySet()) }
    }

    fun toggleSelect(videoId: String) {
        _uiState.update {
            val newSet = it.selectedIds.toMutableSet()
            if (newSet.contains(videoId)) {
                newSet.remove(videoId)
            } else {
                newSet.add(videoId)
            }
            it.copy(selectedIds = newSet)
        }
    }

    fun toggleSelectAll() {
        val currentTabItems = if (_uiState.value.selectedTab == 0) _uiState.value.completedList else _uiState.value.activeList
        val currentIds = currentTabItems.map { it.videoId }.toSet()
        _uiState.update {
            val allSelected = it.selectedIds.containsAll(currentIds)
            it.copy(selectedIds = if (allSelected) emptySet() else currentIds)
        }
    }

    fun deleteSelected() {
        val idsToDelete = _uiState.value.selectedIds.toList()
        if (idsToDelete.isNotEmpty()) {
            downloadManager.deleteDownloads(idsToDelete)
            exitMultiSelect()
        }
    }

    // --- 收藏夹互操作 ---
    fun showAddToFavorites(item: DownloadEntity) {
        viewModelScope.launch {
            val folders = favoriteRepository.getAllFolders()
            _uiState.update {
                it.copy(
                    showFolderSelector = true,
                    allFolders = folders,
                    pendingFavoriteItem = item
                )
            }
        }
    }

    fun dismissFolderSelector() {
        _uiState.update { it.copy(showFolderSelector = false, pendingFavoriteItem = null) }
    }

    fun addToFavoriteFolder(folderId: Long) {
        val item = _uiState.value.pendingFavoriteItem ?: return
        viewModelScope.launch {
            favoriteRepository.addToFavorites(
                folderId = folderId,
                videoId = item.videoId,
                title = item.title,
                coverUrl = item.coverUrl,
                duration = "",
                isVip = false
            )
            _uiState.update { it.copy(showFolderSelector = false, pendingFavoriteItem = null) }
            ZakoToast.show("已成功加入收藏夹")
        }
    }

    fun createFolder(name: String, parentId: Long?) {
        viewModelScope.launch {
            favoriteRepository.createFolder(name, parentId)
            val folders = favoriteRepository.getAllFolders()
            _uiState.update { it.copy(allFolders = folders) }
        }
    }
}
