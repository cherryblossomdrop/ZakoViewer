package moe.zako.viewer.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.core.cdn.CdnNode
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.BorderWidth
import moe.zako.viewer.ui.theme.DialogTokens
import moe.zako.viewer.ui.theme.FontSizes
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing

/**
 * 全屏播放与详情页内嵌播放通用的 CDN 节点测速与选择弹窗面板
 * 消费 MenuTokens 与 AppIcons，支持并发测速状态展示与指标实时高亮
 */
@Composable
fun CdnNodeSelectorDialog(
    cdnNodes: List<CdnNode>,
    currentNode: CdnNode?,
    isLocalPlayback: Boolean,
    isTesting: Boolean,
    onSelectNode: (CdnNode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("选择 CDN 播放节点")
                if (isTesting) {
                    Spacer(modifier = Modifier.width(Spacing.Sm))
                    CircularProgressIndicator(
                        modifier = Modifier.size(IconSize.Sm),
                        strokeWidth = BorderWidth.Thick,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    text = if (isTesting) "正在测速，完成后将自动切至最快线路..." else "已按测速排序，点击可手动切换：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.Md))

                LazyColumn(modifier = Modifier.heightIn(max = DialogTokens.CompactListMaxHeight)) {
                    items(cdnNodes) { node ->
                        val isCurrent = !isLocalPlayback && node.url == currentNode?.url
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(node.name, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                                    if (isCurrent) {
                                        Spacer(modifier = Modifier.width(Spacing.Compact))
                                        Text("(当前)", color = MaterialTheme.colorScheme.primary, fontSize = FontSizes.Caption)
                                    }
                                }
                            },
                            supportingContent = {
                                Text(
                                    text = "测速指标: ${node.displayMetric}",
                                    color = if (node.testSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = if (isCurrent) AppIcons.CheckCircle else AppIcons.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable { onSelectNode(node) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
