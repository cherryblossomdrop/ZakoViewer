package moe.zako.viewer.ui.screens.profile.backup

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import moe.zako.viewer.core.storage.ExportResult
import moe.zako.viewer.data.backup.RestoreResult
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.BorderWidth
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.ProgressBarTokens
import moe.zako.viewer.ui.theme.Spacing

/**
 * 备份与恢复全流程进度弹窗（支持分阶段实时进度与常亮防护）
 */
@Composable
fun BackupProgressDialog(
    isRestoring: Boolean,
    includeDownloadedVideos: Boolean,
    progressStage: String,
    progressPercent: Float?,
    progressDetail: String
) {
    AlertDialog(
        onDismissRequest = { /* 正在恢复/导出中，禁止任意关闭以防破坏数据 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.Md),
                    strokeWidth = BorderWidth.ProgressIndicator,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(Spacing.Md))
                Text(
                    text = if (isRestoring) "正在恢复备份…" else "正在导出备份…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md)
            ) {
                // 防退出与大文件耗时耐性提醒文案
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Sm),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.Sm),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = AppIcons.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IconSize.Sm)
                        )
                        Spacer(modifier = Modifier.width(Spacing.Sm))
                        Text(
                            text = if (isRestoring) {
                                "若备份包含视频文件，体积较大、耗时可能较长，请耐心等待，期间请勿退出或关闭应用。"
                            } else if (includeDownloadedVideos) {
                                "正在打包离线视频与数据，体积较大、耗时可能较长，请耐心等待，期间请勿退出或关闭应用。"
                            } else {
                                "正在整理并导出应用数据，请稍候，期间请勿退出或关闭应用。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // 当前所处阶段
                if (progressStage.isNotBlank()) {
                    Text(
                        text = progressStage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 进度条与百分比
                if (progressPercent != null) {
                    val percent = progressPercent
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                        LinearProgressIndicator(
                            progress = { percent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ProgressBarTokens.Height)
                        )
                        Text(
                            text = "${(percent * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ProgressBarTokens.Height)
                    )
                }

                // 详细子进度/文件信息
                if (progressDetail.isNotBlank()) {
                    Text(
                        text = progressDetail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {} // 运行时不提供确认按钮，强制等待完成
    )
}

/**
 * 备份恢复结果反馈弹窗（汇总本次恢复的数量与封面补全状态）
 */
@Composable
fun BackupRestoreResultDialog(
    result: RestoreResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (result.success) AppIcons.CheckCircle else AppIcons.Error,
                    contentDescription = null,
                    tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(IconSize.Lg)
                )
                Spacer(modifier = Modifier.width(Spacing.Sm))
                Text(if (result.success) "备份恢复成功" else "备份恢复失败")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                if (result.success) {
                    Text(
                        text = "已成功将备份中的全部数据还原至本地：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Sm),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.Md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("收藏夹目录数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${result.folderCount} 个", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("收藏视频总数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${result.favoriteItemCount} 部", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("历史记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${result.historyCount} 条", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("稍后再看列表", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${result.watchLaterCount} 部", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            if (result.videoCount > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("还原视频文件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${result.videoCount} 个", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("视频封面状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val coverText = if (result.coverCount > 0) "已恢复 ${result.coverCount} 张" else "已就绪 (按需自动补全)"
                                Text(coverText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "恢复过程中发生错误：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Sm),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = result.errorMessage ?: "未知错误",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(Spacing.Sm)
                        )
                    }
                    Text(
                        text = "临时解压文件已被自动安全清理，未对现有数据造成不可逆破坏。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (result.success) "确定" else "关闭")
            }
        }
    )
}

/**
 * 备份导出成功结果弹窗（替代纯 Toast 提示）
 */
@Composable
fun BackupExportResultDialog(
    result: ExportResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.Lg)
                )
                Spacer(modifier = Modifier.width(Spacing.Sm))
                Text("备份导出成功")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                Text(
                    text = "数据备份已完整写入公共存储：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Sm),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(result.displayPath))
                            ZakoToast.show("已复制文件路径到剪贴板")
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
                TextButton(onClick = onDismiss) {
                    Text("确定")
                }
            }
        }
    )
}
