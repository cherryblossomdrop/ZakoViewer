package moe.zako.viewer.ui.screens.profile.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.ui.components.DurationBadge
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.LocalFileInfo
import moe.zako.viewer.ui.components.LocalFileInfoDialog
import moe.zako.viewer.ui.components.MultiSelectTopBar
import moe.zako.viewer.ui.components.VideoActionBottomSheet
import moe.zako.viewer.ui.components.VideoMenuAction
import moe.zako.viewer.ui.components.ZakoSearchBar
import moe.zako.viewer.ui.components.clearFocusOnScroll
import moe.zako.viewer.ui.components.clearFocusOnTap
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled
import moe.zako.viewer.ui.theme.SearchBarTokens

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (videoId: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

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
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("加入收藏夹")
                        }
                        TextButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(imageVector = AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(4.dp))
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
                    contentPadding = PaddingValues(bottom = 16.dp)
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
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
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
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
                    supportingText = "将视频添加到自定义收藏夹",
                    onClick = {
                        viewModel.showAddToFavoritesForSingleItem(target)
                    }
                )
            )
            add(
                VideoMenuAction(
                    id = "detail",
                    title = "查看详情",
                    icon = AppIcons.VideoDetail,
                    supportingText = "查看本视频应用内详情页",
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
                        supportingText = "查看文件名、本地存储路径与大小",
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
                    supportingText = "添加至稍后再看列表",
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
                    supportingText = "从历史记录中移除此条目",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryRowItem(
    item: WatchHistoryEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onSelectToggle() })
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Thumbnail
        val isCoverBlurEnabled = LocalCoverBlurEnabled.current
        val context = LocalContext.current
        Box(
            modifier = Modifier
                .width(110.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = CoverCacheManager.getCoverModel(context, item.videoId, item.coverUrl, autoFetch = true),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isCoverBlurEnabled) Modifier.blur(18.dp).clipToBounds() else Modifier
                    ),
                contentScale = ContentScale.Crop
            )
            DurationBadge(duration = item.duration)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exact time formatted as HH:mm (e.g. 14:35)
                Text(
                    text = "观看时间: ${item.timeExactString}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
