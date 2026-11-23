package moe.zako.viewer.ui.screens.profile.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.core.external.AppPickerHelper
import moe.zako.viewer.core.permission.StoragePermissionHelper
import moe.zako.viewer.core.storage.StorageConstants
import moe.zako.viewer.ui.components.AppPickerDialog
import moe.zako.viewer.ui.components.FolderBrowserDialog
import moe.zako.viewer.ui.components.MigrationProgressDialog
import moe.zako.viewer.ui.components.ObserveStoragePermissionOnResume
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing
import java.io.File

/**
 * 隐私设置子页面 (视频封面默认模糊)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPrivacyScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隐私") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = AppIcons.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.Lg)
        ) {
            item {
                SettingsCard(shape = RoundedCornerShape(CornerRadius.CardGroup)) {
                    SettingsSwitchItem(
                        title = "视频封面模糊",
                        subtitle = "对全应用所有视频卡片与列表项（推荐页、搜索结果、历史记录、收藏夹、稍后再看、相关推荐等）的封面施加明显的隐私模糊效果，防止敏感画面社死（默认开启）。",
                        checked = state.coverBlurEnabled,
                        onCheckedChange = { viewModel.setCoverBlur(it) }
                    )
                }
            }
        }
    }
}

/**
 * 分享设置子页面 (受隐藏解锁开关控制)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsShareScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分享设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = AppIcons.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.Lg)
        ) {
            // 动态依据可见项集合判定渲染
            if (state.visibleShareSettingItems.contains(ShareSettingItem.LONG_PRESS_SHARE_URL)) {
                item {
                    SettingsCard(shape = RoundedCornerShape(CornerRadius.CardGroup)) {
                        SettingsSwitchItem(
                            title = "开启长按分享复制完整链接",
                            subtitle = "在视频详情页长按分享图标时，将直接复制视频的完整原始播放/详情链接到剪贴板",
                            checked = state.isLongPressShareEnabled,
                            onCheckedChange = { viewModel.setLongPressShareEnabled(it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 下载与存储设置子页面 (文件访问权限、目录、外部下载器)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDownloadScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    var hasStoragePermission by remember { mutableStateOf(StoragePermissionHelper.hasAllFilesAccess()) }
    var showPermissionGuideDialog by remember { mutableStateOf(false) }
    var showFolderBrowserDialog by remember { mutableStateOf(false) }
    var showDownloaderPickerDialog by remember { mutableStateOf(false) }

    ObserveStoragePermissionOnResume { granted ->
        hasStoragePermission = granted
        if (granted) {
            showPermissionGuideDialog = false
        }
    }

    val selectedDownloaderApp = remember(state.externalDownloaderPkg) {
        AppPickerHelper.getAppByPackage(context, state.externalDownloaderPkg)
    }

    val defaultDownloadPath = remember {
        val emulated = File(StorageConstants.DEFAULT_DOWNLOAD_DIR)
        if (emulated.parentFile?.exists() == true) emulated.absolutePath
        else File(context.getExternalFilesDir(null), "downloads").absolutePath
    }

    val currentDisplayDownloadPath = state.downloadPath.ifEmpty { defaultDownloadPath }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = AppIcons.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.Lg)
        ) {
            // 1. 所有文件访问权限卡片
            item {
                Card(
                    shape = RoundedCornerShape(CornerRadius.Md),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasStoragePermission) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.Lg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (hasStoragePermission) AppIcons.CheckCircle else AppIcons.Warning,
                                    contentDescription = null,
                                    tint = if (hasStoragePermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(IconSize.MediumLarge)
                                )
                                Spacer(modifier = Modifier.width(Spacing.Sm))
                                Text(
                                    text = "所有文件访问权限",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(CornerRadius.Compact),
                                color = if (hasStoragePermission) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = if (hasStoragePermission) "已授权" else "未授权",
                                    color = if (hasStoragePermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = Spacing.SpeedBadge)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.Sm))
                        Text(
                            text = if (hasStoragePermission) {
                                "应用已拥有全盘文件读写权限，可自由指定手机存储中的任意文件夹作为视频下载目录。"
                            } else {
                                "Android 11+ 系统需授予「所有文件访问权限」才能正常在设备存储或 SD 卡中创建和选择下载目录。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!hasStoragePermission) {
                            Spacer(modifier = Modifier.height(Spacing.Intermediate))
                            Button(
                                onClick = { StoragePermissionHelper.requestAllFilesAccess(context) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(AppIcons.Security, contentDescription = null, modifier = Modifier.size(IconSize.Intermediate))
                                Spacer(modifier = Modifier.width(Spacing.Compact))
                                Text("前往系统设置开启权限")
                            }
                        }
                    }
                }
            }

            // 2. 自定义下载目录 (内置文件夹选择器)
            item {
                Card(
                    shape = RoundedCornerShape(CornerRadius.Md),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.Lg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "自定义下载目录",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = {
                                if (hasStoragePermission) {
                                    showFolderBrowserDialog = true
                                } else {
                                    showPermissionGuideDialog = true
                                }
                            }) {
                                Icon(
                                    imageVector = AppIcons.FolderOpen,
                                    contentDescription = "选择目录",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = "离线缓存与视频切片保存的主目录。点击下方路径或右侧图标可打开内置文件夹选择器浏览、新建目录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(Spacing.Intermediate))

                        Surface(
                            shape = RoundedCornerShape(CornerRadius.Sm),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (hasStoragePermission) {
                                        showFolderBrowserDialog = true
                                    } else {
                                        showPermissionGuideDialog = true
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.Md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(IconSize.Lg)
                                )
                                Spacer(modifier = Modifier.width(Spacing.Intermediate))
                                Text(
                                    text = currentDisplayDownloadPath,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    if (hasStoragePermission) {
                                        showFolderBrowserDialog = true
                                    } else {
                                        showPermissionGuideDialog = true
                                    }
                                }) {
                                    Text("更改", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // 3. 自定义外部下载器
            item {
                ExternalAppCard(
                    title = "自定义外部下载器",
                    actionIcon = AppIcons.Download,
                    description = "详情页长按下载按钮可唤起所选外部下载器（如 1DM, ADM, 迅雷等）",
                    selectedPackage = state.externalDownloaderPkg,
                    selectedApp = selectedDownloaderApp,
                    defaultFallbackIcon = AppIcons.DownloadForOffline,
                    onOpenPicker = { showDownloaderPickerDialog = true },
                    onClearPackage = { viewModel.setExternalDownloader("") }
                )
            }
        }
    }

    // Permission guidance dialog
    if (showPermissionGuideDialog) {
        StoragePermissionGuideDialog(
            onConfirm = {
                showPermissionGuideDialog = false
                StoragePermissionHelper.requestAllFilesAccess(context)
            },
            onDismiss = { showPermissionGuideDialog = false }
        )
    }

    // Built-in Folder Browser Dialog
    if (showFolderBrowserDialog) {
        FolderBrowserDialog(
            initialPath = currentDisplayDownloadPath,
            onDismiss = { showFolderBrowserDialog = false },
            onFolderSelected = { selectedPath ->
                viewModel.requestChangeDownloadPath(selectedPath, defaultDownloadPath)
            }
        )
    }

    // Migration Confirmation & Progress Dialog (复用全局统一 MigrationProgressDialog)
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

    // External Downloader App Picker Dialog
    if (showDownloaderPickerDialog) {
        AppPickerDialog(
            title = "选择自定义外部下载器",
            currentPackage = state.externalDownloaderPkg,
            isVideoPlayerMode = false,
            onDismiss = { showDownloaderPickerDialog = false },
            onAppSelected = { pkg ->
                viewModel.setExternalDownloader(pkg)
            }
        )
    }
}
