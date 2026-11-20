package moe.zako.viewer.ui.screens.profile.about

import android.content.Intent
import moe.zako.viewer.ui.components.ZakoToast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.content.FileProvider
import moe.zako.viewer.core.logger.ZakoLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val logs by ZakoLog.logsFlow.collectAsState()

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
                    IconButton(onClick = { ZakoLog.clear() }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "清空日志")
                    }
                    IconButton(onClick = {
                        try {
                            val logFile = ZakoLog.exportToFile(context)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                logFile
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "导出/分享日志文件"))
                        } catch (e: Exception) {
                            ZakoToast.show("导出失败: ${e.message}")
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "导出日志")
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
