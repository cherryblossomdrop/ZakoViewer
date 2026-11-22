package moe.zako.viewer.ui.screens.profile.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import moe.zako.viewer.core.permission.StoragePermissionHelper
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.ui.components.FolderBrowserDialog
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.LocalFileInfo
import moe.zako.viewer.ui.components.LocalFileInfoDialog
import moe.zako.viewer.ui.components.MigrationProgressDialog
import moe.zako.viewer.ui.components.MultiSelectTopBar
import moe.zako.viewer.ui.components.ObserveStoragePermissionOnResume
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing
import java.io.File

/**
 * 离线下载管理主视窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onNavigateBack: () -> Unit,
    onPlayDownloadedVideo: (videoId: String, localPath: String) -> Unit,
    onPlayOnline: (videoId: String) -> Unit,
    onNavigateToDetail: (videoId: String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var hasStoragePermission by remember { mutableStateOf(StoragePermissionHelper.hasAllFilesAccess()) }
    var pendingOpenFolderBrowserOnPermissionGranted by remember { mutableStateOf(false) }
    var showFolderBrowserDialog by remember { mutableStateOf(false) }
    var showPermissionGuideDialog by remember { mutableStateOf(false) }

    ObserveStoragePermissionOnResume { granted, newlyGranted ->
        hasStoragePermission = granted
        if (newlyGranted && pendingOpenFolderBrowserOnPermissionGranted) {
            pendingOpenFolderBrowserOnPermissionGranted = false
            showPermissionGuideDialog = false
            showFolderBrowserDialog = true
        }
    }

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
            pendingOpenFolderBrowserOnPermissionGranted = true
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

    val currentList = if (state.selectedTab == 0) state.completedList else state.activeList
    val allSelected = currentList.isNotEmpty() && currentList.all { state.selectedIds.contains(it.videoId) }

    Scaffold(
        topBar = {
            if (state.isMultiSelectMode) {
                MultiSelectTopBar(
                    selectedCount = state.selectedIds.size,
                    onExitSelection = { viewModel.exitMultiSelect() },
                    allSelected = allSelected,
                    onToggleSelectAll = { viewModel.toggleSelectAll() },
                    actions = {
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
                    modifier = Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppIcons.SdCard,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Sm),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(Spacing.Sm))
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
                            .padding(Spacing.Md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
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
                            .padding(Spacing.Md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
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

    // 1. 长按功能菜单
    if (showMenuBottomSheet && menuTargetItem != null) {
        DownloadActionBottomSheet(
            target = menuTargetItem!!,
            onEnterMultiSelect = { videoId -> viewModel.enterMultiSelect(videoId) },
            onAddToFavorites = { target -> viewModel.showAddToFavorites(target) },
            onNavigateToDetail = { videoId -> onNavigateToDetail(videoId) },
            onShowFileInfo = { target ->
                fileInfoTargetItem = target
                showFileInfoDialog = true
            },
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
        LocalFileInfoDialog(
            info = LocalFileInfo.fromDownload(fileInfoTargetItem!!),
            onDismiss = { showFileInfoDialog = false }
        )
    }

    // 3. 本地文件不存在提示弹窗
    if (showMissingFileDialog && missingFileTargetItem != null) {
        MissingFileDialog(
            target = missingFileTargetItem!!,
            onRedownload = {
                val target = missingFileTargetItem!!
                showMissingFileDialog = false
                viewModel.redownload(target.videoId)
                viewModel.selectTab(1)
                ZakoToast.show("已开始重新下载视频")
            },
            onPlayOnline = {
                val target = missingFileTargetItem!!
                showMissingFileDialog = false
                onPlayOnline(target.videoId)
            },
            onDismiss = { showMissingFileDialog = false }
        )
    }

    // 4. 批量删除确认弹窗
    if (showBatchDeleteConfirmDialog) {
        BatchDeleteConfirmDialog(
            selectedCount = state.selectedIds.size,
            onConfirm = {
                showBatchDeleteConfirmDialog = false
                viewModel.deleteSelected()
                ZakoToast.show("已批量删除所选任务")
            },
            onDismiss = { showBatchDeleteConfirmDialog = false }
        )
    }

    // 5. 文件夹浏览器弹窗
    if (showFolderBrowserDialog) {
        FolderBrowserDialog(
            initialPath = state.currentStoragePath,
            onDismiss = { showFolderBrowserDialog = false },
            onFolderSelected = { selectedPath ->
                viewModel.updateCustomStoragePath(selectedPath)
            }
        )
    }

    // 6. 存储权限引导弹窗
    if (showPermissionGuideDialog) {
        DownloadPermissionGuideDialog(
            onDismiss = { showPermissionGuideDialog = false }
        )
    }

    // 7. 存储路径跨目录迁移弹窗 (复用全局统一 MigrationProgressDialog)
    if (state.showMigrationDialog) {
        MigrationProgressDialog(
            isMigrating = state.isMigrating,
            progressText = state.migrationProgressText,
            filesCount = state.filesToMigrateCount,
            targetPath = state.pendingNewDownloadPath,
            onConfirm = { viewModel.confirmMigration() },
            onSkip = { viewModel.skipMigrationAndSetPath() },
            onDismiss = { viewModel.dismissMigrationDialog() }
        )
    }
}
