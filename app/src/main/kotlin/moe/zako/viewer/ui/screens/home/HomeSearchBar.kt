package moe.zako.viewer.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.zako.viewer.ui.components.ZakoSearchBar
import moe.zako.viewer.ui.theme.AppIcons

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
                    .padding(top = 8.dp, bottom = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
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
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
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
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(if (allSelected) "取消全选" else "全选", fontSize = 12.sp)
                                }
                                TextButton(
                                    onClick = onDeleteSelectedHistory,
                                    enabled = selectedHistoryQueries.isNotEmpty(),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "删除(${selectedHistoryQueries.size})",
                                        fontSize = 12.sp,
                                        color = if (selectedHistoryQueries.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                                TextButton(
                                    onClick = onClearAllHistory,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("清空全部", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                                TextButton(
                                    onClick = onExitHistoryMultiSelect,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("完成", fontSize = 12.sp)
                                }
                            } else {
                                TextButton(
                                    onClick = { onEnterHistoryMultiSelect(null) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(AppIcons.MultiSelect, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("多选/管理", fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        onSearchFocusChanged(false)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(AppIcons.ExpandLess, contentDescription = "收起", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 搜索词条流式列表
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                            Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
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
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Close,
                                                contentDescription = "删除记录",
                                                modifier = Modifier.size(14.dp)
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
