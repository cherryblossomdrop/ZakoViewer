package moe.zako.viewer.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.data.source.cosav.CosComment
import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.ui.components.VideoCard
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing

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
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Intermediate)
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(CornerRadius.Md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.Expanded)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.Comment,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Intermediate)
                    )
                    Spacer(modifier = Modifier.width(Spacing.Sm))
                    Text(
                        text = "留言区 (${comments.size} 条评论)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = "展开/折叠"
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = Spacing.Intermediate)) {
                    if (comments.isEmpty()) {
                        Text(
                            text = "暂无留言，快来抢沙发吧~",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        comments.forEach { comment ->
                            CommentItem(comment)
                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.Compact))
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
        Spacer(modifier = Modifier.height(Spacing.Xxs))
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
            contentPadding = PaddingValues(bottom = Spacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Intermediate),
            verticalArrangement = Arrangement.spacedBy(Spacing.Intermediate)
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
                modifier = Modifier.padding(start = Spacing.Lg, top = Spacing.Md, bottom = Spacing.Sm)
            )
        }

        val pairs = relatedList.chunked(2)
        items(pairs, key = { it.joinToString("_") { v -> v.id } }) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Lg, vertical = Spacing.Compact),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Intermediate)
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
            Spacer(modifier = Modifier.height(Spacing.Xl))
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
                Spacer(modifier = Modifier.height(Spacing.Md))

                ListItem(
                    headlineContent = { Text("内置多节点多线程下载器") },
                    supportingContent = { Text("并发向每一个 CDN 节点拉取分片，支持断点续传") },
                    leadingContent = { Icon(imageVector = AppIcons.Speed, contentDescription = null) },
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
