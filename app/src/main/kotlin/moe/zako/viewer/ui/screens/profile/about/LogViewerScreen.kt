package moe.zako.viewer.ui.screens.profile.about

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.storage.ExportResult
import moe.zako.viewer.core.storage.PublicStorageHelper
import moe.zako.viewer.ui.components.ZakoToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val logs by ZakoLog.logsFlow.collectAsState()
    val includeHistory by ZakoLog.includeHistoryFlow.collectAsState()
    var exportedResult by remember { mutableStateOf<ExportResult?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    // 日志导出成功结果弹窗（替代纯 Toast）
    if (exportedResult != null) {
        val result = exportedResult!!
        AlertDialog(
            onDismissRequest = { exportedResult = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("日志导出成功")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "日志文件已写入公共存储目录：",
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
                        text = "可在系统文件管理器的下载目录中直接查看与排查。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, result.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "分享日志文件"))
                        exportedResult = null
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
                    TextButton(onClick = { exportedResult = null }) {
                        Text("确定")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("运行日志 (${logs.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        ZakoLog.clear()
                        ZakoToast.show("运行日志已清空")
                    }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "清空日志")
                    }
                    IconButton(
                        enabled = !isExporting,
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                isExporting = true
                                try {
                                    val result = ZakoLog.exportToPublicDownloads(context, includeHistory = includeHistory)
                                    withContext(Dispatchers.Main) {
                                        exportedResult = result
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        ZakoToast.show("导出失败: ${e.message}")
                                    }
                                } finally {
                                    isExporting = false
                                }
                            }
                        }
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "导出日志")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F0F0F))
        ) {
            // 常驻显示日志保存目录 (随时可查，点击复制)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clickable {
                        clipboardManager.setText(AnnotatedString(PublicStorageHelper.LOGS_DISPLAY_DIR))
                        ZakoToast.show("已复制日志目录到剪贴板")
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
                        text = "日志保存目录：${PublicStorageHelper.LOGS_DISPLAY_DIR}",
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

            // 版本状态与历史版本日志隔离控制
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "当前版本: ${ZakoLog.CURRENT_VERSION_TAG}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontFamily = FontFamily.Monospace
                    )
                }

                FilterChip(
                    selected = includeHistory,
                    onClick = { ZakoLog.setIncludeHistory(!includeHistory) },
                    label = {
                        Text(
                            text = if (includeHistory) "已包含历史日志" else "仅当前版本日志",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (includeHistory) Icons.Default.History else Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    val color = when (log.level) {
                        "ERROR" -> Color(0xFFFF5252)
                        "WARN" -> Color(0xFFFFD740)
                        "INFO" -> Color(0xFF69F0AE)
                        else -> Color(0xFFE0E0E0)
                    }
                    Text(
                        text = log.formatted(),
                        color = color,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
