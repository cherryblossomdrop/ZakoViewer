package moe.zako.viewer.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.core.logger.NeoLog
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.data.source.cosav.CosApiClient
import moe.zako.viewer.data.source.cosav.CosVideoItem
import kotlinx.coroutines.flow.*
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
    val errorMessage: String? = null
)

class HomeViewModel(
    private val apiClient: CosApiClient = CosApiClient.instance,
    private val settingsRepository: SettingsRepository
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

    init {
        viewModelScope.launch {
            settingsRepository.includeVipFlow.collect { include ->
                _uiState.update { it.copy(includeVip = include) }
            }
        }
        loadVideos(refresh = true)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun submitSearch() {
        loadVideos(refresh = true)
    }

    fun selectCategory(category: String) {
        if (_uiState.value.selectedCategory != category || _uiState.value.searchQuery.isNotEmpty()) {
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
                    includeVip = includeVip,
                    searchQuery = ""
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
        loadVideos(refresh = true)
    }

    fun loadMore() {
        if (!_uiState.value.isLoading && _uiState.value.hasMore) {
            loadVideos(refresh = false)
        }
    }

    private fun loadVideos(refresh: Boolean) {
        viewModelScope.launch {
            val currentPage = if (refresh) 0 else _uiState.value.page + 1
            _uiState.update {
                it.copy(
                    isLoading = !refresh,
                    isRefreshing = refresh,
                    errorMessage = null
                )
            }

            try {
                val query = _uiState.value.searchQuery.trim()
                val orderParam = _uiState.value.sortDimension.apiOrder

                if (query.isNotEmpty()) {
                    val apiOrder = if (_uiState.value.selectedTimeRange != TimeRange.ALL) "mr" else orderParam
                    val response = apiClient.getVideoLists(
                        page = currentPage,
                        keyword = query,
                        order = apiOrder,
                        limit = 20
                    )
                    val newItems = response.items
                    _uiState.update { current ->
                        val combined = if (refresh) newItems else current.videos + newItems
                        current.copy(
                            videos = combined,
                            page = currentPage,
                            hasMore = newItems.isNotEmpty() && combined.size < response.totalCount,
                            isLoading = false,
                            isRefreshing = false
                        )
                    }
                } else {
                    val cat = _uiState.value.selectedCategory
                    val tag = _uiState.value.selectedTag
                    val timeRange = _uiState.value.selectedTimeRange

                    if (cat == "推荐" && tag.isEmpty() && timeRange == TimeRange.ALL) {
                        // 仅当推荐分类且无标签、无时间筛选时使用固定推荐流 /video/recommend
                        if (refresh || currentPage == 0) {
                            val recommendItems = apiClient.getVideoRecommend()
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
                    } else {
                        // 分类 / 标签 / 时间筛选 / 排序请求 -> 调用 /video/lists
                        val apiOrder = when {
                            cat == "热门" && timeRange == TimeRange.ALL -> "mv"
                            cat == "最新" -> "mr"
                            timeRange != TimeRange.ALL -> "mr" // 时间筛选必须从最新视频流拉取，才能包含本月/本周/今日的最新视频
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
                        val newItems = response.items
                        _uiState.update { current ->
                            val combined = if (refresh) newItems else current.videos + newItems
                            current.copy(
                                videos = combined,
                                page = currentPage,
                                hasMore = newItems.isNotEmpty() && combined.size < response.totalCount,
                                isLoading = false,
                                isRefreshing = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                NeoLog.e(TAG, "Failed to load videos", e)
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
