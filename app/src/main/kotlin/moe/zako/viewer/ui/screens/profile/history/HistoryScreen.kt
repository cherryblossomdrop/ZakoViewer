package moe.zako.viewer.ui.screens.profile.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (videoId: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    // Group history items by calendar day
    val groupedHistory = remember(state.historyList) {
        state.historyList.groupBy { it.dayGroupString }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isSelectionMode) "已选择 ${state.selectedVideoIds.size} 项" else "观看记录"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
                        TextButton(onClick = { viewModel.selectAll() }) { Text("全选") }
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "退出多选")
                        }
                    } else {
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Icon(imageVector = Icons.Default.CheckCircleOutline, contentDescription = "多选管理")
                        }
                        IconButton(onClick = { viewModel.clearAllHistory() }) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "清空记录")
                        }
                    }
                }
            )
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
                            Icon(imageVector = Icons.Default.Favorite, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("加入收藏夹")
                        }
                        TextButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
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
        ) {
            // Search Bar (占位文本已按需求彻底移除)
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true
            )

            if (state.historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无观看记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                }
                            )
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }

    if (state.showFolderSelector) {
        FolderTreeDialog(
            folders = state.allFolders,
            onDismiss = { viewModel.dismissFolderDialog() },
            onFolderSelected = { targetId -> viewModel.batchAddToFavorites(targetId) },
            onCreateFolder = { _, _ -> }
        )
    }
}

@Composable
fun HistoryRowItem(
    item: WatchHistoryEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
        AsyncImage(
            model = CoverCacheManager.getCoverModel(context, item.videoId, item.coverUrl, autoFetch = true),
            contentDescription = null,
            modifier = Modifier
                .width(110.dp)
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

                if (item.duration.isNotEmpty()) {
                    Text(
                        text = item.duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
