package moe.zako.viewer.ui.screens.home

import moe.zako.viewer.ui.components.ZakoToast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.zako.viewer.core.search.SearchQueryParser
import moe.zako.viewer.core.share.ShareCipherUtil
import moe.zako.viewer.data.repository.LayoutMode
import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.ui.components.VideoCard
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetail: (videoId: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val staggeredGridState = rememberLazyStaggeredGridState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }

    // Scroll to top when page changes in Paginated mode
    LaunchedEffect(state.page, state.layoutMode) {
        if (state.layoutMode == LayoutMode.PAGINATED) {
            try {
                gridState.scrollToItem(0)
            } catch (_: Throwable) {}
        }
    }

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
            isSearchFocused = false
            viewModel.submitSearch()
        }
    }

    // Trigger infinite scroll load more when reaching end (only in Waterfall mode)
    val shouldLoadMore = remember {
        derivedStateOf {
            if (state.layoutMode != LayoutMode.WATERFALL) return@derivedStateOf false
            val totalItems = staggeredGridState.layoutInfo.totalItemsCount
            val lastVisible = staggeredGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMore()
        }
    }

    // 复合搜索词法解析
    val parsedQuery = remember(state.searchQuery) {
        SearchQueryParser.parse(state.searchQuery)
    }

    // 筛选与排序处理：
    // 1. 包含VIP抢先看视频：开启状态包含 VIP 视频，关闭状态隐藏 VIP 视频
    // 2. 复合搜索语法过滤 (AND, NOT, OR)
    // 3. 时间范围过滤
    // 4. 排序维度与正序/倒序应用
    val displayedVideos = remember(
        state.videos,
        state.includeVip,
        state.selectedTimeRange,
        state.sortDimension,
        state.sortDirection,
        state.searchQuery,
        state.selectedTag
    ) {
        var list = if (state.includeVip) {
            state.videos
        } else {
            state.videos.filter { !it.isVipEarlyAccess }
        }

        if (state.searchQuery.isNotEmpty()) {
            list = list.filter { parsedQuery.matches(it) }
        }

        if (state.selectedTag.isNotEmpty()) {
            list = list.filter { video ->
                val searchable = "${video.title} ${video.barcode ?: ""} ${video.tagList.joinToString(" ")} ${video.channelName ?: ""} ${video.company ?: ""} ${video.series ?: ""} ${video.cosRole ?: ""} ${video.cosWorks ?: ""}"
                searchable.contains(state.selectedTag, ignoreCase = true)
            }
        }

        if (state.selectedTimeRange != TimeRange.ALL) {
            list = list.filter { matchesTimeRange(it, state.selectedTimeRange) }
        }

        sortVideos(list, state.sortDimension, state.sortDirection).distinctBy { it.id }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            if (displayedVideos.isNotEmpty()) {
                ZakoToast.show(msg)
            }
        }
    }

    // 自动补全加载：当客户端过滤导致当前屏暂无匹配项但服务端仍有更多数据时，自动尝试加载下一页（最多连续探测5页）
    LaunchedEffect(displayedVideos.isEmpty(), state.isLoading, state.hasMore, state.videos.size, state.layoutMode) {
        if (state.layoutMode == LayoutMode.WATERFALL && displayedVideos.isEmpty() && state.hasMore && !state.isLoading && !state.isRefreshing && state.videos.isNotEmpty() && state.page < 5) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // In-page search bar with cipher detection (占位文本已按需求移除)
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isSearchFocused = it.isFocused },
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

                // 搜索历史面板（当点击搜索框获得焦点时展开，支持单选与多选批量删除）
                AnimatedVisibility(visible = isSearchFocused && state.searchHistory.isNotEmpty()) {
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
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (state.isHistoryMultiSelect) "选择历史 (${state.selectedHistoryQueries.size})" else "搜索历史",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (state.isHistoryMultiSelect) {
                                        val allSelected = state.searchHistory.isNotEmpty() && state.selectedHistoryQueries.containsAll(state.searchHistory)
                                        TextButton(
                                            onClick = { viewModel.toggleSelectAllHistory() },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(if (allSelected) "取消全选" else "全选", fontSize = 12.sp)
                                        }
                                        TextButton(
                                            onClick = { viewModel.deleteSelectedHistory() },
                                            enabled = state.selectedHistoryQueries.isNotEmpty(),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "删除(${state.selectedHistoryQueries.size})",
                                                fontSize = 12.sp,
                                                color = if (state.selectedHistoryQueries.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            )
                                        }
                                        TextButton(
                                            onClick = { viewModel.clearAllHistory() },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("清空全部", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                        TextButton(
                                            onClick = { viewModel.exitHistoryMultiSelect() },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("完成", fontSize = 12.sp)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = { viewModel.enterHistoryMultiSelect() },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("多选/管理", fontSize = 12.sp)
                                        }
                                        IconButton(
                                            onClick = {
                                                focusManager.clearFocus()
                                                isSearchFocused = false
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ExpandLess, contentDescription = "收起", modifier = Modifier.size(18.dp))
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
                                state.searchHistory.forEach { historyQuery ->
                                    val isSelected = state.selectedHistoryQueries.contains(historyQuery)

                                    if (state.isHistoryMultiSelect) {
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.toggleHistorySelection(historyQuery) },
                                            label = { Text(historyQuery, maxLines = 1) },
                                            leadingIcon = {
                                                if (isSelected) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        )
                                    } else {
                                        InputChip(
                                            selected = false,
                                            onClick = {
                                                viewModel.onSearchQueryChange(historyQuery)
                                                focusManager.clearFocus()
                                                isSearchFocused = false
                                                viewModel.submitSearch()
                                            },
                                            label = {
                                                Text(
                                                    text = historyQuery,
                                                    maxLines = 1,
                                                    modifier = Modifier.combinedClickable(
                                                        onClick = {
                                                            viewModel.onSearchQueryChange(historyQuery)
                                                            focusManager.clearFocus()
                                                            isSearchFocused = false
                                                            viewModel.submitSearch()
                                                        },
                                                        onLongClick = {
                                                            viewModel.enterHistoryMultiSelect(historyQuery)
                                                        }
                                                    )
                                                )
                                            },
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = { viewModel.deleteSearchHistoryItem(historyQuery) },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
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

                    // 3. 布局模式切换按钮 (瀑布流 / 分页排布)
                    FilledTonalIconButton(
                        onClick = { viewModel.toggleLayoutMode() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (state.layoutMode == LayoutMode.WATERFALL) Icons.Default.GridView else Icons.Default.ViewAgenda,
                            contentDescription = if (state.layoutMode == LayoutMode.WATERFALL) "切换为分页排布" else "切换为瀑布流",
                            modifier = Modifier.size(20.dp)
                        )
                    }

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
                        val isSelected = if (state.searchQuery.isNotEmpty()) {
                            if (chipTag in listOf("全部", "推荐")) {
                                state.selectedTag.isEmpty()
                            } else {
                                state.selectedTag.equals(chipTag, ignoreCase = true)
                            }
                        } else {
                            state.selectedCategory == chipTag && state.selectedTag.isEmpty()
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (state.searchQuery.isNotEmpty()) {
                                    viewModel.filterSearchByTag(chipTag)
                                } else {
                                    viewModel.selectCategory(chipTag)
                                }
                            },
                            label = { Text(chipTag) }
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
            if (state.isLoading && displayedVideos.isEmpty()) {
                // 切换标签/提交新搜索时展示居中加载中动画，提供清晰明确的过渡感知
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "加载中…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (displayedVideos.isEmpty() && !state.isLoading && !state.isRefreshing) {
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
            } else if (state.layoutMode == LayoutMode.WATERFALL) {
                // 瀑布流模式：双列交错网格 + 触底自动加载下一页
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
                    state = staggeredGridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp
                ) {
                    items(displayedVideos, key = { it.id }) { video ->
                        VideoCard(
                            video = video,
                            showVipBadge = video.isVipEarlyAccess,
                            onClick = { onNavigateToDetail(video.id) }
                        )
                    }

                    if (state.isLoading) {
                        item(span = StaggeredGridItemSpan.FullLine) {
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
            } else {
                // 分页排布模式：规整卡片网格展示当前页数据 + 底部翻页控制栏
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
                        item(span = { GridItemSpan(maxLineSpan) }) {
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

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.previousPage() },
                                enabled = state.page > 0 && !state.isLoading
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("上一页")
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = "第 ${state.page + 1} 页",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Button(
                                onClick = { viewModel.nextPage() },
                                enabled = state.hasMore && !state.isLoading
                            ) {
                                Text("下一页")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
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
                                selected = state.searchQuery.contains(tag) || tempTag == tag,
                                onClick = {
                                    viewModel.appendTagToSearch(tag)
                                    showFilterSheet = false
                                },
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
