package moe.zako.viewer.ui.screens.profile.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.Spacing

/**
 * 数据源与 CDN 选线设置区块
 */
@Composable
fun DataSourceSettingsCard(
    autoSelectCdn: Boolean,
    defaultCdnIndex: Int,
    onAutoSelectCdnChange: (Boolean) -> Unit,
    onDefaultCdnIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(CornerRadius.Md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.Lg)) {
            Text(
                text = "数据源与 CDN 选线",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "配置在线播放时的节点测速与调度策略（本地视频将始终优先直接播放）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.Md))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAutoSelectCdnChange(!autoSelectCdn) }
                    .padding(vertical = Spacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自动选择最优节点",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (autoSelectCdn) {
                            "播放在线视频时并发测速并自动选取最快线路起播"
                        } else {
                            "已关闭自动测速，每次固定使用下方设定的默认线路"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.Md))
                Switch(
                    checked = autoSelectCdn,
                    onCheckedChange = onAutoSelectCdnChange
                )
            }

            AnimatedVisibility(visible = !autoSelectCdn) {
                Column(modifier = Modifier.padding(top = Spacing.Md)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(Spacing.Intermediate))
                    Text(
                        text = "默认 CDN 线路",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "关闭自动选优后，播放在线视频默认使用的线路：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.Sm))

                    val cdnLineOptions = listOf(
                        0 to "线路 1",
                        1 to "线路 2",
                        2 to "线路 3",
                        3 to "线路 4",
                        4 to "线路 5"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
                    ) {
                        for ((idx, label) in cdnLineOptions) {
                            FilterChip(
                                selected = defaultCdnIndex == idx,
                                onClick = { onDefaultCdnIndexChange(idx) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }
    }
}
