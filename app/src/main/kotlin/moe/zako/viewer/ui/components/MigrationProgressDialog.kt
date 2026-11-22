package moe.zako.viewer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing

/**
 * 跨目录迁移确认与进度统一对话框
 *
 * 整合离线下载管理与全局存储设置两处的目录迁移确认及进度展示。
 * 严格收敛尺寸与间距到 Design Tokens。
 *
 * @param isMigrating 是否正在执行后台物理迁移
 * @param progressText 当前迁移进度文案（如 "正在迁移 (1/5): xxx.mp4"）
 * @param filesCount 待迁移文件总数
 * @param targetPath 目标新目录路径
 * @param onConfirm 确认迁移并更新回调
 * @param onSkip 跳过迁移仅更改路径回调
 * @param onDismiss 取消弹窗回调
 */
@Composable
fun MigrationProgressDialog(
    isMigrating: Boolean,
    progressText: String,
    filesCount: Int,
    targetPath: String,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isMigrating) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isMigrating,
            dismissOnClickOutside = !isMigrating
        ),
        title = { Text(if (isMigrating) "正在迁移文件" else "迁移已下载文件") },
        text = {
            if (isMigrating) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.Md)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(IconSize.Xl))
                    Spacer(modifier = Modifier.height(Spacing.Lg))
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    Text(
                        text = "检测到原下载目录存在 ${filesCount} 个已下载视频文件。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "是否将已下载视频完整移动至新目录，并同步更新本地记录？",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "新目录: $targetPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (!isMigrating) {
                Button(onClick = onConfirm) {
                    Text("迁移并更新")
                }
            }
        },
        dismissButton = {
            if (!isMigrating) {
                Row {
                    TextButton(onClick = onSkip) {
                        Text("仅更改路径")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            }
        }
    )
}
