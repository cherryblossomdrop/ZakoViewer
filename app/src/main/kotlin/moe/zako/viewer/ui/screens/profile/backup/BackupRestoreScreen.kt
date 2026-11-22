package moe.zako.viewer.ui.screens.profile.backup

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.zako.viewer.core.permission.StoragePermissionHelper
import moe.zako.viewer.core.storage.PublicStorageHelper
import moe.zako.viewer.ui.components.ObserveStoragePermissionOnResume
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing

/**
 * 数据备份与恢复视窗
 * 弹窗组件解耦至同包 [BackupRestoreDialogs.kt]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: BackupRestoreViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val state by viewModel.uiState.collectAsState()
    var showExportResultDialog by remember { mutableStateOf(false) }
    var hasStoragePermission by remember { mutableStateOf(StoragePermissionHelper.hasAllFilesAccess()) }

    // 监听生命周期 ON_RESUME 自动刷新存储权限状态
    ObserveStoragePermissionOnResume { granted ->
        hasStoragePermission = granted
    }

    LaunchedEffect(state.exportResult) {
        if (state.exportResult != null) {
            showExportResultDialog = true
        }
    }

    // 在导入恢复或大文件导出期间，保持屏幕常亮
    val isBusy = state.isRestoring || state.isExporting
    DisposableEffect(isBusy) {
        val window = (context as? Activity)?.window
        if (isBusy) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 1. 不可误触关闭的进度弹窗
    if (isBusy) {
        BackupProgressDialog(
            isRestoring = state.isRestoring,
            includeDownloadedVideos = state.includeDownloadedVideos,
            progressStage = state.progressStage,
            progressPercent = state.progressPercent,
            progressDetail = state.progressDetail
        )
    }

    // 2. 恢复结果反馈弹窗
    state.restoreResult?.let { result ->
        BackupRestoreResultDialog(
            result = result,
            onDismiss = { viewModel.dismissRestoreResult() }
        )
    }

    // 3. 备份导出成功结果弹窗
    if (showExportResultDialog && state.exportResult != null) {
        BackupExportResultDialog(
            result = state.exportResult!!,
            onDismiss = { showExportResultDialog = false }
        )
    }

    // Document Picker Launcher for Restore
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.restoreBackup(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复") },
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
            if (!hasStoragePermission) {
                item {
                    Card(
                        shape = RoundedCornerShape(CornerRadius.Md),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(Spacing.Lg)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = AppIcons.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(IconSize.Md)
                                )
                                Spacer(modifier = Modifier.width(Spacing.Sm))
                                Text(
                                    text = "存储权限提示",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.Sm))
                            Text(
                                text = "未获得「所有文件访问权限」。备份与恢复已自动启用应用专属沙盒保护；若需将视频直接导出或恢复至公共 Download 目录，建议授予该权限。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(Spacing.Sm))
                            OutlinedButton(
                                onClick = { StoragePermissionHelper.requestAllFilesAccess(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("前往系统设置授予权限")
                            }
                        }
                    }
                }
            }

            // Section 1: 导出备份
            item {
                Card(
                    shape = RoundedCornerShape(CornerRadius.Md),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(Spacing.Lg)) {
                        Text(
                            text = "导出数据备份",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Spacing.Xs))
                        Text(
                            text = "包含全部历史记录、完整无限层级收藏夹树结构及元数据索引",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(Spacing.Sm))

                        // 常驻显示备份保存目录 (随时可查，点击复制)
                        Surface(
                            shape = RoundedCornerShape(CornerRadius.Sm),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(PublicStorageHelper.BACKUPS_DISPLAY_DIR))
                                    ZakoToast.show("已复制备份目录到剪贴板")
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(IconSize.Sm)
                                )
                                Spacer(modifier = Modifier.width(Spacing.Sm))
                                Text(
                                    text = "备份保存目录：${PublicStorageHelper.BACKUPS_DISPLAY_DIR}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = AppIcons.Copy,
                                    contentDescription = "复制目录路径",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(IconSize.Sm)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.Md))

                        // 复选开关：是否包含已下载视频文件
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = state.includeDownloadedVideos,
                                onCheckedChange = { viewModel.toggleIncludeVideos(it) }
                            )
                            Spacer(modifier = Modifier.width(Spacing.Sm))
                            Column {
                                Text(
                                    text = "包含已下载视频文件 (打包为 ZIP 归档)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "若开启，会将离线缓存的视频文件一同打包，文件体积较大",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.Lg))

                        Button(
                            onClick = { viewModel.exportBackup() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isExporting && !state.isRestoring
                        ) {
                            if (state.isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(IconSize.Sm), color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(Spacing.Sm))
                                Text("正在生成备份...")
                            } else {
                                Icon(imageVector = AppIcons.Upload, contentDescription = null)
                                Spacer(modifier = Modifier.width(Spacing.Sm))
                                Text("导出备份文件")
                            }
                        }
                    }
                }
            }

            // Section 2: 恢复备份
            item {
                Card(
                    shape = RoundedCornerShape(CornerRadius.Md),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(Spacing.Lg)) {
                        Text(
                            text = "从备份文件恢复",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Spacing.Xs))
                        Text(
                            text = "支持从之前导出的 .json 元数据文件或包含视频的 .zip 归档文件无缝还原",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(Spacing.Lg))

                        OutlinedButton(
                            onClick = {
                                restoreFileLauncher.launch(arrayOf("application/json", "application/zip", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isExporting && !state.isRestoring
                        ) {
                            if (state.isRestoring) {
                                CircularProgressIndicator(modifier = Modifier.size(IconSize.Sm))
                                Spacer(modifier = Modifier.width(Spacing.Sm))
                                Text("正在恢复中...")
                            } else {
                                Icon(imageVector = AppIcons.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(Spacing.Sm))
                                Text("选择备份文件恢复")
                            }
                        }

                        state.restoreStatusMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(Spacing.Md))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
