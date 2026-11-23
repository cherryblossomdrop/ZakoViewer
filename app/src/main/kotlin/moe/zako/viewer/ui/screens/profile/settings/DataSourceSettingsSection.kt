package moe.zako.viewer.ui.screens.profile.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.data.source.cosav.CosAvDataSource
import moe.zako.viewer.ui.theme.AppIcons
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
                text = "线路选优",
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
                        text = "自动测速",
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
                        text = "默认线路",
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

/**
 * 数据源设置独立子页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDataSourceScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("源站设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = AppIcons.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.Lg)
        ) {
            // 内置数据源插件开关区域
            if (state.visibleDataSourceSettingItems.contains(DataSourceSettingItem.COSAV_PLUGIN)) {
                item {
                    SettingsCard(shape = RoundedCornerShape(CornerRadius.CardGroup)) {
                        SettingsSwitchItem(
                            title = "CosAV 插件",
                            subtitle = "启用后提供在线推荐视频流、全站搜索与详细信息解析",
                            checked = state.enabledDataSourceIds.contains(CosAvDataSource.ID),
                            onCheckedChange = { viewModel.toggleDataSourceEnabled(CosAvDataSource.ID) }
                        )
                    }
                }
            }

            // CDN 选线与测速策略设置
            item {
                DataSourceSettingsCard(
                    autoSelectCdn = state.autoSelectCdn,
                    defaultCdnIndex = state.defaultCdnIndex,
                    onAutoSelectCdnChange = { viewModel.setAutoSelectCdn(it) },
                    onDefaultCdnIndexChange = { viewModel.setDefaultCdnIndex(it) }
                )
            }
        }
    }
}
