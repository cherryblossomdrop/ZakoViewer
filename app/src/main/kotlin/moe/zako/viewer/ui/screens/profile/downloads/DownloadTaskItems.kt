package moe.zako.viewer.ui.screens.profile.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.ui.components.VideoListItemCard
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.Spacing
import java.io.File
import java.util.Locale

/**
 * 已下载任务条目卡片 (复用全局统一 VideoListItemCard)
 */
@Composable
fun CompletedDownloadItem(
    item: DownloadEntity,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onPlay: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val file = remember(item.savePath) { File(item.savePath) }
    val fileExists = file.exists() && file.length() > 0L

    val coverModel = remember(item.videoId, item.coverUrl) {
        CoverCacheManager.getCoverModel(context, item.videoId, item.coverUrl, autoFetch = true)
    }

    val cardColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else if (!fileExists) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    VideoListItemCard(
        coverModel = coverModel,
        title = item.title,
        isSelectionMode = isMultiSelectMode,
        isSelected = isSelected,
        onSelectToggle = onSelectToggle,
        onClick = {
            if (isMultiSelectMode) onSelectToggle() else onPlay()
        },
        onLongClick = onLongClick,
        duration = item.duration,
        coverAlpha = if (!fileExists) 0.5f else 1f,
        coverColorFilter = if (!fileExists) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
        titleColor = if (!fileExists) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface,
        containerColor = cardColor,
        subtitle = {
            if (!fileExists) {
                Text(
                    text = "此视频已被删除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            } else {
                val sizeMb = item.totalBytes / (1024 * 1024.0)
                Text(
                    text = String.format(Locale.getDefault(), "%.1f MB | 本地已缓存", sizeMb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        actions = {
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = if (!fileExists) AppIcons.WarningAmber else AppIcons.PlayCircle,
                    contentDescription = if (!fileExists) "已删除" else "播放",
                    tint = if (!fileExists) MaterialTheme.colorScheme.error.copy(alpha = 0.65f) else MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

/**
 * 下载中任务条目卡片 (复用全局统一 VideoListItemCard 与 bottomContent 插槽)
 */
@Composable
fun ActiveDownloadItem(
    item: DownloadEntity,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val isPausedOrFailed = item.status == DownloadEntity.STATUS_PAUSED || item.status == DownloadEntity.STATUS_FAILED

    val coverModel = remember(item.videoId, item.coverUrl) {
        CoverCacheManager.getCoverModel(context, item.videoId, item.coverUrl, autoFetch = true)
    }

    val cardColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    VideoListItemCard(
        coverModel = coverModel,
        title = item.title,
        titleMaxLines = 1,
        isSelectionMode = isMultiSelectMode,
        isSelected = isSelected,
        onSelectToggle = onSelectToggle,
        onClick = {
            if (isMultiSelectMode) {
                onSelectToggle()
            } else {
                if (isPausedOrFailed) onResume() else onPause()
            }
        },
        onLongClick = onLongClick,
        containerColor = cardColor,
        duration = item.duration,
        subtitle = {
            val statusText = when (item.status) {
                DownloadEntity.STATUS_DOWNLOADING -> if (item.activeNodesCount > 0) "并发拉取中 (${item.activeNodesCount} 线路)" else "并发拉取中"
                DownloadEntity.STATUS_PAUSED -> "已暂停 (点击继续)"
                DownloadEntity.STATUS_FAILED -> if (!item.errorMessage.isNullOrEmpty()) "中断: ${item.errorMessage}" else "拉取中断 (点击重试)"
                else -> "准备中..."
            }
            val statusColor = when (item.status) {
                DownloadEntity.STATUS_DOWNLOADING -> MaterialTheme.colorScheme.primary
                DownloadEntity.STATUS_FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            IconButton(onClick = {
                if (isPausedOrFailed) onResume() else onPause()
            }) {
                Icon(
                    imageVector = if (isPausedOrFailed) AppIcons.Play else AppIcons.Pause,
                    contentDescription = if (isPausedOrFailed) "继续下载" else "暂停下载"
                )
            }
        },
        bottomContent = {
            Spacer(modifier = Modifier.height(Spacing.Sm))
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.Xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val formatMb = { bytes: Long -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024 * 1024.0)) }
                val sizeText = "${formatMb(item.downloadedBytes)} / ${formatMb(item.totalBytes)}"
                val speedText = if (item.status == DownloadEntity.STATUS_DOWNLOADING && item.speedBps > 0) {
                    if (item.speedBps >= 1024 * 1024) {
                        String.format(Locale.getDefault(), "%.1f MB/s", item.speedBps / (1024 * 1024.0))
                    } else {
                        String.format(Locale.getDefault(), "%.1f KB/s", item.speedBps / 1024.0)
                    }
                } else null

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (speedText != null) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = speedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Spacing.Sm))
                Text(
                    text = "${(item.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}
