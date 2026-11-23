package moe.zako.viewer.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.core.storage.StorageConstants
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地视频文件信息数据载体
 */
data class LocalFileInfo(
    val title: String,
    val fileName: String,
    val filePath: String,
    val fileSizeFormatted: String,
    val duration: String? = null,
    val lastModifiedFormatted: String? = null,
    val statusText: String? = null
) {
    companion object {
        fun formatBytes(bytes: Long): String = StorageConstants.formatBytes(bytes)

        fun fromFile(file: File, title: String, duration: String? = null): LocalFileInfo {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return LocalFileInfo(
                title = title,
                fileName = file.name,
                filePath = file.absolutePath,
                fileSizeFormatted = formatBytes(if (file.exists()) file.length() else 0L),
                duration = duration?.takeIf { it.isNotBlank() },
                lastModifiedFormatted = if (file.exists()) sdf.format(Date(file.lastModified())) else null
            )
        }

        fun fromDownload(download: DownloadEntity): LocalFileInfo {
            val file = File(download.savePath)
            val fileExists = file.exists()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val sizeText = if (download.status == DownloadEntity.STATUS_COMPLETED) {
                val bytes = if (fileExists) file.length() else download.totalBytes
                formatBytes(bytes)
            } else {
                "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}"
            }
            val statusText = when (download.status) {
                DownloadEntity.STATUS_COMPLETED -> "已完成"
                DownloadEntity.STATUS_DOWNLOADING -> "下载中"
                DownloadEntity.STATUS_PAUSED -> "已暂停"
                DownloadEntity.STATUS_FAILED -> "拉取中断"
                else -> "准备中"
            }
            val lastModifiedText = if (fileExists) {
                sdf.format(Date(file.lastModified()))
            } else if (download.completedAt != null && download.completedAt > 0) {
                sdf.format(Date(download.completedAt))
            } else {
                sdf.format(Date(download.createdAt))
            }

            return LocalFileInfo(
                title = download.title,
                fileName = file.name,
                filePath = download.savePath,
                fileSizeFormatted = sizeText,
                duration = download.duration.takeIf { it.isNotBlank() },
                lastModifiedFormatted = lastModifiedText,
                statusText = statusText
            )
        }
    }
}

/**
 * 4 个列表页（离线下载、收藏夹、历史记录、稍后再看）统一复用的本地文件信息弹窗组件
 */
@Composable
fun LocalFileInfoDialog(
    info: LocalFileInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = AppIcons.FileInfo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("本地文件信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                Text(
                    text = "视频标题: ${info.title}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(text = "文件名: ${info.fileName}", style = MaterialTheme.typography.bodySmall)
                Text(text = "文件大小: ${info.fileSizeFormatted}", style = MaterialTheme.typography.bodySmall)
                if (!info.duration.isNullOrBlank()) {
                    Text(text = "时长: ${DurationFormatter.format(info.duration)}", style = MaterialTheme.typography.bodySmall)
                }
                if (!info.statusText.isNullOrBlank()) {
                    Text(text = "下载状态: ${info.statusText}", style = MaterialTheme.typography.bodySmall)
                }
                if (!info.lastModifiedFormatted.isNullOrBlank()) {
                    Text(text = "最后修改时间: ${info.lastModifiedFormatted}", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(Spacing.Xs))
                Text(
                    text = "本地存储路径:\n${info.filePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("FilePath", info.filePath))
                    ZakoToast.show("已复制路径")
                }
            ) {
                Icon(AppIcons.Copy, contentDescription = null, modifier = Modifier.size(IconSize.Sm))
                Spacer(modifier = Modifier.width(Spacing.Compact))
                Text("复制路径")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("确认关闭")
            }
        }
    )
}
