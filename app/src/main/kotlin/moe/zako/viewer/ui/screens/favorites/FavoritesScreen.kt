package moe.zako.viewer.ui.screens.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.ui.components.BaseBatchMenuAction
import moe.zako.viewer.ui.components.BatchMenuAction
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.LocalFileInfo
import moe.zako.viewer.ui.components.LocalFileInfoDialog
import moe.zako.viewer.ui.components.MultiSelectTopBar
import moe.zako.viewer.ui.components.StandardBatchActionMenu
import moe.zako.viewer.ui.components.ZakoSearchBar
import moe.zako.viewer.ui.components.clearFocusOnScroll
import moe.zako.viewer.ui.components.clearFocusOnTap
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.SearchBarTokens
import moe.zako.viewer.ui.theme.Spacing

/**
 * 收藏夹树状目录管理视窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onNavigateToPlayer: (videoId: String) -> Unit,
    onNavigateToDetail: (videoId: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    // Dialog & Menu States
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FavoriteFolderEntity?>(null) }
    var itemToRename by remember { mutableStateOf<FavoriteItemEntity?>(null) }
    var folderMenuTarget by remember { mutableStateOf<FavoriteFolderEntity?>(null) }
    var itemMenuTarget by remember { mutableStateOf<FavoriteItemEntity?>(null) }
    var localVideoInfoToShow by remember { mutableStateOf<LocalFileInfo?>(null) }
    var localInfoForTarget by remember { mutableStateOf<LocalFileInfo?>(null) }

    // 删除确认弹窗状态
    var folderToDelete by remember { mutableStateOf<FavoriteFolderEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<FavoriteItemEntity?>(null) }
    var showBatchDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(itemMenuTarget) {
        localInfoForTarget = null
        val target = itemMenuTarget ?: return@LaunchedEffect
        localInfoForTarget = viewModel.checkLocalVideoInfo(target)
    }

    val selectableFolderCount = state.subFolders.count { it.parentId != null }
    val isAllSelected = (selectableFolderCount + state.items.size) > 0 &&
            state.items.all { state.selectedItemIds.contains(it.id) } &&
            state.subFolders.filter { it.parentId != null }.all { state.selectedFolderIds.contains(it.id) }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                MultiSelectTopBar(
                    selectedCount = state.totalSelectedCount,
                    onExitSelection = { viewModel.toggleSelectionMode() },
                    allSelected = isAllSelected,
                    onToggleSelectAll = { viewModel.toggleSelectAll() },
                    menuContent = { expanded, onDismissRequest ->
                        when {
                            state.selectedFolderIds.isEmpty() && state.selectedItemIds.isNotEmpty() -> {
                                StandardBatchActionMenu(
                                    expanded = expanded,
                                    onDismissRequest = onDismissRequest,
                                    excluded = setOf(BaseBatchMenuAction.ADD_TO_FAVORITE),
                                    onAddToWatchLater = { viewModel.batchAddToWatchLater() },
                                    extraActions = listOf(
                                        BatchMenuAction(
                                            id = "move",
                                            title = "移动视频",
                                            icon = AppIcons.Move,
                                            onClick = { viewModel.showMoveDialogForSelection() }
                                        ),
                                        BatchMenuAction(
                                            id = "copy",
                                            title = "复制视频",
                                            icon = AppIcons.Copy,
                                            onClick = { viewModel.showCopyDialogForSelection() }
                                        )
                                    ),
                                    destructiveAction = BatchMenuAction(
                                        id = "delete",
                                        title = "移出收藏",
                                        icon = AppIcons.Delete,
                                        isDangerous = true,
                                        onClick = { showBatchDeleteConfirmDialog = true }
                                    )
                                )
                            }
                            state.selectedFolderIds.isNotEmpty() && state.selectedItemIds.isEmpty() -> {
                                StandardBatchActionMenu(
                                    expanded = expanded,
                                    onDismissRequest = onDismissRequest,
                                    excluded = setOf(
                                        BaseBatchMenuAction.ADD_TO_FAVORITE,
                                        BaseBatchMenuAction.ADD_TO_WATCH_LATER
                                    ),
                                    extraActions = listOf(
                                        BatchMenuAction(
                                            id = "move",
                                            title = "移动目录",
                                            icon = AppIcons.Move,
                                            onClick = { viewModel.showMoveDialogForSelection() }
                                        )
                                    ),
                                    destructiveAction = BatchMenuAction(
                                        id = "delete",
                                        title = "删除目录",
                                        icon = AppIcons.Delete,
                                        isDangerous = true,
                                        onClick = { showBatchDeleteConfirmDialog = true }
                                    )
                                )
                            }
                            else -> {
                                StandardBatchActionMenu(
                                    expanded = expanded,
                                    onDismissRequest = onDismissRequest,
                                    excluded = setOf(
                                        BaseBatchMenuAction.ADD_TO_FAVORITE,
                                        BaseBatchMenuAction.ADD_TO_WATCH_LATER
                                    ),
                                    extraActions = emptyList(),
                                    destructiveAction = BatchMenuAction(
                                        id = "delete",
                                        title = "批量删除",
                                        icon = AppIcons.Delete,
                                        isDangerous = true,
                                        onClick = { showBatchDeleteConfirmDialog = true }
                                    )
                                )
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = state.currentFolder?.name ?: "收藏夹",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Icon(imageVector = AppIcons.MultiSelect, contentDescription = "多选管理")
                        }
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(imageVector = AppIcons.CreateFolder, contentDescription = "新建子收藏夹")
                        }
                        // 重命名当前收藏夹（包含根收藏夹与子收藏夹）
                        state.currentFolder?.let { cur ->
                            IconButton(onClick = { folderToRename = cur }) {
                                Icon(imageVector = AppIcons.Rename, contentDescription = "重命名当前收藏夹")
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clearFocusOnScroll()
                .clearFocusOnTap(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
            contentPadding = PaddingValues(bottom = Spacing.Xl)
        ) {
            // 1. Breadcrumbs Bar
            item {
                BreadcrumbBar(
                    breadcrumbs = state.breadcrumbs,
                    onFolderClick = { folder -> viewModel.navigateToBreadcrumb(folder) }
                )
            }

            // 2. In-folder Search Bar (复用全局统一搜索框)
            item {
                ZakoSearchBar(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SearchBarTokens.DefaultHorizontalPadding, vertical = Spacing.Xxs)
                )
            }

            // 3. Subfolders Section
            if (state.searchQuery.isEmpty() && state.subFolders.isNotEmpty()) {
                item {
                    Text(
                        text = "子收藏夹 (${state.subFolders.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.Lg, top = Spacing.Compact, bottom = Spacing.Xxs)
                    )
                }

                items(state.subFolders, key = { "folder_${it.id}" }) { folder ->
                    FavoriteFolderItem(
                        folder = folder,
                        isSelectionMode = state.isSelectionMode,
                        isSelected = state.selectedFolderIds.contains(folder.id),
                        onFolderClick = { viewModel.openFolder(folder) },
                        onSelectToggle = { viewModel.toggleFolderSelection(folder.id) },
                        onMenuClick = { folderMenuTarget = folder },
                        onDeleteClick = { folderToDelete = folder }
                    )
                }
            }

            // Divider between folders and items
            if (state.subFolders.isNotEmpty() && state.items.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Xs))
                }
            }

            // 4. Video Items Section
            if (state.items.isEmpty() && state.subFolders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.Massive),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (state.searchQuery.isNotEmpty()) "未匹配到相关收藏视频" else "当前收藏夹为空",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (state.items.isNotEmpty()) {
                item {
                    Text(
                        text = if (state.searchQuery.isNotEmpty()) "递归检索结果 (${state.items.size})" else "视频列表 (${state.items.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.Lg, top = Spacing.Compact, bottom = Spacing.Xxs)
                    )
                }

                items(state.items, key = { "item_${it.id}" }) { item ->
                    val parentFolder = if (state.searchQuery.isNotEmpty() && item.folderId != state.currentFolder?.id) {
                        state.allFolders.firstOrNull { it.id == item.folderId }?.name
                    } else null

                    FavoriteVideoItem(
                        item = item,
                        isSelectionMode = state.isSelectionMode,
                        isSelected = state.selectedItemIds.contains(item.id),
                        isSearchMode = state.searchQuery.isNotEmpty(),
                        parentFolderName = parentFolder,
                        onPlayClick = { onNavigateToPlayer(item.videoId) },
                        onSelectToggle = { viewModel.toggleItemSelection(item.id) },
                        onMenuClick = { itemMenuTarget = item },
                        onDeleteClick = { itemToDelete = item },
                        isLocalCached = state.downloadedVideoIds.contains(item.videoId)
                    )
                }
            }
        }
    }

    // 1. 长按子收藏夹弹出菜单
    folderMenuTarget?.let { folder ->
        FolderActionBottomSheet(
            folder = folder,
            onStartSelection = { fid ->
                folderMenuTarget = null
                viewModel.startSelectionWithFolder(fid)
            },
            onRename = { f ->
                folderMenuTarget = null
                folderToRename = f
            },
            onMove = { fid ->
                folderMenuTarget = null
                viewModel.showMoveDialogForSingleFolder(fid)
            },
            onDelete = { _ ->
                val f = folder
                folderMenuTarget = null
                folderToDelete = f
            },
            onDismissRequest = { folderMenuTarget = null }
        )
    }

    // 2. 长按视频项目弹出菜单
    itemMenuTarget?.let { item ->
        FavoriteItemActionBottomSheet(
            item = item,
            onStartSelection = { iid ->
                itemMenuTarget = null
                viewModel.startSelectionWithItem(iid)
            },
            onRename = { itm ->
                itemMenuTarget = null
                itemToRename = itm
            },
            onMove = { iid ->
                itemMenuTarget = null
                viewModel.showMoveDialogForSingleItem(iid)
            },
            onCopy = { itm ->
                itemMenuTarget = null
                viewModel.showCopyDialogForSingleItem(itm)
            },
            onAddToWatchLater = { itm ->
                itemMenuTarget = null
                viewModel.addToWatchLater(itm)
            },
            onDetail = { itm ->
                itemMenuTarget = null
                onNavigateToDetail(itm.videoId)
            },
            hasLocalFile = localInfoForTarget != null,
            onShowFileInfo = {
                val info = localInfoForTarget
                itemMenuTarget = null
                localVideoInfoToShow = info
            },
            onDelete = { _ ->
                val itm = item
                itemMenuTarget = null
                itemToDelete = itm
            },
            onDismissRequest = { itemMenuTarget = null }
        )
    }

    // 3. 本地视频文件信息弹窗
    localVideoInfoToShow?.let { info ->
        LocalFileInfoDialog(
            info = info,
            onDismiss = { localVideoInfoToShow = null }
        )
    }

    // 4. 新建子收藏夹弹窗
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onConfirm = { name ->
                viewModel.createFolder(name, state.currentFolder?.id)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    // 5. 重命名收藏夹弹窗
    folderToRename?.let { folder ->
        RenameFolderDialog(
            initialName = folder.name,
            onConfirm = { newName ->
                viewModel.renameFolder(folder.id, newName)
                folderToRename = null
            },
            onDismiss = { folderToRename = null }
        )
    }

    // 6. 重命名视频标题弹窗
    itemToRename?.let { item ->
        RenameItemDialog(
            initialTitle = item.title,
            onConfirm = { newTitle ->
                viewModel.renameItem(item.id, newTitle)
                itemToRename = null
            },
            onDismiss = { itemToRename = null }
        )
    }

    // 7. 移动目标选择弹窗
    if (state.showFolderSelectorForMove) {
        FolderTreeDialog(
            folders = state.allFolders,
            onDismiss = { viewModel.dismissMoveDialog() },
            onFolderSelected = { targetId -> viewModel.executeMove(targetId) },
            onCreateFolder = { name, parentId -> viewModel.createFolder(name, parentId) }
        )
    }

    // 8. 复制目标选择弹窗
    if (state.showFolderSelectorForCopy) {
        FolderTreeDialog(
            folders = state.allFolders,
            onDismiss = { viewModel.dismissCopyDialog() },
            onFolderSelected = { targetId -> viewModel.executeCopy(targetId) },
            onCreateFolder = { name, parentId -> viewModel.createFolder(name, parentId) }
        )
    }

    // 9. 删除收藏夹二次确认弹窗
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            icon = { Icon(AppIcons.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认删除收藏夹") },
            text = { Text("确定要删除收藏夹「${folder.name}」及其包含的所有子收藏夹与视频条目吗？此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        val fid = folder.id
                        folderToDelete = null
                        viewModel.deleteFolder(fid)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text("取消操作")
                }
            }
        )
    }

    // 10. 移出收藏二次确认弹窗
    itemToDelete?.let { itm ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认移出收藏") },
            text = { Text("确定要将「${itm.title}」从当前收藏夹中移出吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        val iid = itm.id
                        itemToDelete = null
                        viewModel.deleteItem(iid)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("取消操作")
                }
            }
        )
    }

    // 11. 批量删除二次确认弹窗
    if (showBatchDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirmDialog = false },
            icon = { Icon(AppIcons.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认批量删除") },
            text = { Text("确定要删除所选的 ${state.totalSelectedCount} 个项目吗？若包含收藏夹，其下所有子内容也将一并删除。") },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteConfirmDialog = false
                        viewModel.batchDeleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirmDialog = false }) {
                    Text("取消操作")
                }
            }
        )
    }
}
