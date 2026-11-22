package moe.zako.viewer.ui.screens.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.zako.viewer.data.repository.LayoutMode
import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.ui.components.FilterBarCircleButton
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.FilterBarTokens
import moe.zako.viewer.ui.theme.FontSizes
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.TouchTarget
import java.util.Calendar

/**
 * 首页水平筛选与排序控制条
 */
@Composable
fun HomeFilterBar(
    hasActiveFilter: Boolean,
    onOpenFilterSheet: () -> Unit,
    sortDimension: SortDimension,
    sortDirection: SortDirection,
    onSortDimensionChange: (SortDimension) -> Unit,
    onSortDirectionChange: (SortDirection) -> Unit,
    layoutMode: LayoutMode,
    onToggleLayoutMode: () -> Unit,
    searchQuery: String,
    selectedCategory: String,
    selectedTag: String,
    onSelectCategory: (String) -> Unit,
    onFilterSearchByTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    val isSortActive = sortDimension != SortDimension.VIEWS || sortDirection != SortDirection.DESCENDING

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(FilterBarTokens.ItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 扩充筛选抽屉入口（统一采用 Material 3 Tonal 逻辑与高对比激活态）
        FilterBarCircleButton(
            onClick = onOpenFilterSheet,
            icon = AppIcons.Tune,
            contentDescription = "筛选",
            isActive = hasActiveFilter
        )

        // 2. 排序交互重构图标按钮与弹出菜单（激活态跟随非默认排序维度与方向）
        Box {
            FilterBarCircleButton(
                onClick = { showSortMenu = true },
                icon = AppIcons.Sort,
                contentDescription = "排序",
                isActive = isSortActive
            )

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                Text(
                    text = "排序维度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Compact)
                )
                SortDimension.values().forEach { dim ->
                    DropdownMenuItem(
                        text = { Text(dim.label) },
                        trailingIcon = {
                            if (sortDimension == dim) {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.Intermediate),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            onSortDimensionChange(dim)
                            showSortMenu = false
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.Xs))

                Text(
                    text = "排序方向",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Compact)
                )
                SortDirection.values().forEach { dir ->
                    DropdownMenuItem(
                        text = { Text(dir.label) },
                        trailingIcon = {
                            if (sortDirection == dir) {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.Intermediate),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            onSortDirectionChange(dir)
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        // 3. 布局模式切换按钮 (瀑布流 / 分页排布，非默认态激活高亮)
        FilterBarCircleButton(
            onClick = onToggleLayoutMode,
            icon = if (layoutMode == LayoutMode.WATERFALL) AppIcons.GridView else AppIcons.ViewAgenda,
            contentDescription = if (layoutMode == LayoutMode.WATERFALL) "切换为分页排布" else "切换为瀑布流",
            isActive = layoutMode != LayoutMode.WATERFALL
        )

        // 4. 快速分类与热门标签
        val allChips = remember {
            val list = mutableListOf<String>()
            list.addAll(HomeViewModel.AVAILABLE_CATEGORIES)
            HomeViewModel.POPULAR_TAGS.forEach { t ->
                if (!list.contains(t)) list.add(t)
            }
            list
        }

        allChips.forEach { chipTag ->
            val isSelected = if (searchQuery.isNotEmpty()) {
                if (chipTag in listOf("全部", "推荐")) {
                    selectedTag.isEmpty()
                } else {
                    selectedTag.equals(chipTag, ignoreCase = true)
                }
            } else {
                selectedCategory == chipTag && selectedTag.isEmpty()
            }

            FilterChip(
                selected = isSelected,
                onClick = {
                    if (searchQuery.isNotEmpty()) {
                        onFilterSearchByTag(chipTag)
                    } else {
                        onSelectCategory(chipTag)
                    }
                },
                label = { Text(chipTag) }
            )
        }
    }
}

/**
 * 首页综合筛选底部抽屉
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeFilterBottomSheet(
    initialCategory: String,
    initialTag: String,
    initialTimeRange: TimeRange,
    initialIncludeVip: Boolean,
    currentSearchQuery: String,
    onApplyFilters: (category: String, tag: String, timeRange: TimeRange, includeVip: Boolean) -> Unit,
    onAppendTagToSearch: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var tempCategory by remember { mutableStateOf(initialCategory) }
    var tempTag by remember { mutableStateOf(initialTag) }
    var tempTimeRange by remember { mutableStateOf(initialTimeRange) }
    var tempIncludeVip by remember(initialIncludeVip) { mutableStateOf(initialIncludeVip) }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Dp20, vertical = Spacing.Md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "视频综合筛选",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    tempCategory = "推荐"
                    tempTag = ""
                    tempTimeRange = TimeRange.ALL
                    tempIncludeVip = true
                }) {
                    Text("重置")
                }
            }

            // 1. 分类维度
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                Text(
                    text = "分类",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
                ) {
                    HomeViewModel.AVAILABLE_CATEGORIES.forEach { cat ->
                        FilterChip(
                            selected = tempCategory == cat,
                            onClick = { tempCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            // 2. 常用标签维度
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                Text(
                    text = "标签",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
                ) {
                    FilterChip(
                        selected = tempTag.isEmpty(),
                        onClick = { tempTag = "" },
                        label = { Text("全部") }
                    )
                    HomeViewModel.POPULAR_TAGS.forEach { tag ->
                        FilterChip(
                            selected = currentSearchQuery.contains(tag) || tempTag == tag,
                            onClick = {
                                onAppendTagToSearch(tag)
                                onDismissRequest()
                            },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            // 3. 时间范围维度
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                Text(
                    text = "时间范围",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
                ) {
                    TimeRange.values().forEach { range ->
                        FilterChip(
                            selected = tempTimeRange == range,
                            onClick = { tempTimeRange = range },
                            label = { Text(range.label) }
                        )
                    }
                }
            }

            // 4. 包含维度 (VIP抢先看视频)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                Text(
                    text = "包含",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
                ) {
                    FilterChip(
                        selected = tempIncludeVip,
                        onClick = { tempIncludeVip = !tempIncludeVip },
                        label = { Text("VIP抢先看视频") },
                        leadingIcon = {
                            if (tempIncludeVip) {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.Sm)
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Sm))

            Button(
                onClick = {
                    onApplyFilters(tempCategory, tempTag, tempTimeRange, tempIncludeVip)
                    onDismissRequest()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TouchTarget.Min)
            ) {
                Text("确定应用筛选", fontSize = FontSizes.Body)
            }

            Spacer(modifier = Modifier.height(Spacing.Lg))
        }
    }
}

internal fun matchesTimeRange(video: CosVideoItem, timeRange: TimeRange): Boolean {
    val maxDays = timeRange.maxDays ?: return true
    val maxAgeMs = maxDays * 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()

    // 1. 检查数字时间戳 (addtimestamp)
    val ts = when (val t = video.addtimestamp) {
        is Number -> {
            val v = t.toLong()
            if (v < 10000000000L) v * 1000L else v
        }
        is String -> t.toLongOrNull()?.let { if (it < 10000000000L) it * 1000L else it }
        else -> null
    }
    if (ts != null && ts > 0) {
        return (now - ts) <= maxAgeMs
    }

    // 2. 解析相对时间文本 (addtime，如 "766 天 前", "3 天 前", "1 小时 前", "2 个月 前", "1 年 前")
    val addTimeStr = video.addtime?.trim()
    if (!addTimeStr.isNullOrBlank()) {
        if (addTimeStr.contains("秒") || addTimeStr.contains("分") || addTimeStr.contains("小时") || addTimeStr.contains("刚刚")) {
            return true // 当天内最新发布，符合任意时间范围要求
        }
        val dayMatch = Regex("(\\d+)\\s*天").find(addTimeStr)
        if (dayMatch != null) {
            val days = dayMatch.groupValues[1].toLongOrNull()
            if (days != null) {
                return days <= maxDays
            }
        }
        val monthMatch = Regex("(\\d+)\\s*个?月").find(addTimeStr)
        if (monthMatch != null) {
            val months = monthMatch.groupValues[1].toLongOrNull()
            if (months != null) {
                return (months * 30L) <= maxDays
            }
        }
        val yearMatch = Regex("(\\d+)\\s*年").find(addTimeStr)
        if (yearMatch != null) {
            val years = yearMatch.groupValues[1].toLongOrNull()
            if (years != null) {
                return (years * 365L) <= maxDays
            }
        }
    }

    // 3. 解析标准日期文本 (adddate，格式如 "2024-10-14" 或 "2026-11-19")
    val addDateStr = video.adddate?.trim()
    if (!addDateStr.isNullOrBlank()) {
        try {
            val datePart = addDateStr.split(" ")[0]
            val parts = datePart.split("-")
            if (parts.size >= 3) {
                val year = parts[0].toIntOrNull()
                val month = parts[1].toIntOrNull()
                val day = parts[2].toIntOrNull()
                if (year != null && month != null && day != null) {
                    val cal = Calendar.getInstance()
                    cal.set(year, month - 1, day, 0, 0, 0)
                    val diffMs = now - cal.timeInMillis
                    return diffMs <= maxAgeMs
                }
            }
        } catch (_: Exception) {}
    }

    return false
}

internal fun sortVideos(
    videos: List<CosVideoItem>,
    dimension: SortDimension,
    direction: SortDirection
): List<CosVideoItem> {
    val comparator = when (dimension) {
        SortDimension.VIEWS -> compareBy<CosVideoItem> { it.displayViews }
        SortDimension.PUBLISH_TIME -> compareBy<CosVideoItem> { it.adddate ?: it.addtime ?: "" }
        SortDimension.POPULARITY -> compareBy<CosVideoItem> {
            val h = (it.hits as? Number)?.toLong() ?: (it.hits as? String)?.toLongOrNull() ?: 0L
            val l = (it.likes as? Number)?.toLong() ?: (it.likes as? String)?.toLongOrNull() ?: 0L
            h + l + it.displayViews
        }
    }
    return if (direction == SortDirection.DESCENDING) {
        videos.sortedWith(comparator.reversed())
    } else {
        videos.sortedWith(comparator)
    }
}
