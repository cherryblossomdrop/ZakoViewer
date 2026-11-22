package moe.zako.viewer.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.search.SearchQueryParser
import moe.zako.viewer.data.repository.LayoutMode
import moe.zako.viewer.data.repository.SearchHistoryRepository
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.data.source.cosav.CosApiClient
import moe.zako.viewer.data.source.cosav.CosVideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SortDimension(val label: String, val apiOrder: String) {
    VIEWS("观看量", "mv"),
    PUBLISH_TIME("发布时间", "mr"),
    POPULARITY("热度", "order_rank")
}

enum class SortDirection(val label: String) {
    DESCENDING("倒序"),
    ASCENDING("正序")
}

enum class TimeRange(val label: String, val maxDays: Long?) {
    ALL("全部", null),
    TODAY("今日", 1),
    WEEK("本周", 7),
    MONTH("本月", 30),
    HALF_YEAR("半年内", 182),
    YEAR("一年内", 365)
}

data class HomeUiState(
    val videos: List<CosVideoItem> = emptyList(),
    val selectedCategory: String = "推荐",
    val selectedTag: String = "",
    val selectedTimeRange: TimeRange = TimeRange.ALL,
    val sortDimension: SortDimension = SortDimension.VIEWS,
    val sortDirection: SortDirection = SortDirection.DESCENDING,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val includeVip: Boolean = true, // 包含VIP抢先看视频 (默认包含)
    val page: Int = 0,
    val hasMore: Boolean = true,
    val totalPages: Int? = null,
    val errorMessage: String? = null,
    val layoutMode: LayoutMode = LayoutMode.WATERFALL,
    val searchHistory: List<String> = emptyList(),
    val isHistoryMultiSelect: Boolean = false,
    val selectedHistoryQueries: Set<String> = emptySet()
)

class HomeViewModel(
    private val apiClient: CosApiClient = CosApiClient.instance,
    private val settingsRepository: SettingsRepository,
    private val searchHistoryRepository: SearchHistoryRepository? = null
) : ViewModel() {
    companion object {
        private const val TAG = "HomeViewModel"

        val AVAILABLE_CATEGORIES = listOf(
            "推荐", "热门", "最新", "独家", "动漫", "国产", "同人", "剧情"
        )

        val POPULAR_TAGS = listOf(
            "独家", "Cosplay", "白丝", "黑丝", "制服", "护士", "旗袍", "学生", "萝莉", "剧情", "探花", "极品", "无套"
        )
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastLoadMoreTimestamp: Long = 0L

    init {
        viewModelScope.launch {
            settingsRepository.includeVipFlow.collect { include ->
                _uiState.update { it.copy(includeVip = include) }
            }
        }
        viewModelScope.launch {
            settingsRepository.layoutModeFlow.collect { mode ->
                val prevMode = _uiState.value.layoutMode
                _uiState.update { it.copy(layoutMode = mode) }
                if (prevMode != mode && _uiState.value.videos.isNotEmpty()) {
                    loadVideos(refresh = true, targetPage = 0)
                }
            }
        }
        searchHistoryRepository?.let { repo ->
            viewModelScope.launch {
                repo.searchHistoryFlow.collect { list ->
                    _uiState.update { it.copy(searchHistory = list.map { item -> item.query }) }
                }
            }
        }
        loadVideos(refresh = true)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun submitSearch() {
        val q = _uiState.value.searchQuery.trim()
        if (q.isNotEmpty()) {
            viewModelScope.launch {
                searchHistoryRepository?.saveSearchQuery(q)
            }
        }
        loadVideos(refresh = true)
    }

    // --- 搜索历史管理 ---
    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch {
            searchHistoryRepository?.deleteSearchQuery(query)
        }
    }

    fun enterHistoryMultiSelect(initialQuery: String? = null) {
        _uiState.update {
            it.copy(
                isHistoryMultiSelect = true,
                selectedHistoryQueries = if (initialQuery != null) setOf(initialQuery) else emptySet()
            )
        }
    }

    fun exitHistoryMultiSelect() {
        _uiState.update {
            it.copy(
                isHistoryMultiSelect = false,
                selectedHistoryQueries = emptySet()
            )
        }
    }

    fun toggleHistorySelection(query: String) {
        _uiState.update {
            val newSet = it.selectedHistoryQueries.toMutableSet()
            if (newSet.contains(query)) {
                newSet.remove(query)
            } else {
                newSet.add(query)
            }
            it.copy(selectedHistoryQueries = newSet)
        }
    }

    fun toggleSelectAllHistory() {
        _uiState.update {
            val allQueries = it.searchHistory.toSet()
            val allSelected = it.selectedHistoryQueries.containsAll(allQueries)
            it.copy(selectedHistoryQueries = if (allSelected) emptySet() else allQueries)
        }
    }

    fun deleteSelectedHistory() {
        val selected = _uiState.value.selectedHistoryQueries.toList()
        if (selected.isNotEmpty()) {
            viewModelScope.launch {
                searchHistoryRepository?.deleteSearchQueries(selected)
                exitHistoryMultiSelect()
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            searchHistoryRepository?.clearAll()
            exitHistoryMultiSelect()
        }
    }

    fun appendTagToSearch(tag: String) {
        val current = _uiState.value.searchQuery.trim()
        val newQuery = if (current.isEmpty()) {
            tag
        } else {
            val tokens = current.split(Regex("\\s+"))
            if (tokens.contains(tag) || tokens.contains("+$tag") || tokens.contains("-$tag")) {
                current
            } else {
                "$current $tag"
            }
        }
        _uiState.update { it.copy(searchQuery = newQuery, selectedCategory = "全部") }
        loadVideos(refresh = true)
    }

    fun toggleLayoutMode() {
        viewModelScope.launch {
            settingsRepository.toggleLayoutMode()
        }
    }

    fun nextPage() {
        if (_uiState.value.hasMore && !_uiState.value.isLoading) {
            loadVideos(refresh = false, targetPage = _uiState.value.page + 1)
        }
    }

    fun previousPage() {
        if (_uiState.value.page > 0 && !_uiState.value.isLoading) {
            loadVideos(refresh = false, targetPage = _uiState.value.page - 1)
        }
    }

    /**
     * 跳转到指定页（1-based 自然页码，如第 1 页传 1）
     * 复用当前激活的分类、排序与搜索筛选条件拉取目标页数据
     * @param page 目标页码（1-based 正整数）
     */
    fun jumpToPage(page: Int) {
        val targetZeroBased = (page - 1).coerceAtLeast(0)
        if (targetZeroBased == _uiState.value.page && _uiState.value.videos.isNotEmpty()) {
            return
        }
        if (!_uiState.value.isLoading) {
            loadVideos(refresh = false, targetPage = targetZeroBased)
        }
    }

    fun filterSearchByTag(tag: String) {
        val target = if (tag in listOf("全部", "推荐")) "" else tag
        val current = _uiState.value.selectedTag
        val newTag = if (current.equals(target, ignoreCase = true)) "" else target
        _uiState.update { it.copy(selectedTag = newTag) }
        loadVideos(refresh = true)
    }

    fun selectCategory(category: String) {
        if (_uiState.value.searchQuery.isNotEmpty()) {
            filterSearchByTag(category)
            return
        }
        if (_uiState.value.selectedCategory != category) {
            _uiState.update {
                it.copy(
                    selectedCategory = category,
                    selectedTag = if (category == "独家") "独家" else "",
                    searchQuery = ""
                )
            }
            loadVideos(refresh = true)
        }
    }

    fun applyFilters(category: String, tag: String, timeRange: TimeRange, includeVip: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIncludeVip(includeVip)
            _uiState.update {
                it.copy(
                    selectedCategory = category,
                    selectedTag = tag,
                    selectedTimeRange = timeRange,
                    includeVip = includeVip
                )
            }
            loadVideos(refresh = true)
        }
    }

    fun setSortDimension(dimension: SortDimension) {
        if (_uiState.value.sortDimension != dimension) {
            _uiState.update { it.copy(sortDimension = dimension) }
            loadVideos(refresh = true)
        }
    }

    fun setSortDirection(direction: SortDirection) {
        _uiState.update { it.copy(sortDirection = direction) }
    }

    fun toggleIncludeVip(include: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIncludeVip(include)
            _uiState.update { it.copy(includeVip = include) }
        }
    }

    fun refresh() {
        loadVideos(refresh = true, isPullRefresh = true)
    }

    fun loadMore() {
        val now = System.currentTimeMillis()
        if (now - lastLoadMoreTimestamp < 500L) return
        if (!_uiState.value.isLoading && !_uiState.value.isRefreshing && _uiState.value.hasMore && _uiState.value.layoutMode == LayoutMode.WATERFALL && _uiState.value.videos.isNotEmpty()) {
            lastLoadMoreTimestamp = now
            loadVideos(refresh = false)
        }
    }

    private fun loadVideos(refresh: Boolean, targetPage: Int? = null, isPullRefresh: Boolean = false) {
        if (!refresh && _uiState.value.isLoading) return

        // 切换标签/提交新搜索/初始加载时，立即取消上一次尚未完成的请求，避免并发竞态
        loadJob?.cancel()

        val isPaginated = _uiState.value.layoutMode == LayoutMode.PAGINATED
        val currentPage = targetPage ?: if (refresh) 0 else _uiState.value.page + 1

        _uiState.update { current ->
            current.copy(
                // 切换标签/新搜索时清空旧数据，以便触发居中加载动画并防止将旧标签结果误认为新标签数据
                videos = if (refresh && !isPullRefresh) emptyList() else current.videos,
                isLoading = !isPullRefresh,
                isRefreshing = isPullRefresh,
                errorMessage = null,
                page = if (refresh) 0 else current.page,
                totalPages = if (refresh) null else current.totalPages
            )
        }

        loadJob = viewModelScope.launch {
            try {
                val query = _uiState.value.searchQuery.trim()
                val orderParam = _uiState.value.sortDimension.apiOrder

                if (query.isNotEmpty()) {
                    val parsed = SearchQueryParser.parse(query)
                    val apiKw = parsed.mainKeyword.ifEmpty { query }
                    val apiOrder = if (_uiState.value.selectedTimeRange != TimeRange.ALL) "mr" else orderParam
                    val searchTag = _uiState.value.selectedTag
                    val isCompound = parsed.isCompound || searchTag.isNotEmpty()

                    fun filterItem(item: CosVideoItem): Boolean {
                        if (!parsed.matches(item)) return false
                        if (searchTag.isNotEmpty()) {
                            val searchable = "${item.title} ${item.barcode ?: ""} ${item.tagList.joinToString(" ")} ${item.channelName ?: ""} ${item.company ?: ""} ${item.series ?: ""} ${item.cosRole ?: ""} ${item.cosWorks ?: ""}"
                            if (!searchable.contains(searchTag, ignoreCase = true)) return false
                        }
                        return true
                    }

                    if (isPaginated) {
                        // Paginated mode: request specific page
                        val response = apiClient.getVideoLists(
                            page = currentPage,
                            keyword = apiKw,
                            tags = searchTag,
                            order = apiOrder,
                            limit = 20
                        )
                        if (!isActive) return@launch
                        val filtered = response.items.filter { filterItem(it) }
                        _uiState.update { current ->
                            current.copy(
                                videos = filtered,
                                page = currentPage,
                                hasMore = response.items.isNotEmpty() && (currentPage + 1) * 20 < response.totalCount,
                                totalPages = if (response.totalCount > 0) ((response.totalCount + 19) / 20).coerceAtLeast(1) else null,
                                isLoading = false,
                                isRefreshing = false
                            )
                        }
                    } else {
                        // Waterfall mode: multi-page prefetch on initial refresh for compound/tag filtering
                        if (refresh && isCompound) {
                            val collected = mutableListOf<CosVideoItem>()
                            var totalCount = 0
                            for (p in 0..2) {
                                if (!isActive) return@launch
                                val resp = apiClient.getVideoLists(
                                    page = p,
                                    keyword = apiKw,
                                    tags = searchTag,
                                    order = apiOrder,
                                    limit = 20
                                )
                                totalCount = resp.totalCount
                                collected.addAll(resp.items)
                                if (resp.items.size < 20 || collected.size >= totalCount) break
                            }
                            if (!isActive) return@launch
                            val filtered = collected.distinctBy { it.id }.filter { filterItem(it) }
                            _uiState.update { current ->
                                current.copy(
                                    videos = filtered,
                                    page = 2,
                                    hasMore = collected.size < totalCount,
                                    isLoading = false,
                                    isRefreshing = false
                                )
                            }
                        } else {
                            val response = apiClient.getVideoLists(
                                page = currentPage,
                                keyword = apiKw,
                                tags = searchTag,
                                order = apiOrder,
                                limit = 20
                            )
                            if (!isActive) return@launch
                            val filtered = response.items.filter { filterItem(it) }
                            _uiState.update { current ->
                                val combined = if (refresh) filtered else (current.videos + filtered).distinctBy { it.id }
                                current.copy(
                                    videos = combined,
                                    page = currentPage,
                                    hasMore = response.items.isNotEmpty() && combined.size < response.totalCount,
                                    totalPages = if (response.totalCount > 0) ((response.totalCount + 19) / 20).coerceAtLeast(1) else null,
                                    isLoading = false,
                                    isRefreshing = false
                                )
                            }
                        }
                    }
                } else {
                    val cat = _uiState.value.selectedCategory
                    val tag = _uiState.value.selectedTag
                    val timeRange = _uiState.value.selectedTimeRange

                    if (cat == "推荐" && tag.isEmpty() && timeRange == TimeRange.ALL) {
                        if (isPaginated) {
                            val response = if (currentPage == 0) null else apiClient.getVideoLists(page = currentPage, order = "mv", limit = 20)
                            val items = if (currentPage == 0) {
                                apiClient.getVideoRecommend()
                            } else {
                                response?.items ?: emptyList()
                            }
                            if (!isActive) return@launch
                            _uiState.update { current ->
                                current.copy(
                                    videos = items,
                                    page = currentPage,
                                    hasMore = items.isNotEmpty(),
                                    totalPages = response?.let { if (it.totalCount > 0) ((it.totalCount + 19) / 20).coerceAtLeast(1) else null },
                                    isLoading = false,
                                    isRefreshing = false
                                )
                            }
                        } else {
                            // Fixed / continuous recommendation flow for Waterfall
                            if (refresh || currentPage == 0) {
                                val recommendItems = apiClient.getVideoRecommend()
                                if (!isActive) return@launch
                                _uiState.update { current ->
                                    current.copy(
                                        videos = recommendItems,
                                        page = 0,
                                        hasMore = true,
                                        isLoading = false,
                                        isRefreshing = false
                                    )
                                }
                            } else {
                                val moreRecs = apiClient.getVideoRecommend()
                                if (!isActive) return@launch
                                _uiState.update { current ->
                                    val existingIds = current.videos.map { it.id }.toSet()
                                    val filteredNew = moreRecs.filter { it.id !in existingIds }
                                    val combined = current.videos + filteredNew
                                    current.copy(
                                        videos = combined,
                                        page = currentPage,
                                        hasMore = true,
                                        isLoading = false,
                                        isRefreshing = false
                                    )
                                }
                            }
                        }
                    } else {
                        // Category / Tag / Time filter
                        val apiOrder = when {
                            cat == "热门" && timeRange == TimeRange.ALL -> "mv"
                            cat == "最新" -> "mr"
                            timeRange != TimeRange.ALL -> "mr"
                            else -> orderParam
                        }
                        val apiTags = when {
                            tag.isNotEmpty() -> tag
                            cat == "独家" -> "独家"
                            cat in listOf("动漫", "国产", "同人", "剧情") -> cat
                            else -> ""
                        }

                        val response = apiClient.getVideoLists(
                            page = currentPage,
                            order = apiOrder,
                            tags = apiTags,
                            limit = 20
                        )
                        if (!isActive) return@launch
                        val newItems = response.items
                        _uiState.update { current ->
                            val combined = if (refresh || isPaginated) newItems else (current.videos + newItems).distinctBy { it.id }
                            current.copy(
                                videos = combined,
                                page = currentPage,
                                hasMore = newItems.isNotEmpty() && (if (isPaginated) (currentPage + 1) * 20 < response.totalCount else combined.size < response.totalCount),
                                totalPages = if (response.totalCount > 0) ((response.totalCount + 19) / 20).coerceAtLeast(1) else null,
                                isLoading = false,
                                isRefreshing = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    return@launch
                }
                ZakoLog.e(TAG, "Failed to load videos", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = "网络连接失败: ${e.message}"
                    )
                }
            }
        }
    }
}
