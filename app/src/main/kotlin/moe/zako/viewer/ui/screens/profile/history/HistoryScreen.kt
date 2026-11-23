package moe.zako.viewer.ui.screens.profile.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.LocalFileInfo
import moe.zako.viewer.ui.components.LocalFileInfoDialog
import moe.zako.viewer.ui.components.MultiSelectTopBar
import moe.zako.viewer.ui.components.VideoActionBottomSheet
import moe.zako.viewer.ui.components.VideoListItemCard
import moe.zako.viewer.ui.components.VideoMenuAction
import moe.zako.viewer.ui.components.ZakoSearchBar
import moe.zako.viewer.ui.components.clearFocusOnScroll
import moe.zako.viewer.ui.components.clearFocusOnTap
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.SearchBarTokens
import moe.zako.viewer.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (videoId: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 长按上下文操作菜单状态
    var menuTargetItem by remember { mutableStateOf<WatchHistoryEntity?>(null) }
    var localInfoForTarget by remember { mutableStateOf<LocalFileInfo?>(null) }
    var showFileInfoDialog by remember { mutableStateOf(false) }

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

    // Group history items by calendar day
    val groupedHistory = remember(state.historyList) {
        state.historyList.groupBy { it.dayGroupString }
    }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                val allSelected = state.historyList.isNotEmpty() && state.historyList.all { state.selectedVideoIds.contains(it.videoId) }
                MultiSelectTopBar(
                    selectedCount = state.selectedVideoIds.size,
                    onExitSelection = { viewModel.toggleSelectionMode() },
                    allSelected = allSelected,
                    onToggleSelectAll = { viewModel.toggleSelectAll() }
                )
            } else {
                TopAppBar(
                    title = { Text("历史记录") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = AppIcons.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Icon(imageVector = AppIcons.MultiSelect, contentDescription = "多选")
                        }
                        IconButton(onClick = { viewModel.clearAllHistory() }) {
                            Icon(imageVector = AppIcons.DeleteSweep, contentDescription = "清空记录")
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
                        TextButton(onClick = { viewModel.showAddToFavoritesDialog() }) {
                            Icon(imageVector = AppIcons.AddToFavorites, contentDescription = null)
                            Spacer(modifier = Modifier.width(Spacing.Xs))
                            Text("加入收藏夹")
                        }
                        TextButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(imageVector = AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(Spacing.Xs))
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clearFocusOnScroll()
                .clearFocusOnTap()
        ) {
            // Search Bar (复用全局统一搜索框)
            ZakoSearchBar(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SearchBarTokens.DefaultHorizontalPadding, vertical = SearchBarTokens.DefaultVerticalPadding)
            )

            if (state.historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无历史记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.Lg)
                ) {
                    groupedHistory.forEach { (dayHeader, itemsInDay) ->
                        // Natural Day Header (按天隔离分组展示)
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = dayHeader,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Compact)
                                )
                            }
                        }

                        items(itemsInDay, key = { it.videoId }) { historyItem ->
                            val isSelected = state.selectedVideoIds.contains(historyItem.videoId)

                            HistoryRowItem(
                                item = historyItem,
                                isSelectionMode = state.isSelectionMode,
                                isSelected = isSelected,
                                onSelectToggle = { viewModel.toggleSelection(historyItem.videoId) },
                                onClick = {
                                    if (state.isSelectionMode) {
                                        viewModel.toggleSelection(historyItem.videoId)
                                    } else {
                                        onNavigateToDetail(historyItem.videoId)
                                    }
                                },
                                onLongClick = {
                                    if (state.isSelectionMode) {
                                        viewModel.toggleSelection(historyItem.videoId)
                                    } else {
                                        menuTargetItem = historyItem
                                    }
                                },
                                isLocalCached = state.downloadedVideoIds.contains(historyItem.videoId)
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.Lg))
                        }
                    }
                }
            }
        }
    }

    // 全局长按上下文操作菜单
    if (menuTargetItem != null) {
        val target = menuTargetItem!!
        val actions = buildList {
            add(
                VideoMenuAction(
                    id = "multi_select",
                    title = "多选",
                    icon = AppIcons.MultiSelect,
                    onClick = {
                        viewModel.enterSelectionWithItem(target.videoId)
                    }
                )
            )
            add(
                VideoMenuAction(
                    id = "add_to_favorites",
                    title = "添加到收藏夹",
                    icon = AppIcons.AddToFavorites,
                    onClick = {
                        viewModel.showAddToFavoritesForSingleItem(target)
                    }
                )
            )
            add(
                VideoMenuAction(
                    id = "detail",
                    title = "视频详情",
                    icon = AppIcons.VideoDetail,
                    onClick = {
                        onNavigateToDetail(target.videoId)
                    }
                )
            )
            if (localInfoForTarget != null) {
                add(
                    VideoMenuAction(
                        id = "file_info",
                        title = "文件信息",
                        icon = AppIcons.FileInfo,
                        supportingText = "查看路径、大小与本地状态",
                        onClick = {
                            showFileInfoDialog = true
                        }
                    )
                )
            }
            add(
                VideoMenuAction(
                    id = "watch_later",
                    title = "稍后再看",
                    icon = AppIcons.WatchLater,
                    onClick = {
                        viewModel.addToWatchLater(target)
                    }
                )
            )
            add(
                VideoMenuAction(
                    id = "delete",
                    title = "删除记录",
                    icon = AppIcons.Delete,
                    isDangerous = true,
                    onClick = {
                        viewModel.deleteHistory(target.videoId)
                    }
                )
            )
        }

        VideoActionBottomSheet(
            title = target.title,
            actions = actions,
            onDismissRequest = { menuTargetItem = null }
        )
    }

    if (showFileInfoDialog && localInfoForTarget != null) {
        LocalFileInfoDialog(
            info = localInfoForTarget!!,
            onDismiss = { showFileInfoDialog = false }
        )
    }

    if (state.showFolderSelector) {
        FolderTreeDialog(
            folders = state.allFolders,
            onDismiss = { viewModel.dismissFolderDialog() },
            onFolderSelected = { targetId -> viewModel.onFolderSelected(targetId) },
            onCreateFolder = { name, parentId -> viewModel.createFolder(name, parentId) }
        )
    }
}

@Composable
fun HistoryRowItem(
    item: WatchHistoryEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isLocalCached: Boolean = false
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
        onClick = {
            if (isSelectionMode) onSelectToggle() else onClick()
        },
        onLongClick = onLongClick,
        modifier = Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Xxs),
        subtitle = {
            Text(
                text = "观看时间: ${item.timeExactString}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    )
}
