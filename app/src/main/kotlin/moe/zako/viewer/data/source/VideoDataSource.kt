package moe.zako.viewer.data.source

import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.data.source.cosav.CosVideoListResponse

/**
 * 统一视频数据源插件接口契约
 * 规范推荐流、列表检索、搜索及详情拉取的标准入口
 */
interface VideoDataSource {
    val id: String
    val displayName: String

    suspend fun getVideoRecommend(): List<CosVideoItem>

    suspend fun getVideoLists(
        page: Int = 1,
        order: String = "",
        keyword: String = "",
        tags: String = "",
        limit: Int = 20
    ): CosVideoListResponse

    suspend fun getVideoDetail(id: String): CosVideoItem?

    suspend fun searchVideos(keyword: String, page: Int = 1): CosVideoListResponse {
        return getVideoLists(page = page, order = "mv", keyword = keyword)
    }
}
