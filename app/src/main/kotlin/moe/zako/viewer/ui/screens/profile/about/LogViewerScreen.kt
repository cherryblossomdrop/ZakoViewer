package moe.zako.viewer.ui.screens.profile.about

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.storage.ExportResult
import moe.zako.viewer.ui.components.ZakoToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val logs by ZakoLog.logsFlow.collectAsState()
    var exportedResult by remember { mutableStateOf<ExportResult?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    if (exportedResult != null) {
        val result = exportedResult!!
        AlertDialog(
            onDismissRequest = { exportedResult = null },
            title = { Text("日志导出成功") },
            text = { Text("日志文件已保存到公共目录：\n${result.displayPath}") },
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
                TextButton(onClick = { exportedResult = null }) {
                    Text("关闭")
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
                                    val result = ZakoLog.exportToPublicDownloads(context)
                                    withContext(Dispatchers.Main) {
                                        exportedResult = result
                                        ZakoToast.show("已保存到 ${result.displayPath}")
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
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F0F0F))
        ) {
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
