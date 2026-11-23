package moe.zako.viewer.data.source.cosav

import moe.zako.viewer.data.source.VideoDataSource

/**
 * 内置 CosAV 数据源插件实现
 */
class CosAvDataSource(
    private val client: CosApiClient = CosApiClient.instance
) : VideoDataSource {
    override val id: String = ID
    override val displayName: String = "CosAV"

    companion object {
        const val ID = "cosav"
    }

    override suspend fun getVideoRecommend(): List<CosVideoItem> = client.getVideoRecommend()

    override suspend fun getVideoLists(
        page: Int,
        order: String,
        keyword: String,
        tags: String,
        limit: Int
    ): CosVideoListResponse = client.getVideoLists(page, order, keyword, tags, limit)

    override suspend fun getVideoDetail(id: String): CosVideoItem? = client.getVideoDetail(id)

    override suspend fun searchVideos(keyword: String, page: Int): CosVideoListResponse =
        client.searchVideos(keyword, page)
}
