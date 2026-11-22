package moe.zako.viewer.ui.screens.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.LocalFileInfo
import moe.zako.viewer.ui.components.LocalFileInfoDialog
import moe.zako.viewer.ui.components.MultiSelectTopBar
import moe.zako.viewer.ui.components.ZakoSearchBar
import moe.zako.viewer.ui.components.clearFocusOnScroll
import moe.zako.viewer.ui.components.clearFocusOnTap
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.SearchBarTokens
import kotlinx.coroutines.launch

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
                    onToggleSelectAll = { viewModel.toggleSelectAll() }
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
        },
        bottomBar = {
            // Batch Action Bottom Bar
            if (state.isSelectionMode && state.totalSelectedCount > 0) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.showMoveDialogForSelection() }) {
                            Icon(imageVector = AppIcons.Move, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("移动 (${state.totalSelectedCount})")
                        }

                        TextButton(
                            onClick = { viewModel.showCopyDialogForSelection() },
                            enabled = state.selectedItemIds.isNotEmpty()
                        ) {
                            Icon(imageVector = AppIcons.Copy, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("复制 (${state.selectedItemIds.size})")
                        }

                        TextButton(onClick = { viewModel.batchDeleteSelected() }) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除 (${state.totalSelectedCount})", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clearFocusOnScroll()
                .clearFocusOnTap(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
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
                        .padding(horizontal = SearchBarTokens.DefaultHorizontalPadding, vertical = 2.dp)
                )
            }

            // 3. Subfolders Section
            if (state.searchQuery.isEmpty() && state.subFolders.isNotEmpty()) {
                item {
                    Text(
                        text = "子收藏夹 (${state.subFolders.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp)
                    )
                }

                items(state.subFolders, key = { "folder_${it.id}" }) { folder ->
                    FavoriteFolderItem(
                        folder = folder,
                        isSelectionMode = state.isSelectionMode,
                        isSelected = state.selectedFolderIds.contains(folder.id),
                        onFolderClick = { viewModel.openFolder(folder) },
                        onSelectToggle = { viewModel.toggleFolderSelection(folder.id) },
                        onMenuClick = { folderMenuTarget = folder }
                    )
                }
            }

            // Divider between folders and items
            if (state.subFolders.isNotEmpty() && state.items.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }

            // 4. Video Items Section
            if (state.items.isEmpty() && state.subFolders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
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
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp)
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
                        onMenuClick = { itemMenuTarget = item }
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
            onDelete = { fid ->
                folderMenuTarget = null
                viewModel.deleteFolder(fid)
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
            onDelete = { iid ->
                itemMenuTarget = null
                viewModel.deleteItem(iid)
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
}
