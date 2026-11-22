package moe.zako.viewer.ui.screens.profile.watchlater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.WatchLaterEntity
import moe.zako.viewer.data.repository.FavoriteRepository
import moe.zako.viewer.data.repository.HistoryRepository
import moe.zako.viewer.ui.components.ZakoToast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchLaterUiState(
    val list: List<WatchLaterEntity> = emptyList(),
    val showFolderSelector: Boolean = false,
    val allFolders: List<FavoriteFolderEntity> = emptyList(),
    val pendingVideo: WatchLaterEntity? = null
)

class WatchLaterViewModel(
    private val historyRepository: HistoryRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchLaterUiState())
    val uiState: StateFlow<WatchLaterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            historyRepository.getWatchLaterFlow().collect { items ->
                _uiState.update { it.copy(list = items) }
            }
        }
    }

    fun remove(videoId: String) {
        viewModelScope.launch {
            historyRepository.removeFromWatchLater(videoId)
            ZakoToast.show("已从稍后再看中移除")
        }
    }

    fun showAddToFavorites(item: WatchLaterEntity) {
        viewModelScope.launch {
            val folders = favoriteRepository.getAllFolders()
            _uiState.update {
                it.copy(
                    showFolderSelector = true,
                    allFolders = folders,
                    pendingVideo = item
                )
            }
        }
    }

    fun dismissFolderSelector() {
        _uiState.update { it.copy(showFolderSelector = false, pendingVideo = null) }
    }

    fun addToFavoriteFolder(folderId: Long) {
        val item = _uiState.value.pendingVideo ?: return
        viewModelScope.launch {
            favoriteRepository.addToFavorites(
                folderId = folderId,
                videoId = item.videoId,
                title = item.title,
                coverUrl = item.coverUrl,
                duration = item.duration,
                isVip = item.isVip
            )
            _uiState.update { it.copy(showFolderSelector = false, pendingVideo = null) }
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
