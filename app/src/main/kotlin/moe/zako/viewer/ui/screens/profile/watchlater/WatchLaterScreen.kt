package moe.zako.viewer.ui.screens.profile.watchlater

import androidx.activity.compose.BackHandler
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
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.data.local.entities.WatchLaterEntity
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.MultiSelectTopBar
import moe.zako.viewer.ui.components.VideoActionBottomSheet
import moe.zako.viewer.ui.components.VideoMenuAction
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WatchLaterScreen(
    viewModel: WatchLaterViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (videoId: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var menuTargetItem by remember { mutableStateOf<WatchLaterEntity?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                val allSelected = state.list.isNotEmpty() && state.selectedVideoIds.size == state.list.size
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
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("加入收藏夹 (${state.selectedVideoIds.size})")
                        }
                        TextButton(onClick = { showBatchDeleteDialog = true }) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
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
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.list, key = { it.videoId }) { item ->
                    val isSelected = state.selectedVideoIds.contains(item.videoId)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
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
                                }
                            ),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleSelection(item.videoId) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            val isCoverBlurEnabled = LocalCoverBlurEnabled.current
                            val context = LocalContext.current
                            AsyncImage(
                                model = CoverCacheManager.getCoverModel(context, item.videoId, item.coverUrl, autoFetch = true),
                                contentDescription = null,
                                modifier = Modifier
                                    .width(100.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (isCoverBlurEnabled) Modifier.blur(18.dp).clipToBounds() else Modifier
                                    ),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (item.duration.isNotEmpty()) {
                                    Text(
                                        text = item.duration,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!state.isSelectionMode) {
                                IconButton(onClick = { viewModel.remove(item.videoId) }) {
                                    Icon(
                                        imageVector = AppIcons.DeleteOutline,
                                        contentDescription = "移除"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 全局长按上下文操作菜单
    if (menuTargetItem != null) {
        val target = menuTargetItem!!
        val actions = listOf(
            VideoMenuAction(
                id = "multi_select",
                title = "多选",
                icon = AppIcons.MultiSelect,
                onClick = {
                    viewModel.enterSelectionWithItem(target.videoId)
                }
            ),
            VideoMenuAction(
                id = "add_to_favorites",
                title = "添加到收藏夹",
                icon = AppIcons.AddToFavorites,
                supportingText = "将视频添加到自定义收藏夹",
                onClick = {
                    viewModel.showAddToFavorites(target)
                }
            ),
            VideoMenuAction(
                id = "detail",
                title = "详情页",
                icon = AppIcons.VideoDetail,
                supportingText = "查看本视频应用内详情页",
                onClick = {
                    onNavigateToDetail(target.videoId)
                }
            ),
            VideoMenuAction(
                id = "remove_watch_later",
                title = "移出稍后再看",
                icon = AppIcons.Delete,
                supportingText = "从稍后再看列表中移除",
                isDangerous = true,
                onClick = {
                    viewModel.remove(target.videoId)
                }
            )
        )

        VideoActionBottomSheet(
            title = target.title,
            actions = actions,
            onDismissRequest = { menuTargetItem = null }
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
                    Text("取消")
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
