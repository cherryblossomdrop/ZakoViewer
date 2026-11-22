package moe.zako.viewer.ui.screens.detail

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.zako.viewer.core.cdn.CdnNode
import moe.zako.viewer.core.player.HwDecMode
import moe.zako.viewer.core.player.PlayerEngine
import moe.zako.viewer.data.source.cosav.CosComment
import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.ui.components.VideoCard
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.theme.AppIcons

/**
 * 详情页留言区与相关视频高内聚 UI 区块
 * 统筹评论区折叠卡片、横屏双列推荐网格、竖屏列表推荐分区与下载选择弹窗
 */

/**
 * 详情页留言区卡片 (支持折叠/展开与评论列表呈现)
 */
@Composable
fun DetailCommentsCard(
    video: CosVideoItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val comments = video.comments ?: emptyList()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Comment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "留言区 (${comments.size} 条评论)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) AppIcons.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "展开/折叠"
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (comments.isEmpty()) {
                        Text(
                            text = "暂无留言，快来抢沙发吧~",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        comments.forEach { comment ->
                            CommentItem(comment)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单条评论卡片展示
 */
@Composable
fun CommentItem(comment: CosComment) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = comment.displayAuthor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            val timeText = comment.addtime ?: comment.createdAt
            timeText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = comment.displayContent,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * 平板横屏专属：右栏独立可滚动的相关推荐视频双列网格
 */
@Composable
fun DetailRelatedVideosGrid(
    relatedList: List<CosVideoItem>,
    onNavigateToDetail: (videoId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (relatedList.isNotEmpty()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(relatedList, key = { it.id }) { relItem ->
                VideoCard(
                    video = relItem,
                    showVipBadge = true,
                    onClick = { onNavigateToDetail(relItem.id) }
                )
            }
        }
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无相关推荐视频",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 手机竖屏模式：LazyColumn 内挂载的双列相关推荐视频分区
 */
fun LazyListScope.detailRelatedVideosSection(
    relatedList: List<CosVideoItem>,
    onNavigateToDetail: (videoId: String) -> Unit
) {
    if (relatedList.isNotEmpty()) {
        item {
            Text(
                text = "相关视频 (${relatedList.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
            )
        }

        val pairs = relatedList.chunked(2)
        items(pairs, key = { it.joinToString("_") { v -> v.id } }) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    VideoCard(
                        video = pair[0],
                        showVipBadge = true,
                        onClick = { onNavigateToDetail(pair[0].id) }
                    )
                }
                if (pair.size > 1) {
                    Box(modifier = Modifier.weight(1f)) {
                        VideoCard(
                            video = pair[1],
                            showVipBadge = true,
                            onClick = { onNavigateToDetail(pair[1].id) }
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 长按下载方式弹窗 (内置下载器 vs 外部下载器)
 */
@Composable
fun DetailDownloadChoiceDialog(
    onDismissRequest: () -> Unit,
    onStartInternalDownload: () -> Unit,
    onStartExternalDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("选择下载方式") },
        text = {
            Column {
                Text(
                    text = "选择要使用的下载引擎：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                ListItem(
                    headlineContent = { Text("内置多节点多线程下载器") },
                    supportingContent = { Text("并发向每一个 CDN 节点拉取分片，支持断点续传") },
                    leadingContent = { Icon(imageVector = Icons.Default.Speed, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onStartInternalDownload()
                    }
                )

                ListItem(
                    headlineContent = { Text("外部下载器 (1DM / ADM)") },
                    supportingContent = { Text("透传视频直链与 HTTP 请求头唤起外部下载器") },
                    leadingContent = { Icon(imageVector = AppIcons.OpenInNew, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onStartExternalDownload()
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        }
    )
}

/**
 * 详情页 CDN 节点测速与选优切换弹窗
 */
@Composable
fun DetailCdnDialog(
    state: VideoDetailUiState,
    onSelectNode: (CdnNode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择 CDN 播放节点") },
        text = {
            Column {
                Text(
                    text = "选择最流畅的视频流媒体镜像节点（已实时标注测速结果）：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(state.cdnNodes) { node ->
                        val isCurrent = node.url == state.currentCdnNode?.url
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(node.name, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                                    if (isCurrent) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(当前)", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                    }
                                }
                            },
                            supportingContent = {
                                Text(
                                    text = "测速指标: ${node.displayMetric}",
                                    color = if (node.testSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                if (isCurrent) {
                                    Icon(imageVector = AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable { onSelectNode(node) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/**
 * 详情页播放内核切换弹窗 (系统 / MPV)
 */
@Composable
fun DetailEngineDialog(
    currentEngine: PlayerEngine,
    onSelectEngine: (PlayerEngine) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换播放内核") },
        text = {
            Column {
                PlayerEngine.values().forEach { eng ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectEngine(eng) }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(eng.label, fontWeight = if (currentEngine == eng) FontWeight.Bold else FontWeight.Normal)
                        if (currentEngine == eng) {
                            Icon(imageVector = AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 详情页硬件解码设置弹窗
 */
@Composable
fun DetailHwDecDialog(
    currentMode: HwDecMode,
    onSelectMode: (HwDecMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("硬件解码设置") },
        text = {
            Column {
                HwDecMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMode(mode) }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(mode.label, fontWeight = if (currentMode == mode) FontWeight.Bold else FontWeight.Normal)
                        if (currentMode == mode) {
                            Icon(imageVector = AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
