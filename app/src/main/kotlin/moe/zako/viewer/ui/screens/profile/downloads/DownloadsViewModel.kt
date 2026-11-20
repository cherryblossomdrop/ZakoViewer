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
    val currentStoragePath: String = ""
)

class DownloadsViewModel(
    private val downloadManager: DownloadManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
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
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun pauseDownload(videoId: String) {
        downloadManager.pauseDownload(videoId)
    }

    fun deleteDownload(videoId: String) {
        downloadManager.deleteDownload(videoId)
    }

    fun updateCustomStoragePath(newPath: String) {
        viewModelScope.launch {
            settingsRepository.setDownloadPath(newPath)
        }
    }
}
