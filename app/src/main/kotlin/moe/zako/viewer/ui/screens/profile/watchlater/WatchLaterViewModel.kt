package moe.zako.viewer.ui.screens.profile.watchlater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
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
    val isSelectionMode: Boolean = false,
    val selectedVideoIds: Set<String> = emptySet(),
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
                _uiState.update { current ->
                    // 如果列表变更，同步剔除已不存在的选中项
                    val currentIds = items.map { it.videoId }.toSet()
                    val validSelected = current.selectedVideoIds.filter { currentIds.contains(it) }.toSet()
                    current.copy(
                        list = items,
                        selectedVideoIds = validSelected,
                        isSelectionMode = if (items.isEmpty()) false else current.isSelectionMode
                    )
                }
            }
        }
    }

    // --- 多选状态管理 ---
    fun toggleSelectionMode() {
        _uiState.update {
            it.copy(isSelectionMode = !it.isSelectionMode, selectedVideoIds = emptySet())
        }
    }

    fun enterSelectionWithItem(videoId: String) {
        _uiState.update {
            it.copy(isSelectionMode = true, selectedVideoIds = setOf(videoId))
        }
    }

    fun exitSelectionMode() {
        _uiState.update {
            it.copy(isSelectionMode = false, selectedVideoIds = emptySet())
        }
    }

    fun toggleSelection(videoId: String) {
        _uiState.update { current ->
            val set = current.selectedVideoIds.toMutableSet()
            if (set.contains(videoId)) set.remove(videoId) else set.add(videoId)
            current.copy(selectedVideoIds = set)
        }
    }

    fun toggleSelectAll() {
        _uiState.update { current ->
            val allIds = current.list.map { it.videoId }.toSet()
            val isAllSelected = current.selectedVideoIds.containsAll(allIds) && allIds.isNotEmpty()
            current.copy(selectedVideoIds = if (isAllSelected) emptySet() else allIds)
        }
    }

    // --- 单项与批量删除 ---
    fun remove(videoId: String) {
        viewModelScope.launch {
            historyRepository.removeFromWatchLater(videoId)
            ZakoToast.show("已从稍后再看中移除")
        }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedVideoIds.toList()
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                historyRepository.removeFromWatchLaterList(ids)
                _uiState.update { it.copy(selectedVideoIds = emptySet(), isSelectionMode = false) }
                ZakoToast.show("已移出 ${ids.size} 项稍后再看")
            }
        }
    }

    // --- 收藏夹互操作（单项 & 批量）---
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

    fun showBatchAddToFavoritesDialog() {
        viewModelScope.launch {
            val folders = favoriteRepository.getAllFolders()
            _uiState.update {
                it.copy(
                    showFolderSelector = true,
                    allFolders = folders,
                    pendingVideo = null
                )
            }
        }
    }

    fun dismissFolderSelector() {
        _uiState.update { it.copy(showFolderSelector = false, pendingVideo = null) }
    }

    fun onFolderSelected(folderId: Long) {
        val single = _uiState.value.pendingVideo
        if (single != null) {
            viewModelScope.launch {
                favoriteRepository.addToFavorites(
                    folderId = folderId,
                    videoId = single.videoId,
                    title = single.title,
                    coverUrl = single.coverUrl,
                    duration = single.duration,
                    isVip = single.isVip
                )
                _uiState.update { it.copy(showFolderSelector = false, pendingVideo = null) }
                ZakoToast.show("已成功加入收藏夹")
            }
        } else {
            batchAddToFavorites(folderId)
        }
    }

    private fun batchAddToFavorites(targetFolderId: Long) {
        val selectedIds = _uiState.value.selectedVideoIds
        val selectedItems = _uiState.value.list.filter { selectedIds.contains(it.videoId) }
        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            val favoriteItems = selectedItems.map {
                FavoriteItemEntity(
                    folderId = targetFolderId,
                    videoId = it.videoId,
                    title = it.title,
                    coverUrl = it.coverUrl,
                    duration = it.duration,
                    isVip = it.isVip,
                    addedAt = System.currentTimeMillis()
                )
            }
            favoriteRepository.batchCopy(favoriteItems, targetFolderId)
            _uiState.update {
                it.copy(
                    showFolderSelector = false,
                    isSelectionMode = false,
                    selectedVideoIds = emptySet(),
                    pendingVideo = null
                )
            }
            ZakoToast.show("已将 ${selectedItems.size} 个视频加入收藏夹")
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
