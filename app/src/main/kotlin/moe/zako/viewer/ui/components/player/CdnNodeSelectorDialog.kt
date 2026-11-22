package moe.zako.viewer.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.zako.viewer.core.cdn.CdnNode
import moe.zako.viewer.ui.theme.AppIcons

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
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    text = if (isTesting) "正在并发对所有线路测速，完成后将自动切至最优节点..." else "已测速完成并按质量排序，点击任意节点可手动切换：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(cdnNodes) { node ->
                        val isCurrent = !isLocalPlayback && node.url == currentNode?.url
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
                            leadingContent = {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
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
