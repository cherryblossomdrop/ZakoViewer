package moe.zako.viewer.ui.screens.profile.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.data.repository.FavoriteRepository
import moe.zako.viewer.data.repository.HistoryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HistoryUiState(
    val historyList: List<WatchHistoryEntity> = emptyList(),
    val searchQuery: String = "",
    val isSelectionMode: Boolean = false,
    val selectedVideoIds: Set<String> = emptySet(),
    val allFolders: List<FavoriteFolderEntity> = emptyList(),
    val showFolderSelector: Boolean = false
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

    fun showAddToFavoritesDialog() {
        _uiState.update { it.copy(showFolderSelector = true) }
    }

    fun dismissFolderDialog() {
        _uiState.update { it.copy(showFolderSelector = false) }
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
                    showFolderSelector = false
                )
            }
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
