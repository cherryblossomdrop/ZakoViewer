package moe.zako.viewer.ui.screens.home

import moe.zako.viewer.ui.components.ZakoToast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.zako.viewer.core.search.SearchQueryParser
import moe.zako.viewer.core.share.ShareCipherUtil
import moe.zako.viewer.data.repository.LayoutMode
import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.ui.components.VideoCard
import moe.zako.viewer.ui.components.clearFocusOnScroll
import moe.zako.viewer.ui.components.clearFocusOnTap
import moe.zako.viewer.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetail: (videoId: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val staggeredGridState = rememberLazyStaggeredGridState()

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
            val visibleItems = staggeredGridState.layoutInfo.visibleItemsInfo
            val lastVisible = visibleItems.lastOrNull()?.index ?: 0
            // 避免冷启动首屏项目较少（如 10-12 项）且用户尚未开始滑动时，首屏渲染立刻误判触底发起重复请求
            val isNotAtTop = staggeredGridState.firstVisibleItemIndex > 0 || staggeredGridState.firstVisibleItemScrollOffset > 20
            totalItems > 0 && isNotAtTop && lastVisible >= totalItems - 4
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

    // 筛选与排序处理
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

    val hasActiveFilter = state.selectedTag.isNotEmpty() ||
            state.selectedTimeRange != TimeRange.ALL ||
            state.selectedCategory !in listOf("推荐", "全部") ||
            !state.includeVip

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // 1. 顶部搜索栏组件
                HomeSearchBar(
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                    onPerformSearch = performSearchOrCipher,
                    isSearchFocused = isSearchFocused,
                    onSearchFocusChanged = { isSearchFocused = it },
                    searchHistory = state.searchHistory,
                    isHistoryMultiSelect = state.isHistoryMultiSelect,
                    selectedHistoryQueries = state.selectedHistoryQueries,
                    onToggleSelectAllHistory = { viewModel.toggleSelectAllHistory() },
                    onDeleteSelectedHistory = { viewModel.deleteSelectedHistory() },
                    onClearAllHistory = { viewModel.clearAllHistory() },
                    onEnterHistoryMultiSelect = { query -> viewModel.enterHistoryMultiSelect(query) },
                    onExitHistoryMultiSelect = { viewModel.exitHistoryMultiSelect() },
                    onToggleHistorySelection = { query -> viewModel.toggleHistorySelection(query) },
                    onDeleteSearchHistoryItem = { query -> viewModel.deleteSearchHistoryItem(query) },
                    onHistoryItemClick = { query ->
                        viewModel.onSearchQueryChange(query)
                        viewModel.submitSearch()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. 筛选与排序控制条
                HomeFilterBar(
                    hasActiveFilter = hasActiveFilter,
                    onOpenFilterSheet = { showFilterSheet = true },
                    sortDimension = state.sortDimension,
                    sortDirection = state.sortDirection,
                    onSortDimensionChange = { viewModel.setSortDimension(it) },
                    onSortDirectionChange = { viewModel.setSortDirection(it) },
                    layoutMode = state.layoutMode,
                    onToggleLayoutMode = { viewModel.toggleLayoutMode() },
                    searchQuery = state.searchQuery,
                    selectedCategory = state.selectedCategory,
                    selectedTag = state.selectedTag,
                    onSelectCategory = { viewModel.selectCategory(it) },
                    onFilterSearchByTag = { viewModel.filterSearchByTag(it) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clearFocusOnScroll()
                .clearFocusOnTap()
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
                                    imageVector = AppIcons.ArrowBack,
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
                                    imageVector = AppIcons.ArrowForward,
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

    // 综合筛选底部抽屉
    if (showFilterSheet) {
        HomeFilterBottomSheet(
            initialCategory = state.selectedCategory,
            initialTag = state.selectedTag,
            initialTimeRange = state.selectedTimeRange,
            initialIncludeVip = state.includeVip,
            currentSearchQuery = state.searchQuery,
            onApplyFilters = { category, tag, timeRange, includeVip ->
                viewModel.applyFilters(category, tag, timeRange, includeVip)
            },
            onAppendTagToSearch = { tag ->
                viewModel.appendTagToSearch(tag)
            },
            onDismissRequest = { showFilterSheet = false }
        )
    }
}
