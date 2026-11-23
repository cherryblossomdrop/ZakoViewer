package moe.zako.viewer.ui.screens.profile.downloads

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import moe.zako.viewer.core.permission.StoragePermissionHelper
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.ui.components.VideoActionBottomSheet
import moe.zako.viewer.ui.components.VideoMenuAction
import moe.zako.viewer.ui.screens.profile.settings.StoragePermissionGuideDialog
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.Spacing

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
            onClick = { onAddToFavorites(target) }
        ),
        VideoMenuAction(
            id = "detail",
            title = "视频详情",
            icon = AppIcons.VideoDetail,
            onClick = { onNavigateToDetail(target.videoId) }
        ),
        VideoMenuAction(
            id = "file_info",
            title = "文件信息",
            icon = AppIcons.FileInfo,
            supportingText = "查看路径、大小与本地状态",
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
                Spacer(modifier = Modifier.width(Spacing.Xs))
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
 * 存储权限引导弹窗（直接复用全局 StoragePermissionGuideDialog）
 */
@Composable
fun DownloadPermissionGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    StoragePermissionGuideDialog(
        onConfirm = {
            onDismiss()
            StoragePermissionHelper.requestAllFilesAccess(context)
        },
        onDismiss = onDismiss
    )
}
