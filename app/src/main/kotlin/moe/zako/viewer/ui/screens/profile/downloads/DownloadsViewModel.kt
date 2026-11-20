package moe.zako.viewer.ui.screens.profile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class DownloadsUiState(
    val selectedTab: Int = 0, // 0 = 已下载, 1 = 下载中
    val completedList: List<DownloadEntity> = emptyList(),
    val activeList: List<DownloadEntity> = emptyList(),
    val currentStoragePath: String = "",
    val isMultiSelectMode: Boolean = false,
    val selectedIds: Set<String> = emptySet()
)

class DownloadsViewModel(
    private val downloadManager: DownloadManager,
    private val settingsRepository: SettingsRepository
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
        viewModelScope.launch {
            settingsRepository.setDownloadPath(newPath)
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
}
