package moe.zako.viewer.ui.screens.profile.watchlater

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.data.local.entities.WatchLaterEntity
import moe.zako.viewer.ui.components.BaseVideoMenuAction
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.LocalFileInfo
import moe.zako.viewer.ui.components.LocalFileInfoDialog
import moe.zako.viewer.ui.components.MultiSelectTopBar
import moe.zako.viewer.ui.components.StandardVideoActionMenu
import moe.zako.viewer.ui.components.VideoListItemCard
import moe.zako.viewer.ui.components.VideoMenuAction
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WatchLaterScreen(
    viewModel: WatchLaterViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (videoId: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var menuTargetItem by remember { mutableStateOf<WatchLaterEntity?>(null) }
    var localInfoForTarget by remember { mutableStateOf<LocalFileInfo?>(null) }
    var showFileInfoDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var itemToRemove by remember { mutableStateOf<WatchLaterEntity?>(null) }

    LaunchedEffect(menuTargetItem) {
        localInfoForTarget = null
        val target = menuTargetItem ?: return@LaunchedEffect
        val local = withContext(Dispatchers.IO) {
            DownloadManager.getInstance(context).findLocalCompletedVideo(target.videoId)
        }
        if (local != null) {
            localInfoForTarget = LocalFileInfo.fromFile(local.file, target.title, target.duration)
        }
    }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                val allSelected = state.list.isNotEmpty() && state.list.all { state.selectedVideoIds.contains(it.videoId) }
                MultiSelectTopBar(
                    selectedCount = state.selectedVideoIds.size,
                    onExitSelection = { viewModel.exitSelectionMode() },
                    allSelected = allSelected,
                    onToggleSelectAll = { viewModel.toggleSelectAll() }
                )
            } else {
                TopAppBar(
                    title = { Text("稍后再看 (${state.list.size})") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = AppIcons.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (state.list.isNotEmpty()) {
                            IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                                Icon(imageVector = AppIcons.MultiSelect, contentDescription = "多选")
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (state.isSelectionMode && state.selectedVideoIds.isNotEmpty()) {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.showBatchAddToFavoritesDialog() }) {
                            Icon(imageVector = AppIcons.AddToFavorites, contentDescription = null)
                            Spacer(modifier = Modifier.width(Spacing.Xs))
                            Text("加入收藏夹 (${state.selectedVideoIds.size})")
                        }
                        TextButton(onClick = { showBatchDeleteDialog = true }) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(Spacing.Xs))
                            Text("移出稍后再看 (${state.selectedVideoIds.size})", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (state.list.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("稍后再看列表为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
            ) {
                items(state.list, key = { it.videoId }) { item ->
                    val isSelected = state.selectedVideoIds.contains(item.videoId)
                    WatchLaterItemCard(
                        item = item,
                        isSelectionMode = state.isSelectionMode,
                        isSelected = isSelected,
                        onSelectToggle = { viewModel.toggleSelection(item.videoId) },
                        onClick = {
                            if (state.isSelectionMode) {
                                viewModel.toggleSelection(item.videoId)
                            } else {
                                onNavigateToDetail(item.videoId)
                            }
                        },
                        onLongClick = {
                            if (state.isSelectionMode) {
                                viewModel.toggleSelection(item.videoId)
                            } else {
                                menuTargetItem = item
                            }
                        },
                        onRemove = { itemToRemove = item },
                        isLocalCached = state.downloadedVideoIds.contains(item.videoId)
                    )
                }
            }
        }
    }

    // 全局长按上下文操作菜单
    if (menuTargetItem != null) {
        val target = menuTargetItem!!
        StandardVideoActionMenu(
            title = target.title,
            onDismissRequest = { menuTargetItem = null },
            excluded = setOf(BaseVideoMenuAction.WATCH_LATER),
            onBatchSelect = {
                viewModel.enterSelectionWithItem(target.videoId)
            },
            onAddToFavorite = {
                viewModel.showAddToFavorites(target)
            },
            onVideoDetail = {
                onNavigateToDetail(target.videoId)
            },
            onFileInfo = if (localInfoForTarget != null) {
                { showFileInfoDialog = true }
            } else null,
            destructiveAction = VideoMenuAction(
                id = "remove_watch_later",
                title = "移出待看",
                icon = AppIcons.Delete,
                isDangerous = true,
                onClick = {
                    itemToRemove = target
                }
            )
        )
    }

    if (showFileInfoDialog && localInfoForTarget != null) {
        LocalFileInfoDialog(
            info = localInfoForTarget!!,
            onDismiss = { showFileInfoDialog = false }
        )
    }

    // 单条移出二次确认弹窗
    if (itemToRemove != null) {
        val target = itemToRemove!!
        AlertDialog(
            onDismissRequest = { itemToRemove = null },
            icon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认移出待看") },
            text = { Text("确定要将「${target.title}」从稍后再看列表中移除吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        val vid = target.videoId
                        itemToRemove = null
                        viewModel.remove(vid)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认移出")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRemove = null }) {
                    Text("取消操作")
                }
            }
        )
    }

    // 批量移出二次确认弹窗
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            icon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认移出稍后再看") },
            text = { Text("确定要将选中的 ${state.selectedVideoIds.size} 个视频从稍后再看列表中移除吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteDialog = false
                        viewModel.deleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认移出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("取消操作")
                }
            }
        )
    }

    if (state.showFolderSelector) {
        FolderTreeDialog(
            folders = state.allFolders,
            onDismiss = { viewModel.dismissFolderSelector() },
            onFolderSelected = { folderId -> viewModel.onFolderSelected(folderId) },
            onCreateFolder = { name, parentId -> viewModel.createFolder(name, parentId) }
        )
    }
}

/**
 * 稍后再看条目卡片 (复用全局统一 VideoListItemCard)
 */
@Composable
fun WatchLaterItemCard(
    item: WatchLaterEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
    isLocalCached: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coverModel = remember(item.videoId, item.coverUrl) {
        CoverCacheManager.getCoverModel(context, item.videoId, item.coverUrl, autoFetch = true)
    }

    VideoListItemCard(
        coverModel = coverModel,
        title = item.title,
        duration = item.duration,
        isLocalCached = isLocalCached,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected,
        onSelectToggle = onSelectToggle,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        actions = {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = AppIcons.DeleteOutline,
                    contentDescription = "移除"
                )
            }
        }
    )
}
