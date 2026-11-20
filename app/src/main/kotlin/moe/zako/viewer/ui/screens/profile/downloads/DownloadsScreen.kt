package moe.zako.viewer.ui.screens.profile.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.zako.viewer.core.permission.StoragePermissionHelper
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.ui.components.FolderBrowserDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onNavigateBack: () -> Unit,
    onPlayDownloadedVideo: (videoId: String) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showFolderBrowserDialog by remember { mutableStateOf(false) }
    var showPermissionGuideDialog by remember { mutableStateOf(false) }

    fun requestChangeFolder() {
        if (StoragePermissionHelper.hasAllFilesAccess()) {
            showFolderBrowserDialog = true
        } else {
            showPermissionGuideDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("离线下载") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { requestChangeFolder() }) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "存储路径")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Storage Path Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SdCard,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "保存目录: ${state.currentStoragePath}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { requestChangeFolder() }) {
                        Text("修改", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Top Tabs: 已下载 vs 下载中
            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("已下载 (${state.completedList.size})") }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("下载中 (${state.activeList.size})") }
                )
            }

            if (state.selectedTab == 0) {
                // Completed Downloads View
                if (state.completedList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无已下载视频", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.completedList, key = { it.videoId }) { item ->
                            CompletedDownloadItem(
                                item = item,
                                onPlay = { onPlayDownloadedVideo(item.videoId) },
                                onDelete = { viewModel.deleteDownload(item.videoId) }
                            )
                        }
                    }
                }
            } else {
                // Active Downloads View
                if (state.activeList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无正在下载的任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.activeList, key = { it.videoId }) { item ->
                            ActiveDownloadItem(
                                item = item,
                                onPause = { viewModel.pauseDownload(item.videoId) },
                                onDelete = { viewModel.deleteDownload(item.videoId) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Folder Browser Dialog
    if (showFolderBrowserDialog) {
        FolderBrowserDialog(
            initialPath = state.currentStoragePath,
            onDismiss = { showFolderBrowserDialog = false },
            onFolderSelected = { selectedPath ->
                viewModel.updateCustomStoragePath(selectedPath)
            }
        )
    }

    // Permission Guide Dialog
    if (showPermissionGuideDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionGuideDialog = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("需要「所有文件访问权限」") },
            text = {
                Text(
                    text = "自定义下载目录需要系统授予「所有文件访问权限」，以便在手机外部存储或 SD 卡中自由读写和保存下载的视频。请前往设置开启该权限。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionGuideDialog = false
                    StoragePermissionHelper.requestAllFilesAccess(context)
                }) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionGuideDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun CompletedDownloadItem(
    item: DownloadEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                val sizeMb = item.totalBytes / (1024 * 1024.0)
                Text(
                    text = String.format("%.1f MB | 本地已缓存", sizeMb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onPlay) {
                Icon(imageVector = Icons.Default.PlayCircle, contentDescription = "播放", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "删除")
            }
        }
    }
}

@Composable
fun ActiveDownloadItem(
    item: DownloadEntity,
    onPause: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onPause) {
                    Icon(imageVector = Icons.Default.Pause, contentDescription = "暂停")
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "取消")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val speedMb = item.speedBps / (1024 * 1024.0)
                Text(
                    text = "多节点并发拉取中 (${String.format("%.1f MB/s", speedMb)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${(item.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
