package moe.zako.viewer.ui.screens.home

import moe.zako.viewer.ui.components.ZakoToast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.zako.viewer.core.share.ShareCipherUtil
import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.ui.components.VideoCard
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetail: (videoId: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // 下拉刷新支持
    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
        }
    }
    LaunchedEffect(state.isRefreshing) {
        if (!state.isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    // 暗号识别与普通搜索判定
    val performSearchOrCipher = {
        val query = state.searchQuery.trim()
        if (ShareCipherUtil.containsCipherMarker(query)) {
            val videoId = ShareCipherUtil.decode(query)
            if (videoId != null) {
                onNavigateToDetail(videoId)
            } else {
                ZakoToast.show("无效分享暗号")
            }
        } else {
            focusManager.clearFocus()
            viewModel.submitSearch()
        }
    }

    // Trigger infinite scroll load more when reaching end
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMore()
        }
    }

    // 筛选与排序处理：
    // 1. 包含VIP抢先看视频：开启状态包含 VIP 视频，关闭状态隐藏 VIP 视频
    // 2. 时间范围过滤
    // 3. 排序维度与正序/倒序应用
    val displayedVideos = remember(
        state.videos,
        state.includeVip,
        state.selectedTimeRange,
        state.sortDimension,
        state.sortDirection
    ) {
        var list = if (state.includeVip) {
            state.videos
        } else {
            state.videos.filter { !it.isVipEarlyAccess }
        }

        if (state.selectedTimeRange != TimeRange.ALL) {
            list = list.filter { matchesTimeRange(it, state.selectedTimeRange) }
        }

        sortVideos(list, state.sortDimension, state.sortDirection)
    }

    // 自动补全加载：当客户端过滤导致当前屏暂无匹配项但服务端仍有更多数据时，自动尝试加载下一页（最多连续探测5页）
    LaunchedEffect(displayedVideos.isEmpty(), state.isLoading, state.hasMore, state.videos.size) {
        if (displayedVideos.isEmpty() && state.hasMore && !state.isLoading && !state.isRefreshing && state.videos.isNotEmpty() && state.page < 5) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // In-page search bar with cipher detection (占位文本已按需求移除)
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "搜索")
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.onSearchQueryChange("")
                                    viewModel.submitSearch()
                                }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "清空")
                                }
                            }
                            IconButton(onClick = { performSearchOrCipher() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "提交",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearchOrCipher() })
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filter & Sort Row (Horizontal Scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 扩充筛选抽屉入口
                    val hasActiveFilter = state.selectedTag.isNotEmpty() ||
                            state.selectedTimeRange != TimeRange.ALL ||
                            state.selectedCategory !in listOf("推荐", "全部") ||
                            !state.includeVip

                    FilledTonalIconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (hasActiveFilter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "筛选",
                            modifier = Modifier.size(20.dp),
                            tint = if (hasActiveFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 2. 排序交互重构图标按钮与弹出菜单
                    Box {
                        FilledTonalIconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "排序",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            Text(
                                text = "排序维度",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            SortDimension.values().forEach { dim ->
                                DropdownMenuItem(
                                    text = { Text(dim.label) },
                                    trailingIcon = {
                                        if (state.sortDimension == dim) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setSortDimension(dim)
                                        showSortMenu = false
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = "排序方向",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            SortDirection.values().forEach { dir ->
                                DropdownMenuItem(
                                    text = { Text(dir.label) },
                                    trailingIcon = {
                                        if (state.sortDirection == dir) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setSortDirection(dir)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // 3. 快速分类标签
                    HomeViewModel.AVAILABLE_CATEGORIES.forEach { category ->
                        FilterChip(
                            selected = state.selectedCategory == category && state.selectedTag.isEmpty() && state.searchQuery.isEmpty(),
                            onClick = { viewModel.selectCategory(category) },
                            label = { Text(category) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            if (displayedVideos.isEmpty() && !state.isLoading && !state.isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = state.errorMessage ?: if (state.searchQuery.isNotEmpty()) "未找到相关视频" else "暂无符合筛选条件的视频",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.errorMessage != null) {
                            Button(onClick = { viewModel.refresh() }) {
                                Text("重试")
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedVideos, key = { it.id }) { video ->
                        VideoCard(
                            video = video,
                            showVipBadge = video.isVipEarlyAccess,
                            onClick = { onNavigateToDetail(video.id) }
                        )
                    }

                    if (state.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            if (pullToRefreshState.verticalOffset > 0f || pullToRefreshState.isRefreshing) {
                PullToRefreshContainer(
                    state = pullToRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // 扩充筛选弹窗 / 底部抽屉
    if (showFilterSheet) {
        var tempCategory by remember { mutableStateOf(state.selectedCategory) }
        var tempTag by remember { mutableStateOf(state.selectedTag) }
        var tempTimeRange by remember { mutableStateOf(state.selectedTimeRange) }
        var tempIncludeVip by remember(state.includeVip) { mutableStateOf(state.includeVip) }

        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "分类",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "标签",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = tempTag.isEmpty(),
                            onClick = { tempTag = "" },
                            label = { Text("全部") }
                        )
                        HomeViewModel.POPULAR_TAGS.forEach { tag ->
                            FilterChip(
                                selected = tempTag == tag,
                                onClick = { tempTag = if (tempTag == tag) "" else tag },
                                label = { Text(tag) }
                            )
                        }
                    }
                }

                // 3. 时间范围维度
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "时间范围",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "包含",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = tempIncludeVip,
                            onClick = { tempIncludeVip = !tempIncludeVip },
                            label = { Text("VIP抢先看视频") },
                            leadingIcon = {
                                if (tempIncludeVip) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        viewModel.applyFilters(tempCategory, tempTag, tempTimeRange, tempIncludeVip)
                        showFilterSheet = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("确定应用筛选", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun matchesTimeRange(video: CosVideoItem, timeRange: TimeRange): Boolean {
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

    // 若无法解析到有效时间信息且用户设置了限定时间范围，则排除
    return false
}

private fun sortVideos(
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
