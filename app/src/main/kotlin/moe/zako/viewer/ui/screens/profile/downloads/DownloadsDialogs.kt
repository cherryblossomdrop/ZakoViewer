package moe.zako.viewer.ui.screens.profile.downloads

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import moe.zako.viewer.core.permission.StoragePermissionHelper
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.ui.components.VideoActionBottomSheet
import moe.zako.viewer.ui.components.VideoMenuAction
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.theme.AppIcons
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 下载任务长按菜单底部抽屉
 */
@Composable
fun DownloadActionBottomSheet(
    target: DownloadEntity,
    onEnterMultiSelect: (String) -> Unit,
    onAddToFavorites: (DownloadEntity) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onShowFileInfo: (DownloadEntity) -> Unit,
    onDismissRequest: () -> Unit
) {
    val actions = listOf(
        VideoMenuAction(
            id = "multi_select",
            title = "多选",
            icon = AppIcons.MultiSelect,
            onClick = { onEnterMultiSelect(target.videoId) }
        ),
        VideoMenuAction(
            id = "add_to_favorites",
            title = "添加到收藏夹",
            icon = AppIcons.AddToFavorites,
            supportingText = "将视频添加到自定义收藏夹",
            onClick = { onAddToFavorites(target) }
        ),
        VideoMenuAction(
            id = "detail",
            title = "查看详情",
            icon = AppIcons.VideoDetail,
            supportingText = "查看本视频应用内详情页",
            onClick = { onNavigateToDetail(target.videoId) }
        ),
        VideoMenuAction(
            id = "file_info",
            title = "文件信息",
            icon = AppIcons.FileInfo,
            supportingText = "查看文件名、本地存储路径、下载状态与修改时间",
            onClick = { onShowFileInfo(target) }
        )
    )

    VideoActionBottomSheet(
        title = target.title,
        actions = actions,
        onDismissRequest = onDismissRequest
    )
}

/**
 * 本地文件不存在提示弹窗
 */
@Composable
fun MissingFileDialog(
    target: DownloadEntity,
    onRedownload: () -> Unit,
    onPlayOnline: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("本地文件不存在") },
        text = {
            Text(
                text = "检测到视频「${target.title}」对应的本地文件已被移动或从外部删除。为避免无感消耗流量，请选择在线播放或重新下载。",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onRedownload) {
                Text("重新下载")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onPlayOnline) {
                    Text("在线播放")
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

/**
 * 批量删除确认弹窗
 */
@Composable
fun BatchDeleteConfirmDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("确认批量删除") },
        text = {
            Text("确定要删除所选的 $selectedCount 个任务及其对应的本地视频文件吗？此操作不可恢复。")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("确认删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 存储权限引导弹窗
 */
@Composable
fun DownloadPermissionGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
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
                onDismiss()
                StoragePermissionHelper.requestAllFilesAccess(context)
            }) {
                Text("前往设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}


