package moe.zako.viewer.ui.screens.profile.about

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.storage.ExportResult
import moe.zako.viewer.core.storage.PublicStorageHelper
import moe.zako.viewer.ui.components.ZakoSearchBar
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.components.clearFocusOnScroll
import moe.zako.viewer.ui.components.clearFocusOnTap
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.BorderWidth
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.ExtraTextStyles
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.LogLevelColors
import moe.zako.viewer.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val logs by ZakoLog.logsFlow.collectAsState()
    val includeHistory by ZakoLog.includeHistoryFlow.collectAsState()
    var exportedResult by remember { mutableStateOf<ExportResult?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) logs
        else logs.filter { it.formatted().contains(searchQuery, ignoreCase = true) }
    }

    // 日志导出成功结果弹窗（替代纯 Toast）
    if (exportedResult != null) {
        val result = exportedResult!!
        AlertDialog(
            onDismissRequest = { exportedResult = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.Lg)
                    )
                    Spacer(modifier = Modifier.width(Spacing.Sm))
                    Text("日志导出成功")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    Text(
                        text = "日志文件已写入公共存储目录：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Sm),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(AnnotatedString(result.displayPath))
                                ZakoToast.show("已复制路径")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.Sm),
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
                                imageVector = AppIcons.Copy,
                                contentDescription = "复制路径",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(IconSize.Sm)
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
                    Text("分享日志")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(result.displayPath))
                            ZakoToast.show("已复制路径")
                        }
                    ) {
                        Text("复制路径")
                    }
                    TextButton(onClick = { exportedResult = null }) {
                        Text("确认关闭")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (searchQuery.isBlank()) "运行日志 (${logs.size})"
                        else "运行日志 (${filteredLogs.size}/${logs.size})"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        ZakoLog.clear()
                        ZakoToast.show("运行日志已清空")
                    }) {
                        Icon(imageVector = AppIcons.Delete, contentDescription = "清空日志")
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
                            CircularProgressIndicator(modifier = Modifier.size(IconSize.Md), strokeWidth = BorderWidth.Thick)
                        } else {
                            Icon(imageVector = AppIcons.Share, contentDescription = "导出日志")
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
                .background(if (isDark) LogLevelColors.BackgroundDark else LogLevelColors.BackgroundLight)
                .clearFocusOnScroll()
                .clearFocusOnTap()
        ) {
            // 全局统一搜索框
            ZakoSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholderText = "搜索运行日志关键词...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Sm, vertical = Spacing.Xs)
            )

            // 常驻显示日志保存目录 (随时可查，点击复制)
            Surface(
                shape = RoundedCornerShape(CornerRadius.Sm),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Sm, vertical = Spacing.Xs)
                    .clickable {
                        clipboardManager.setText(AnnotatedString(PublicStorageHelper.LOGS_DISPLAY_DIR))
                        ZakoToast.show("已复制目录")
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
                        text = "日志保存目录：${PublicStorageHelper.LOGS_DISPLAY_DIR}",
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

            // 版本状态与历史版本日志隔离控制
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Sm, vertical = Spacing.Xxs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Xs),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "当前版本: ${ZakoLog.CURRENT_VERSION_TAG}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = Spacing.Xs),
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
                            imageVector = if (includeHistory) AppIcons.History else AppIcons.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.Xs)
                        )
                    }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.Sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
            ) {
                items(filteredLogs) { log ->
                    val color = LogLevelColors.colorFor(log.level, isDark)
                    Text(
                        text = log.formatted(),
                        color = color,
                        style = ExtraTextStyles.MonospaceLog
                    )
                }
            }
        }
    }
}
