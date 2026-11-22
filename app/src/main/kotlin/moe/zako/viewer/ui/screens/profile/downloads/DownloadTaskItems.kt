package moe.zako.viewer.ui.screens.profile.downloads

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.ui.components.DurationBadge
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled
import java.io.File
import java.util.Locale

/**
 * 已下载任务条目卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompletedDownloadItem(
    item: DownloadEntity,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val isCoverBlurEnabled = LocalCoverBlurEnabled.current
    val file = remember(item.savePath) { File(item.savePath) }
    val fileExists = file.exists() && file.length() > 0L

    // 优先加载本地持久化封面，缺失时自动补全
    val coverModel = remember(item.videoId, item.coverUrl) {
        CoverCacheManager.getCoverModel(context, item.videoId, item.coverUrl, autoFetch = true)
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else if (!fileExists) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) onSelectToggle() else onPlay()
                },
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() }
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            // 视频缩略图封面展示（本地文件已删除则去色置灰）
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = null,
                    colorFilter = if (!fileExists) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isCoverBlurEnabled) Modifier.blur(18.dp).clipToBounds() else Modifier),
                    contentScale = ContentScale.Crop,
                    alpha = if (!fileExists) 0.5f else 1f
                )
                DurationBadge(duration = item.duration)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!fileExists) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
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
            }

            if (!isMultiSelectMode) {
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = if (!fileExists) AppIcons.WarningAmber else AppIcons.PlayCircle,
                        contentDescription = if (!fileExists) "已删除" else "播放",
                        tint = if (!fileExists) MaterialTheme.colorScheme.error.copy(alpha = 0.65f) else MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = AppIcons.DeleteOutline,
                        contentDescription = "删除",
                        tint = if (!fileExists) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 下载中任务条目卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveDownloadItem(
    item: DownloadEntity,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val isPausedOrFailed = item.status == DownloadEntity.STATUS_PAUSED || item.status == DownloadEntity.STATUS_FAILED
    val isCoverBlurEnabled = LocalCoverBlurEnabled.current

    // 优先加载本地持久化封面，缺失时自动补全
    val coverModel = remember(item.videoId, item.coverUrl) {
        CoverCacheManager.getCoverModel(context, item.videoId, item.coverUrl, autoFetch = true)
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) {
                        onSelectToggle()
                    } else {
                        if (isPausedOrFailed) onResume() else onPause()
                    }
                },
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // 视频缩略图封面展示（遵循封面模糊保护开关）
                AsyncImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier
                        .width(90.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (isCoverBlurEnabled) Modifier.blur(18.dp).clipToBounds() else Modifier),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val speedMb = item.speedBps / (1024 * 1024.0)
                    val statusText = when (item.status) {
                        DownloadEntity.STATUS_DOWNLOADING -> "并发拉取中 (${String.format(Locale.getDefault(), "%.1f MB/s", speedMb)})"
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
                        maxLines = 1
                    )
                }

                if (!isMultiSelectMode) {
                    IconButton(onClick = {
                        if (isPausedOrFailed) onResume() else onPause()
                    }) {
                        Icon(
                            imageVector = if (isPausedOrFailed) AppIcons.Play else AppIcons.Pause,
                            contentDescription = if (isPausedOrFailed) "继续下载" else "暂停下载"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = AppIcons.Close, contentDescription = "取消")
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val formatMb = { bytes: Long -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024 * 1024.0)) }
                Text(
                    text = "${formatMb(item.downloadedBytes)} / ${formatMb(item.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(item.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
