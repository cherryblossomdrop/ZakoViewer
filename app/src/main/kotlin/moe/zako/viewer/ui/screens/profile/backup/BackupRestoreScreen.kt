package moe.zako.viewer.ui.screens.profile.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import moe.zako.viewer.ui.components.ZakoToast

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

    LaunchedEffect(state.exportResult) {
        if (state.exportResult != null) {
            showExportResultDialog = true
        }
    }

    // 备份导出成功结果弹窗（替代纯 Toast 提示）
    if (showExportResultDialog && state.exportResult != null) {
        val result = state.exportResult!!
        AlertDialog(
            onDismissRequest = { showExportResultDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("备份导出成功")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "数据备份已完整写入公共存储：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(AnnotatedString(result.displayPath))
                                ZakoToast.show("已复制文件路径到剪贴板")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = result.displayPath,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制路径",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "可在系统文件管理器的下载目录中直接查看与管理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, result.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "分享备份文件"))
                    }
                ) {
                    Text("分享")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(result.displayPath))
                            ZakoToast.show("已复制文件路径到剪贴板")
                        }
                    ) {
                        Text("复制路径")
                    }
                    TextButton(onClick = { showExportResultDialog = false }) {
                        Text("确定")
                    }
                }
            }
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
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val hasStoragePermission = StoragePermissionHelper.hasAllFilesAccess()
            if (!hasStoragePermission) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "存储权限提示",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "未获得「所有文件访问权限」。备份与恢复已自动启用应用专属沙盒保护；若需将视频直接导出或恢复至公共 Download 目录，建议授予该权限。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
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
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "导出数据备份",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "包含全部观看记录、完整无限层级收藏夹树结构及元数据索引",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 常驻显示备份保存目录 (随时可查，点击复制)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(PublicStorageHelper.BACKUPS_DISPLAY_DIR))
                                    ZakoToast.show("已复制备份目录到剪贴板")
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "备份保存目录：${PublicStorageHelper.BACKUPS_DISPLAY_DIR}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "复制目录路径",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 复选开关：是否包含已下载视频文件
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = state.includeDownloadedVideos,
                                onCheckedChange = { viewModel.toggleIncludeVideos(it) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.exportBackup() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isExporting && !state.isRestoring
                        ) {
                            if (state.isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在生成备份...")
                            } else {
                                Icon(imageVector = Icons.Default.Upload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("导出备份文件")
                            }
                        }

                        state.exportResult?.let { result ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "最近备份已保存至:\n${result.displayPath}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/zip"
                                                    putExtra(Intent.EXTRA_STREAM, result.uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "分享备份文件"))
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("分享备份")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(result.displayPath))
                                                ZakoToast.show("已复制路径到剪贴板")
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("复制路径")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: 恢复备份
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "从备份文件恢复",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "支持从之前导出的 .json 元数据文件或包含视频的 .zip 归档文件无缝还原",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = {
                                restoreFileLauncher.launch(arrayOf("application/json", "application/zip", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isExporting && !state.isRestoring
                        ) {
                            if (state.isRestoring) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在恢复中...")
                            } else {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("选择备份文件恢复")
                            }
                        }

                        state.restoreStatusMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(12.dp))
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
