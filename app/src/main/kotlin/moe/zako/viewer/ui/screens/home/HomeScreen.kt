package moe.zako.viewer.ui.screens.home

import moe.zako.viewer.ui.components.ZakoToast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import moe.zako.viewer.ui.theme.BorderWidth
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.TouchTarget
import moe.zako.viewer.ui.theme.VideoCoverTokens

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
    var showJumpDialog by remember { mutableStateOf(false) }
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

    val hasActiveFilter = (state.selectedTag.isNotEmpty() && state.selectedTag != state.selectedCategory) ||
            state.selectedTimeRange != TimeRange.ALL

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.Lg, vertical = Spacing.Sm)
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

                Spacer(modifier = Modifier.height(Spacing.Sm))

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
                        verticalArrangement = Arrangement.spacedBy(Spacing.Md)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(TouchTarget.Dense),
                            strokeWidth = BorderWidth.HeavyStroke
                        )
                        Text(
                            text = "加载中…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (state.layoutMode == LayoutMode.PAGINATED) {
                // 分页排布模式：规整卡片网格展示当前页数据（或超界/无结果空状态）+ 底部翻页控制栏（绝不消失）
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = VideoCoverTokens.GridAdaptiveMinSize),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Intermediate),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Intermediate)
                ) {
                    if (displayedVideos.isEmpty() && !state.isLoading && !state.isRefreshing) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.Massive),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Spacing.Md)
                                ) {
                                    Text(
                                        text = state.errorMessage ?: if (state.searchQuery.isNotEmpty()) "未找到相关视频" else "暂无符合筛选条件的视频",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (state.errorMessage != null) {
                                        IconButton(onClick = { viewModel.refresh() }) {
                                            Icon(
                                                imageVector = AppIcons.Refresh,
                                                contentDescription = "重试",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
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
                                        .padding(Spacing.Lg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(IconSize.Lg))
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HomePaginationBar(
                            currentPage = state.page,
                            isLoading = state.isLoading,
                            hasMore = state.hasMore,
                            onPreviousPage = { viewModel.previousPage() },
                            onNextPage = { viewModel.nextPage() },
                            onOpenJumpDialog = { showJumpDialog = true }
                        )
                    }
                }
            } else if (displayedVideos.isEmpty() && !state.isLoading && !state.isRefreshing) {
                // 瀑布流模式下的空状态展示
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.Md)
                    ) {
                        Text(
                            text = state.errorMessage ?: if (state.searchQuery.isNotEmpty()) "未找到相关视频" else "暂无符合筛选条件的视频",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.errorMessage != null) {
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(
                                    imageVector = AppIcons.Refresh,
                                    contentDescription = "重试",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (state.layoutMode == LayoutMode.WATERFALL) {
                // 瀑布流模式：双列交错网格 + 触底自动加载下一页
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = VideoCoverTokens.GridAdaptiveMinSize),
                    state = staggeredGridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Intermediate),
                    verticalItemSpacing = Spacing.Intermediate
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
                                    .padding(Spacing.Lg),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(IconSize.Lg))
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

    // 分页跳转指定页弹窗
    if (showJumpDialog) {
        HomeJumpPageDialog(
            currentPage = state.page,
            totalPages = state.totalPages,
            onConfirm = { targetPage ->
                viewModel.jumpToPage(targetPage)
            },
            onDismiss = { showJumpDialog = false }
        )
    }
}

/**
 * 首页分页排布模式统一底部分页控制栏组件
 */
@Composable
private fun HomePaginationBar(
    currentPage: Int,
    isLoading: Boolean,
    hasMore: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenJumpDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPreviousPage,
            enabled = currentPage > 1 && !isLoading
        ) {
            Icon(
                imageVector = AppIcons.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(IconSize.Sm)
            )
            Spacer(modifier = Modifier.width(Spacing.Xs))
            Text("上一页")
        }

        Spacer(modifier = Modifier.width(Spacing.Lg))

        Surface(
            onClick = onOpenJumpDialog,
            shape = RoundedCornerShape(CornerRadius.Sm),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.Sm)
        ) {
            Text(
                text = "第 $currentPage 页",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Compact)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.Lg))

        Button(
            onClick = onNextPage,
            enabled = hasMore && !isLoading
        ) {
            Text("下一页")
            Spacer(modifier = Modifier.width(Spacing.Xs))
            Icon(
                imageVector = AppIcons.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(IconSize.Sm)
            )
        }
    }
}
