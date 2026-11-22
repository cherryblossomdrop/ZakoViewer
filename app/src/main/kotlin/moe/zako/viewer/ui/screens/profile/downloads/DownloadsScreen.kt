package moe.zako.viewer.ui.screens.profile.downloads

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.core.permission.StoragePermissionHelper
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.ui.components.FolderBrowserDialog
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.VideoActionBottomSheet
import moe.zako.viewer.ui.components.VideoMenuAction
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onNavigateBack: () -> Unit,
    onPlayDownloadedVideo: (videoId: String, localPath: String) -> Unit,
    onPlayOnline: (videoId: String) -> Unit,
    onNavigateToDetail: (videoId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showFolderBrowserDialog by remember { mutableStateOf(false) }
    var showPermissionGuideDialog by remember { mutableStateOf(false) }

    // 长按菜单状态
    var menuTargetItem by remember { mutableStateOf<DownloadEntity?>(null) }
    var showMenuBottomSheet by remember { mutableStateOf(false) }

    // 文件信息弹窗状态
    var fileInfoTargetItem by remember { mutableStateOf<DownloadEntity?>(null) }
    var showFileInfoDialog by remember { mutableStateOf(false) }

    // 本地文件不存在提示弹窗状态
    var missingFileTargetItem by remember { mutableStateOf<DownloadEntity?>(null) }
    var showMissingFileDialog by remember { mutableStateOf(false) }

    // 批量删除确认弹窗状态
    var showBatchDeleteConfirmDialog by remember { mutableStateOf(false) }

    fun requestChangeFolder() {
        if (StoragePermissionHelper.hasAllFilesAccess()) {
            showFolderBrowserDialog = true
        } else {
            showPermissionGuideDialog = true
        }
    }

    fun handlePlayClick(item: DownloadEntity) {
        val file = File(item.savePath)
        if (file.exists() && file.length() > 0L) {
            onPlayDownloadedVideo(item.videoId, file.absolutePath)
        } else {
            missingFileTargetItem = item
            showMissingFileDialog = true
        }
    }

    Scaffold(
        topBar = {
            if (state.isMultiSelectMode) {
                TopAppBar(
                    title = { Text("已选 ${state.selectedIds.size} 项", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitMultiSelect() }) {
                            Icon(imageVector = AppIcons.Close, contentDescription = "取消多选")
                        }
                    },
                    actions = {
                        val currentList = if (state.selectedTab == 0) state.completedList else state.activeList
                        val allSelected = currentList.isNotEmpty() && state.selectedIds.containsAll(currentList.map { it.videoId })
                        TextButton(onClick = { viewModel.toggleSelectAll() }) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                        IconButton(
                            onClick = {
                                if (state.selectedIds.isNotEmpty()) {
                                    showBatchDeleteConfirmDialog = true
                                } else {
                                    ZakoToast.show("请先勾选需要删除的任务")
                                }
                            },
                            enabled = state.selectedIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = "批量删除",
                                tint = if (state.selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("离线下载") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = AppIcons.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        val currentList = if (state.selectedTab == 0) state.completedList else state.activeList
                        if (currentList.isNotEmpty()) {
                            IconButton(onClick = { viewModel.enterMultiSelect() }) {
                                Icon(imageVector = AppIcons.MultiSelect, contentDescription = "多选")
                            }
                        }
                        IconButton(onClick = { requestChangeFolder() }) {
                            Icon(imageVector = AppIcons.FolderOpen, contentDescription = "存储路径")
                        }
                    }
                )
            }
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
                        imageVector = AppIcons.SdCard,
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.completedList, key = { it.videoId }) { item ->
                            val isSelected = state.selectedIds.contains(item.videoId)
                            CompletedDownloadItem(
                                item = item,
                                isMultiSelectMode = state.isMultiSelectMode,
                                isSelected = isSelected,
                                onSelectToggle = { viewModel.toggleSelect(item.videoId) },
                                onPlay = { handlePlayClick(item) },
                                onDelete = { viewModel.deleteDownload(item.videoId) },
                                onLongClick = {
                                    if (state.isMultiSelectMode) {
                                        viewModel.toggleSelect(item.videoId)
                                    } else {
                                        menuTargetItem = item
                                        showMenuBottomSheet = true
                                    }
                                }
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.activeList, key = { it.videoId }) { item ->
                            val isSelected = state.selectedIds.contains(item.videoId)
                            ActiveDownloadItem(
                                item = item,
                                isMultiSelectMode = state.isMultiSelectMode,
                                isSelected = isSelected,
                                onSelectToggle = { viewModel.toggleSelect(item.videoId) },
                                onPause = { viewModel.pauseDownload(item.videoId) },
                                onResume = { viewModel.resumeDownload(item.videoId) },
                                onDelete = { viewModel.deleteDownload(item.videoId) },
                                onLongClick = {
                                    if (state.isMultiSelectMode) {
                                        viewModel.toggleSelect(item.videoId)
                                    } else {
                                        menuTargetItem = item
                                        showMenuBottomSheet = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. 长按功能菜单 (全局复用 VideoActionBottomSheet)
    if (showMenuBottomSheet && menuTargetItem != null) {
        val target = menuTargetItem!!
        val actions = listOf(
            VideoMenuAction(
                id = "multi_select",
                title = "多选",
                icon = AppIcons.MultiSelect,
                onClick = {
                    viewModel.enterMultiSelect(target.videoId)
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
                id = "file_info",
                title = "文件信息",
                icon = AppIcons.FileInfo,
                supportingText = "查看文件名、本地存储路径、下载状态与修改时间",
                onClick = {
                    fileInfoTargetItem = target
                    showFileInfoDialog = true
                }
            )
        )

        VideoActionBottomSheet(
            title = target.title,
            actions = actions,
            onDismissRequest = { showMenuBottomSheet = false }
        )
    }

    // 收藏夹层级树选择弹窗
    if (state.showFolderSelector) {
        FolderTreeDialog(
            folders = state.allFolders,
            onDismiss = { viewModel.dismissFolderSelector() },
            onFolderSelected = { folderId -> viewModel.addToFavoriteFolder(folderId) },
            onCreateFolder = { name, parentId -> viewModel.createFolder(name, parentId) }
        )
    }

    // 2. 查看文件信息弹窗
    if (showFileInfoDialog && fileInfoTargetItem != null) {
        val target = fileInfoTargetItem!!
        val file = File(target.savePath)
        val fileExists = file.exists()

        val formatBytes = { bytes: Long ->
            if (bytes <= 0) "0 B"
            else {
                val units = arrayOf("B", "KB", "MB", "GB")
                val i = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
                String.format(Locale.getDefault(), "%.2f %s", bytes / Math.pow(1024.0, i.toDouble()), units[i])
            }
        }

        val sizeText = if (target.status == DownloadEntity.STATUS_COMPLETED) {
            val bytes = if (fileExists) file.length() else target.totalBytes
            formatBytes(bytes)
        } else {
            "${formatBytes(target.downloadedBytes)} / ${formatBytes(target.totalBytes)}"
        }

        val statusText = when (target.status) {
            DownloadEntity.STATUS_COMPLETED -> "已完成"
            DownloadEntity.STATUS_DOWNLOADING -> "下载中"
            DownloadEntity.STATUS_PAUSED -> "已暂停"
            DownloadEntity.STATUS_FAILED -> "拉取中断"
            else -> "准备中"
        }

        val lastModifiedText = if (fileExists) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
        } else if (target.completedAt != null && target.completedAt > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(target.completedAt))
        } else {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(target.createdAt))
        }

        AlertDialog(
            onDismissRequest = { showFileInfoDialog = false },
            icon = { Icon(AppIcons.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("文件信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("视频标题: ${target.title}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("文件名: ${file.name}", style = MaterialTheme.typography.bodySmall)
                    Text("文件大小: $sizeText", style = MaterialTheme.typography.bodySmall)
                    Text("下载状态: $statusText", style = MaterialTheme.typography.bodySmall)
                    Text("最后修改时间: $lastModifiedText", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "本地存储路径:\n${target.savePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("FilePath", target.savePath))
                        ZakoToast.show("已复制路径到剪贴板")
                    }
                ) {
                    Icon(AppIcons.Copy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制路径")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFileInfoDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 3. 本地文件不存在提示弹窗
    if (showMissingFileDialog && missingFileTargetItem != null) {
        val target = missingFileTargetItem!!
        AlertDialog(
            onDismissRequest = { showMissingFileDialog = false },
            icon = { Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("本地文件不存在") },
            text = {
                Text(
                    text = "检测到视频「${target.title}」对应的本地文件已被移动或从外部删除。为避免无感消耗流量，请选择在线播放或重新下载。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMissingFileDialog = false
                        viewModel.redownload(target.videoId)
                        viewModel.selectTab(1)
                        ZakoToast.show("已开始重新下载视频")
                    }
                ) {
                    Text("重新下载")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showMissingFileDialog = false
                            onPlayOnline(target.videoId)
                        }
                    ) {
                        Text("在线播放")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { showMissingFileDialog = false }) {
                        Text("取消")
                    }
                }
            }
        )
    }

    // 4. 批量删除确认弹窗
    if (showBatchDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirmDialog = false },
            icon = { Icon(AppIcons.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认批量删除") },
            text = {
                Text("确定要删除所选的 ${state.selectedIds.size} 个任务及其对应的本地视频文件吗？此操作不可恢复。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteConfirmDialog = false
                        viewModel.deleteSelected()
                        ZakoToast.show("已批量删除所选任务")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
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
            icon = { Icon(AppIcons.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
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

    // Migration Confirmation & Progress Dialog
    if (state.showMigrationDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isMigrating) {
                    viewModel.dismissMigrationDialog()
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = !state.isMigrating,
                dismissOnClickOutside = !state.isMigrating
            ),
            title = { Text(if (state.isMigrating) "正在迁移文件" else "迁移已下载文件") },
            text = {
                if (state.isMigrating) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.migrationProgressText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "检测到原下载目录存在 ${state.filesToMigrateCount} 个已下载视频文件。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "是否将已下载视频完整移动至新目录，并同步更新本地记录？",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "新目录: ${state.pendingNewDownloadPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                if (!state.isMigrating) {
                    Button(onClick = { viewModel.confirmMigration() }) {
                        Text("迁移并更新")
                    }
                }
            },
            dismissButton = {
                if (!state.isMigrating) {
                    Row {
                        TextButton(onClick = { viewModel.skipMigrationAndSetPath() }) {
                            Text("仅更改路径")
                        }
                        TextButton(onClick = { viewModel.dismissMigrationDialog() }) {
                            Text("取消")
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompletedDownloadItem(
    item: DownloadEntity,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val isCoverBlurEnabled = LocalCoverBlurEnabled.current
    val file = remember(item.savePath) { File(item.savePath) }
    val fileExists = file.exists() && file.length() > 0L

    // 优先加载本地持久化封面，缺失时自动补全
    val coverModel = remember(item.videoId, item.coverUrl) {
        CoverCacheManager.getCoverModel(context, item.videoId, item.coverUrl, autoFetch = true)
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else if (!fileExists) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) onSelectToggle() else onPlay()
                },
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() }
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            // 视频缩略图封面展示（本地文件已删除则去色置灰）
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                colorFilter = if (!fileExists) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (isCoverBlurEnabled) Modifier.blur(18.dp).clipToBounds() else Modifier),
                contentScale = ContentScale.Crop,
                alpha = if (!fileExists) 0.5f else 1f
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!fileExists) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (!fileExists) {
                    Text(
                        text = "此视频已被删除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    val sizeMb = item.totalBytes / (1024 * 1024.0)
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f MB | 本地已缓存", sizeMb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!isMultiSelectMode) {
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = if (!fileExists) AppIcons.WarningAmber else AppIcons.PlayCircle,
                        contentDescription = if (!fileExists) "已删除" else "播放",
                        tint = if (!fileExists) MaterialTheme.colorScheme.error.copy(alpha = 0.65f) else MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = AppIcons.DeleteOutline,
                        contentDescription = "删除",
                        tint = if (!fileExists) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveDownloadItem(
    item: DownloadEntity,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val isPausedOrFailed = item.status == DownloadEntity.STATUS_PAUSED || item.status == DownloadEntity.STATUS_FAILED
    val isCoverBlurEnabled = LocalCoverBlurEnabled.current

    // 优先加载本地持久化封面，缺失时自动补全
    val coverModel = remember(item.videoId, item.coverUrl) {
        CoverCacheManager.getCoverModel(context, item.videoId, item.coverUrl, autoFetch = true)
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) {
                        onSelectToggle()
                    } else {
                        if (isPausedOrFailed) onResume() else onPause()
                    }
                },
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // 视频缩略图封面展示（遵循封面模糊保护开关）
                AsyncImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier
                        .width(90.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (isCoverBlurEnabled) Modifier.blur(18.dp).clipToBounds() else Modifier),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val speedMb = item.speedBps / (1024 * 1024.0)
                    val statusText = when (item.status) {
                        DownloadEntity.STATUS_DOWNLOADING -> "并发拉取中 (${String.format(Locale.getDefault(), "%.1f MB/s", speedMb)})"
                        DownloadEntity.STATUS_PAUSED -> "已暂停 (点击继续)"
                        DownloadEntity.STATUS_FAILED -> if (!item.errorMessage.isNullOrEmpty()) "中断: ${item.errorMessage}" else "拉取中断 (点击重试)"
                        else -> "准备中..."
                    }
                    val statusColor = when (item.status) {
                        DownloadEntity.STATUS_DOWNLOADING -> MaterialTheme.colorScheme.primary
                        DownloadEntity.STATUS_FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        maxLines = 1
                    )
                }

                if (!isMultiSelectMode) {
                    IconButton(onClick = {
                        if (isPausedOrFailed) onResume() else onPause()
                    }) {
                        Icon(
                            imageVector = if (isPausedOrFailed) AppIcons.Play else AppIcons.Pause,
                            contentDescription = if (isPausedOrFailed) "继续下载" else "暂停下载"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = AppIcons.Close, contentDescription = "取消")
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val formatMb = { bytes: Long -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024 * 1024.0)) }
                Text(
                    text = "${formatMb(item.downloadedBytes)} / ${formatMb(item.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(item.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
