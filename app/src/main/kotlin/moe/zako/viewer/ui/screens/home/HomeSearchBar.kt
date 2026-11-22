package moe.zako.viewer.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.zako.viewer.ui.components.ZakoSearchBar
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.FontSizes
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing

/**
 * 首页顶部搜索栏与搜索历史管理面板
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onPerformSearch: () -> Unit,
    isSearchFocused: Boolean,
    onSearchFocusChanged: (Boolean) -> Unit,
    searchHistory: List<String>,
    isHistoryMultiSelect: Boolean,
    selectedHistoryQueries: Set<String>,
    onToggleSelectAllHistory: () -> Unit,
    onDeleteSelectedHistory: () -> Unit,
    onClearAllHistory: () -> Unit,
    onEnterHistoryMultiSelect: (String?) -> Unit,
    onExitHistoryMultiSelect: () -> Unit,
    onToggleHistorySelection: (String) -> Unit,
    onDeleteSearchHistoryItem: (String) -> Unit,
    onHistoryItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.fillMaxWidth()) {
        // 统一搜索输入框（右侧箭头已彻底移除，复用公共组件与一键清空微动效）
        ZakoSearchBar(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            onSearch = onPerformSearch,
            onClear = {
                onPerformSearch()
            },
            onFocusChanged = onSearchFocusChanged,
            modifier = Modifier.fillMaxWidth()
        )

        // 搜索历史面板（当点击搜索框获得焦点时展开，支持单选与多选批量删除）
        AnimatedVisibility(visible = isSearchFocused && searchHistory.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.Sm, bottom = Spacing.Xs),
                shape = RoundedCornerShape(CornerRadius.Lg),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                )
            ) {
                Column(modifier = Modifier.padding(Spacing.Md)) {
                    // 标题与管理栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = AppIcons.History,
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.Intermediate),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(Spacing.Compact))
                            Text(
                                text = if (isHistoryMultiSelect) "选择历史 (${selectedHistoryQueries.size})" else "搜索历史",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isHistoryMultiSelect) {
                                val allSelected = searchHistory.isNotEmpty() && selectedHistoryQueries.containsAll(searchHistory)
                                TextButton(
                                    onClick = onToggleSelectAllHistory,
                                    contentPadding = PaddingValues(horizontal = Spacing.Compact, vertical = Spacing.Xxs)
                                ) {
                                    Text(if (allSelected) "取消全选" else "全选", fontSize = FontSizes.Caption)
                                }
                                TextButton(
                                    onClick = onDeleteSelectedHistory,
                                    enabled = selectedHistoryQueries.isNotEmpty(),
                                    contentPadding = PaddingValues(horizontal = Spacing.Compact, vertical = Spacing.Xxs)
                                ) {
                                    Text(
                                        "删除(${selectedHistoryQueries.size})",
                                        fontSize = FontSizes.Caption,
                                        color = if (selectedHistoryQueries.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        onClearAllHistory()
                                        focusManager.clearFocus()
                                        onSearchFocusChanged(false)
                                    },
                                    contentPadding = PaddingValues(horizontal = Spacing.Compact, vertical = Spacing.Xxs)
                                ) {
                                    Text("清空全部", fontSize = FontSizes.Caption, color = MaterialTheme.colorScheme.error)
                                }
                                TextButton(
                                    onClick = onExitHistoryMultiSelect,
                                    contentPadding = PaddingValues(horizontal = Spacing.Compact, vertical = Spacing.Xxs)
                                ) {
                                    Text("完成", fontSize = FontSizes.Caption)
                                }
                            } else {
                                TextButton(
                                    onClick = { onEnterHistoryMultiSelect(null) },
                                    contentPadding = PaddingValues(horizontal = Spacing.Compact, vertical = Spacing.Xxs)
                                ) {
                                    Icon(AppIcons.MultiSelect, contentDescription = null, modifier = Modifier.size(IconSize.Sm))
                                    Spacer(modifier = Modifier.width(Spacing.Xs))
                                    Text("多选/管理", fontSize = FontSizes.Caption)
                                }
                                IconButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        onSearchFocusChanged(false)
                                    },
                                    modifier = Modifier.size(IconSize.Lg)
                                ) {
                                    Icon(AppIcons.ExpandLess, contentDescription = "收起", modifier = Modifier.size(IconSize.Intermediate))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.Sm))

                    // 搜索词条流式列表
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Compact)
                    ) {
                        searchHistory.forEach { historyQuery ->
                            val isSelected = selectedHistoryQueries.contains(historyQuery)

                            if (isHistoryMultiSelect) {
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onToggleHistorySelection(historyQuery) },
                                    label = { Text(historyQuery, maxLines = 1) },
                                    leadingIcon = {
                                        if (isSelected) {
                                            Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(IconSize.Sm))
                                        }
                                    }
                                )
                            } else {
                                InputChip(
                                    selected = false,
                                    onClick = {
                                        onHistoryItemClick(historyQuery)
                                        focusManager.clearFocus()
                                        onSearchFocusChanged(false)
                                    },
                                    label = {
                                        Text(
                                            text = historyQuery,
                                            maxLines = 1,
                                            modifier = Modifier.combinedClickable(
                                                onClick = {
                                                    onHistoryItemClick(historyQuery)
                                                    focusManager.clearFocus()
                                                    onSearchFocusChanged(false)
                                                },
                                                onLongClick = {
                                                    onEnterHistoryMultiSelect(historyQuery)
                                                }
                                            )
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { onDeleteSearchHistoryItem(historyQuery) },
                                            modifier = Modifier.size(IconSize.Intermediate)
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Close,
                                                contentDescription = "删除记录",
                                                modifier = Modifier.size(IconSize.Xs)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
