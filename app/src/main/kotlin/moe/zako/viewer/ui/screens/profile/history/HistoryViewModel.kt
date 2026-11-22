package moe.zako.viewer.ui.screens.profile.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.data.repository.FavoriteRepository
import moe.zako.viewer.data.repository.HistoryRepository
import moe.zako.viewer.ui.components.ZakoToast
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HistoryUiState(
    val historyList: List<WatchHistoryEntity> = emptyList(),
    val searchQuery: String = "",
    val isSelectionMode: Boolean = false,
    val selectedVideoIds: Set<String> = emptySet(),
    val allFolders: List<FavoriteFolderEntity> = emptyList(),
    val showFolderSelector: Boolean = false,
    val pendingSingleItem: WatchHistoryEntity? = null
)

class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        observeHistory()
        loadFolders()
    }

    private fun loadFolders() {
        viewModelScope.launch {
            val folders = favoriteRepository.getAllFolders()
            _uiState.update { it.copy(allFolders = folders) }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            _uiState.map { it.searchQuery }.distinctUntilChanged().collectLatest { query ->
                val flow = if (query.isEmpty()) {
                    historyRepository.getAllHistory()
                } else {
                    historyRepository.searchHistory(query)
                }
                flow.collect { list ->
                    _uiState.update { it.copy(historyList = list) }
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSelectionMode() {
        _uiState.update {
            it.copy(isSelectionMode = !it.isSelectionMode, selectedVideoIds = emptySet())
        }
    }

    fun toggleSelection(videoId: String) {
        _uiState.update { current ->
            val set = current.selectedVideoIds.toMutableSet()
            if (set.contains(videoId)) set.remove(videoId) else set.add(videoId)
            current.copy(selectedVideoIds = set)
        }
    }

    fun selectAll() {
        _uiState.update { current ->
            current.copy(selectedVideoIds = current.historyList.map { it.videoId }.toSet())
        }
    }

    fun enterSelectionWithItem(videoId: String) {
        _uiState.update {
            it.copy(
                isSelectionMode = true,
                selectedVideoIds = setOf(videoId)
            )
        }
    }

    fun showAddToFavoritesDialog() {
        _uiState.update { it.copy(showFolderSelector = true, pendingSingleItem = null) }
    }

    fun showAddToFavoritesForSingleItem(item: WatchHistoryEntity) {
        viewModelScope.launch {
            val folders = favoriteRepository.getAllFolders()
            _uiState.update {
                it.copy(
                    showFolderSelector = true,
                    allFolders = folders,
                    pendingSingleItem = item
                )
            }
        }
    }

    fun dismissFolderDialog() {
        _uiState.update { it.copy(showFolderSelector = false, pendingSingleItem = null) }
    }

    fun onFolderSelected(targetFolderId: Long) {
        val single = _uiState.value.pendingSingleItem
        if (single != null) {
            viewModelScope.launch {
                favoriteRepository.addToFavorites(
                    folderId = targetFolderId,
                    videoId = single.videoId,
                    title = single.title,
                    coverUrl = single.coverUrl,
                    duration = single.duration,
                    isVip = single.isVip
                )
                _uiState.update { it.copy(showFolderSelector = false, pendingSingleItem = null) }
                ZakoToast.show("已成功加入收藏夹")
            }
        } else {
            batchAddToFavorites(targetFolderId)
        }
    }

    fun batchAddToFavorites(targetFolderId: Long) {
        viewModelScope.launch {
            val selected = _uiState.value.historyList.filter {
                _uiState.value.selectedVideoIds.contains(it.videoId)
            }
            historyRepository.batchAddToFavorites(selected, targetFolderId)
            _uiState.update {
                it.copy(
                    selectedVideoIds = emptySet(),
                    isSelectionMode = false,
                    showFolderSelector = false,
                    pendingSingleItem = null
                )
            }
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

    fun addToWatchLater(item: WatchHistoryEntity) {
        viewModelScope.launch {
            historyRepository.addToWatchLater(
                videoId = item.videoId,
                title = item.title,
                coverUrl = item.coverUrl,
                duration = item.duration,
                isVip = item.isVip
            )
            ZakoToast.show("已添加到稍后再看")
        }
    }

    fun deleteHistory(videoId: String) {
        viewModelScope.launch {
            historyRepository.deleteHistory(videoId)
            ZakoToast.show("已删除历史记录")
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            historyRepository.deleteHistoryList(_uiState.value.selectedVideoIds.toList())
            _uiState.update { it.copy(selectedVideoIds = emptySet(), isSelectionMode = false) }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
        }
    }
}
