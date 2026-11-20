package moe.zako.viewer.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.core.logger.NeoLog
import moe.zako.viewer.data.local.dao.DownloadDao
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.data.repository.FavoriteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocalVideoInfo(
    val title: String,
    val filePath: String,
    val fileSizeFormatted: String,
    val createdAtFormatted: String,
    val duration: String
)

data class FavoritesUiState(
    val currentFolder: FavoriteFolderEntity? = null,
    val breadcrumbs: List<FavoriteFolderEntity> = emptyList(),
    val subFolders: List<FavoriteFolderEntity> = emptyList(),
    val items: List<FavoriteItemEntity> = emptyList(),
    val allFolders: List<FavoriteFolderEntity> = emptyList(),
    val searchQuery: String = "",
    val isSelectionMode: Boolean = false,
    val selectedItemIds: Set<Long> = emptySet(),
    val selectedFolderIds: Set<Long> = emptySet(),
    val showFolderSelectorForMove: Boolean = false,
    val showFolderSelectorForCopy: Boolean = false,
    val singleItemToMove: Long? = null,
    val singleItemToCopy: FavoriteItemEntity? = null,
    val singleFolderToMove: Long? = null
) {
    val totalSelectedCount: Int
        get() = selectedItemIds.size + selectedFolderIds.size
}

class FavoritesViewModel(
    private val favoriteRepository: FavoriteRepository,
    private val downloadDao: DownloadDao
) : ViewModel() {
    companion object {
        private const val TAG = "FavoritesViewModel"
    }

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val root = favoriteRepository.ensureDefaultRootFolderExists()
            _uiState.update { it.copy(currentFolder = root, breadcrumbs = listOf(root)) }
            loadAllFolders()
        }
        observeCurrentFolderContent()
    }

    private fun loadAllFolders() {
        viewModelScope.launch {
            favoriteRepository.getRootFolders().collect {
                val all = favoriteRepository.getAllFolders()
                _uiState.update { state -> state.copy(allFolders = all) }
            }
        }
    }

    private fun observeCurrentFolderContent() {
        viewModelScope.launch {
            _uiState.map { it.currentFolder?.id }.distinctUntilChanged().collectLatest { folderId ->
                if (folderId != null) {
                    launch {
                        favoriteRepository.getSubFolders(folderId).collect { subs ->
                            _uiState.update { it.copy(subFolders = subs) }
                        }
                    }
                    launch {
                        _uiState.map { it.searchQuery }.distinctUntilChanged().collectLatest { query ->
                            val flow = if (query.isEmpty()) {
                                favoriteRepository.getItemsInFolder(folderId)
                            } else {
                                favoriteRepository.searchItemsInFolder(folderId, query)
                            }
                            flow.collect { itemList ->
                                _uiState.update { it.copy(items = itemList) }
                            }
                        }
                    }
                }
            }
        }
    }

    fun openFolder(folder: FavoriteFolderEntity) {
        val newBreadcrumbs = _uiState.value.breadcrumbs + folder
        _uiState.update {
            it.copy(
                currentFolder = folder,
                breadcrumbs = newBreadcrumbs,
                searchQuery = "",
                isSelectionMode = false,
                selectedItemIds = emptySet(),
                selectedFolderIds = emptySet()
            )
        }
    }

    fun navigateToBreadcrumb(targetFolder: FavoriteFolderEntity) {
        val index = _uiState.value.breadcrumbs.indexOfFirst { it.id == targetFolder.id }
        if (index >= 0) {
            val newBreadcrumbs = _uiState.value.breadcrumbs.take(index + 1)
            _uiState.update {
                it.copy(
                    currentFolder = targetFolder,
                    breadcrumbs = newBreadcrumbs,
                    searchQuery = "",
                    isSelectionMode = false,
                    selectedItemIds = emptySet(),
                    selectedFolderIds = emptySet()
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun createFolder(name: String, parentId: Long?) {
        viewModelScope.launch {
            val parent = parentId ?: _uiState.value.currentFolder?.id
            favoriteRepository.createFolder(name, parent)
            loadAllFolders()
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        viewModelScope.launch {
            favoriteRepository.renameFolder(folderId, newName)
            if (_uiState.value.currentFolder?.id == folderId) {
                _uiState.update {
                    it.copy(
                        currentFolder = it.currentFolder?.copy(name = newName),
                        breadcrumbs = it.breadcrumbs.map { b -> if (b.id == folderId) b.copy(name = newName) else b }
                    )
                }
            }
            loadAllFolders()
        }
    }

    fun renameItem(itemId: Long, newTitle: String) {
        viewModelScope.launch {
            favoriteRepository.renameItem(itemId, newTitle)
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            favoriteRepository.deleteFolder(folderId)
            loadAllFolders()
        }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            favoriteRepository.deleteItem(itemId)
        }
    }

    // --- Selection & Batch Operations ---
    fun toggleSelectionMode() {
        _uiState.update {
            it.copy(
                isSelectionMode = !it.isSelectionMode,
                selectedItemIds = emptySet(),
                selectedFolderIds = emptySet()
            )
        }
    }

    fun startSelectionWithFolder(folderId: Long) {
        _uiState.update {
            it.copy(
                isSelectionMode = true,
                selectedFolderIds = setOf(folderId),
                selectedItemIds = emptySet()
            )
        }
    }

    fun startSelectionWithItem(itemId: Long) {
        _uiState.update {
            it.copy(
                isSelectionMode = true,
                selectedItemIds = setOf(itemId),
                selectedFolderIds = emptySet()
            )
        }
    }

    fun toggleFolderSelection(folderId: Long) {
        _uiState.update { current ->
            val set = current.selectedFolderIds.toMutableSet()
            if (set.contains(folderId)) set.remove(folderId) else set.add(folderId)
            current.copy(selectedFolderIds = set)
        }
    }

    fun toggleItemSelection(itemId: Long) {
        _uiState.update { current ->
            val set = current.selectedItemIds.toMutableSet()
            if (set.contains(itemId)) set.remove(itemId) else set.add(itemId)
            current.copy(selectedItemIds = set)
        }
    }

    fun selectAll() {
        _uiState.update { current ->
            val selectableFolderIds = current.subFolders.filter { it.parentId != null }.map { it.id }.toSet()
            current.copy(
                selectedItemIds = current.items.map { it.id }.toSet(),
                selectedFolderIds = selectableFolderIds
            )
        }
    }

    fun batchDeleteSelected() {
        viewModelScope.launch {
            val itemIds = _uiState.value.selectedItemIds.toList()
            val folderIds = _uiState.value.selectedFolderIds.toList()
            if (itemIds.isNotEmpty()) {
                favoriteRepository.deleteItems(itemIds)
            }
            if (folderIds.isNotEmpty()) {
                favoriteRepository.batchDeleteFolders(folderIds)
            }
            _uiState.update {
                it.copy(
                    selectedItemIds = emptySet(),
                    selectedFolderIds = emptySet(),
                    isSelectionMode = false
                )
            }
            loadAllFolders()
        }
    }

    fun showMoveDialogForSelection() {
        _uiState.update {
            it.copy(
                showFolderSelectorForMove = true,
                singleItemToMove = null,
                singleFolderToMove = null
            )
        }
    }

    fun showMoveDialogForSingleItem(itemId: Long) {
        _uiState.update {
            it.copy(
                showFolderSelectorForMove = true,
                singleItemToMove = itemId,
                singleFolderToMove = null
            )
        }
    }

    fun showMoveDialogForSingleFolder(folderId: Long) {
        _uiState.update {
            it.copy(
                showFolderSelectorForMove = true,
                singleItemToMove = null,
                singleFolderToMove = folderId
            )
        }
    }

    fun dismissMoveDialog() {
        _uiState.update {
            it.copy(
                showFolderSelectorForMove = false,
                singleItemToMove = null,
                singleFolderToMove = null
            )
        }
    }

    fun executeMove(targetFolderId: Long) {
        viewModelScope.launch {
            val singleItem = _uiState.value.singleItemToMove
            val singleFolder = _uiState.value.singleFolderToMove
            when {
                singleItem != null -> {
                    favoriteRepository.moveItem(singleItem, targetFolderId)
                }
                singleFolder != null -> {
                    favoriteRepository.batchMoveFolders(listOf(singleFolder), targetFolderId)
                }
                else -> {
                    val itemIds = _uiState.value.selectedItemIds.toList()
                    val folderIds = _uiState.value.selectedFolderIds.toList()
                    if (itemIds.isNotEmpty()) {
                        favoriteRepository.batchMove(itemIds, targetFolderId)
                    }
                    if (folderIds.isNotEmpty()) {
                        favoriteRepository.batchMoveFolders(folderIds, targetFolderId)
                    }
                }
            }
            _uiState.update {
                it.copy(
                    selectedItemIds = emptySet(),
                    selectedFolderIds = emptySet(),
                    isSelectionMode = false,
                    showFolderSelectorForMove = false,
                    singleItemToMove = null,
                    singleFolderToMove = null
                )
            }
            loadAllFolders()
        }
    }

    fun showCopyDialogForSelection() {
        _uiState.update {
            it.copy(
                showFolderSelectorForCopy = true,
                singleItemToCopy = null
            )
        }
    }

    fun showCopyDialogForSingleItem(item: FavoriteItemEntity) {
        _uiState.update {
            it.copy(
                showFolderSelectorForCopy = true,
                singleItemToCopy = item
            )
        }
    }

    fun dismissCopyDialog() {
        _uiState.update {
            it.copy(
                showFolderSelectorForCopy = false,
                singleItemToCopy = null
            )
        }
    }

    fun executeCopy(targetFolderId: Long) {
        viewModelScope.launch {
            val singleItem = _uiState.value.singleItemToCopy
            if (singleItem != null) {
                favoriteRepository.copyItem(singleItem, targetFolderId)
            } else {
                val selected = _uiState.value.items.filter { _uiState.value.selectedItemIds.contains(it.id) }
                if (selected.isNotEmpty()) {
                    favoriteRepository.batchCopy(selected, targetFolderId)
                }
            }
            _uiState.update {
                it.copy(
                    selectedItemIds = emptySet(),
                    selectedFolderIds = emptySet(),
                    isSelectionMode = false,
                    showFolderSelectorForCopy = false,
                    singleItemToCopy = null
                )
            }
            loadAllFolders()
        }
    }

    suspend fun checkLocalVideoInfo(item: FavoriteItemEntity): LocalVideoInfo? {
        val download = downloadDao.getDownloadByVideoId(item.videoId)
        if (download != null && download.status == DownloadEntity.STATUS_COMPLETED) {
            val file = File(download.savePath)
            if (file.exists()) {
                val sizeStr = when {
                    file.length() > 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f GB", file.length() / (1024.0 * 1024.0 * 1024.0))
                    file.length() > 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", file.length() / (1024.0 * 1024.0))
                    else -> String.format(Locale.getDefault(), "%.1f KB", file.length() / 1024.0)
                }
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val completed = download.completedAt ?: 0L
                val timeMs = if (completed > 0L) completed else file.lastModified()
                return LocalVideoInfo(
                    title = item.title,
                    filePath = file.absolutePath,
                    fileSizeFormatted = sizeStr,
                    createdAtFormatted = sdf.format(Date(timeMs)),
                    duration = item.duration.ifEmpty { "未知" }
                )
            }
        }

        // Direct local file path check
        val directFile = File(item.videoId)
        if (directFile.exists() && directFile.isFile) {
            val sizeStr = String.format(Locale.getDefault(), "%.1f MB", directFile.length() / (1024.0 * 1024.0))
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return LocalVideoInfo(
                title = item.title,
                filePath = directFile.absolutePath,
                fileSizeFormatted = sizeStr,
                createdAtFormatted = sdf.format(Date(directFile.lastModified())),
                duration = item.duration.ifEmpty { "未知" }
            )
        }

        return null
    }
}
