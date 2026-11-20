package moe.zako.viewer.ui.screens.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.core.external.ExternalLauncher
import moe.zako.viewer.core.logger.NeoLog
import moe.zako.viewer.core.logger.NeoLogger
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.repository.FavoriteRepository
import moe.zako.viewer.data.repository.HistoryRepository
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.data.source.cosav.CosApiClient
import moe.zako.viewer.data.source.cosav.CosVideoItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class VideoDetailUiState(
    val video: CosVideoItem? = null,
    val isLoading: Boolean = true,
    val isCommentsExpanded: Boolean = false, // 留言区默认处于折叠收起状态
    val isPreviewPlaying: Boolean = false,
    val isFavorite: Boolean = false,
    val isWatchLater: Boolean = false,
    val downloadStatus: String = "NONE", // "NONE", "DOWNLOADING", "COMPLETED"
    val isLongPressShareUnlocked: Boolean = false,
    val isLongPressShareEnabled: Boolean = false,
    val allFolders: List<FavoriteFolderEntity> = emptyList(),
    val showFolderSelector: Boolean = false,
    val showDownloadChoiceDialog: Boolean = false,
    val externalDownloaderPkg: String = "",
    val errorMessage: String? = null
)

class VideoDetailViewModel(
    private val videoId: String,
    private val apiClient: CosApiClient = CosApiClient.instance,
    private val favoriteRepository: FavoriteRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {
    companion object {
        private const val TAG = "VideoDetailViewModel"
    }

    private val _uiState = MutableStateFlow(VideoDetailUiState())
    val uiState: StateFlow<VideoDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
        observeDataSources()
        loadFolders()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val detail = apiClient.getVideoDetail(videoId)
                _uiState.update { it.copy(video = detail, isLoading = false) }

                // Record in watch history
                detail?.let {
                    historyRepository.recordWatch(
                        videoId = it.id,
                        title = it.title,
                        coverUrl = it.displayCover,
                        duration = it.duration ?: "",
                        isVip = it.isVipEarlyAccess
                    )
                }
            } catch (e: Exception) {
                NeoLogger.e(TAG, "Failed to load video detail for ID $videoId", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun observeDataSources() {
        // 观察收藏状态
        viewModelScope.launch {
            favoriteRepository.isFavoriteFlow(videoId).collect { isFav ->
                _uiState.update { it.copy(isFavorite = isFav) }
            }
        }

        // 观察稍后再看状态
        viewModelScope.launch {
            historyRepository.isWatchLaterFlow(videoId).collect { isLater ->
                _uiState.update { it.copy(isWatchLater = isLater) }
            }
        }

        // 观察下载状态 (NONE / DOWNLOADING / COMPLETED)
        viewModelScope.launch {
            downloadManager.getDownloadStatusFlow(videoId).collect { entity ->
                val status = when {
                    entity == null -> "NONE"
                    entity.status == "COMPLETED" -> "COMPLETED"
                    else -> "DOWNLOADING"
                }
                _uiState.update { it.copy(downloadStatus = status) }
            }
        }

        // 观察设置
        viewModelScope.launch {
            settingsRepository.externalDownloaderFlow.collect { pkg ->
                _uiState.update { it.copy(externalDownloaderPkg = pkg) }
            }
        }
        viewModelScope.launch {
            settingsRepository.longPressShareUnlockedFlow.collect { unlocked ->
                _uiState.update { it.copy(isLongPressShareUnlocked = unlocked) }
            }
        }
        viewModelScope.launch {
            settingsRepository.longPressShareEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(isLongPressShareEnabled = enabled) }
            }
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            val folders = favoriteRepository.getAllFolders()
            _uiState.update { it.copy(allFolders = folders) }
        }
    }

    fun toggleCommentsExpanded() {
        _uiState.update { it.copy(isCommentsExpanded = !it.isCommentsExpanded) }
    }

    fun togglePreview() {
        _uiState.update { it.copy(isPreviewPlaying = !it.isPreviewPlaying) }
    }

    fun onFavoriteClick() {
        viewModelScope.launch {
            if (_uiState.value.isFavorite) {
                favoriteRepository.removeFromFavorites(videoId)
            } else {
                _uiState.update { it.copy(showFolderSelector = true) }
            }
        }
    }

    fun toggleWatchLater() {
        viewModelScope.launch {
            val current = _uiState.value.video ?: return@launch
            if (_uiState.value.isWatchLater) {
                historyRepository.removeFromWatchLater(videoId)
            } else {
                historyRepository.addToWatchLater(
                    videoId = current.id,
                    title = current.title,
                    coverUrl = current.displayCover,
                    duration = current.duration ?: "",
                    isVip = current.isVipEarlyAccess
                )
            }
        }
    }

    fun showFavoriteFolderPicker() {
        loadFolders()
        _uiState.update { it.copy(showFolderSelector = true) }
    }

    fun dismissFolderPicker() {
        _uiState.update { it.copy(showFolderSelector = false) }
    }

    fun createFolder(name: String, parentId: Long?) {
        viewModelScope.launch {
            favoriteRepository.createFolder(name, parentId)
            loadFolders()
        }
    }

    fun addToFavoriteFolder(folderId: Long) {
        viewModelScope.launch {
            val current = _uiState.value.video ?: return@launch
            favoriteRepository.addToFavorites(
                folderId = folderId,
                videoId = current.id,
                title = current.title,
                coverUrl = current.displayCover,
                duration = current.duration ?: "",
                isVip = current.isVipEarlyAccess
            )
            _uiState.update { it.copy(showFolderSelector = false) }
        }
    }

    fun onDownloadClick() {
        startInternalDownload()
    }

    fun onDownloadLongClick() {
        // 长按：弹出选择菜单，允许选择内置下载器或配置的外部下载器
        _uiState.update { it.copy(showDownloadChoiceDialog = true) }
    }

    fun dismissDownloadChoice() {
        _uiState.update { it.copy(showDownloadChoiceDialog = false) }
    }

    fun startInternalDownload() {
        val current = _uiState.value.video ?: return
        val urls = current.allMp4Lines.ifEmpty { current.allVideoLines }
        downloadManager.enqueueDownload(
            videoId = current.id,
            title = current.title,
            coverUrl = current.displayCover,
            cdnUrls = urls
        )
        _uiState.update { it.copy(showDownloadChoiceDialog = false) }
    }

    fun startExternalDownload(context: Context) {
        val current = _uiState.value.video ?: return
        val targetUrl = current.allMp4Lines.firstOrNull() ?: current.allVideoLines.firstOrNull() ?: return
        ExternalLauncher.openExternalDownloader(
            context = context,
            downloadUrl = targetUrl,
            title = current.title,
            customPackage = _uiState.value.externalDownloaderPkg.ifEmpty { null }
        )
        _uiState.update { it.copy(showDownloadChoiceDialog = false) }
    }
}
