package moe.zako.viewer.data.source.cosav

import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

class LenientCommentListDeserializer : JsonDeserializer<List<CosComment>> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): List<CosComment> {
        if (json == null || !json.isJsonArray) {
            return emptyList()
        }
        val list = mutableListOf<CosComment>()
        for (elem in json.asJsonArray) {
            if (elem.isJsonObject && context != null) {
                try {
                    val comment: CosComment = context.deserialize(elem, CosComment::class.java)
                    list.add(comment)
                } catch (_: Exception) {}
            }
        }
        return list
    }
}

class LenientVideoItemListDeserializer : JsonDeserializer<List<CosVideoItem>> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): List<CosVideoItem> {
        if (json == null || !json.isJsonArray) {
            return emptyList()
        }
        val list = mutableListOf<CosVideoItem>()
        for (elem in json.asJsonArray) {
            if (elem.isJsonObject && context != null) {
                try {
                    val item: CosVideoItem = context.deserialize(elem, CosVideoItem::class.java)
                    list.add(item)
                } catch (_: Exception) {}
            }
        }
        return list
    }
}

class LenientStringListDeserializer : JsonDeserializer<List<String>> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): List<String> {
        if (json == null || !json.isJsonArray) {
            return emptyList()
        }
        val list = mutableListOf<String>()
        for (elem in json.asJsonArray) {
            if (elem.isJsonPrimitive) {
                list.add(elem.asString)
            }
        }
        return list
    }
}

data class CosApiResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("data") val data: String? = null,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("message") val message: String? = null
)

data class CosVideoListResponse(
    @SerializedName("list") val list: List<CosVideoItem>? = null,
    @SerializedName("lists") val lists: List<CosVideoItem>? = null,
    @SerializedName("totalCnt") val totalCnt: Any? = null,
    @SerializedName("total") val total: Any? = null,
    @SerializedName("lastpage") val lastpage: Any? = null,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 20
) {
    val items: List<CosVideoItem>
        get() = list ?: lists ?: emptyList()

    val totalCount: Int
        get() = when (totalCnt) {
            is Number -> totalCnt.toInt()
            is String -> totalCnt.toIntOrNull() ?: items.size
            else -> when (total) {
                is Number -> total.toInt()
                is String -> total.toIntOrNull() ?: items.size
                else -> items.size
            }
        }
}

data class CosVideoItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("photo") val photo: String? = null,
    @SerializedName("video_img") val videoImg: String? = null,
    @SerializedName("img") val img: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("viewnumber") val viewnumber: String? = null,
    @SerializedName("hits") val hits: Any? = null,
    @SerializedName("views") val views: Any? = null,
    @SerializedName("adddate") val adddate: String? = null,
    @SerializedName("addtime") val addtime: String? = null,
    @SerializedName("addtimestamp") val addtimestamp: Any? = null,
    @SerializedName("barcode") val barcode: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("cos_role") val cosRole: String? = null,
    @SerializedName("cos_works") val cosWorks: String? = null,
    @SerializedName("company") val company: String? = null,
    @SerializedName("series") val series: String? = null,
    @SerializedName("channel") val channel: Any? = null,
    @SerializedName("channel_name") val channelName: String? = null,
    @SerializedName("channel_bg_color") val channelBgColor: String? = null,
    @SerializedName("tags") val tags: Any? = null, // String or List<String>
    @SerializedName("is_exclusive") val isExclusive: Any? = null, // Boolean, String or Int
    @SerializedName("is_vip") val isVip: Any? = null, // String, Int or Boolean
    @SerializedName("is_vip_limited_time") val isVipLimitedTime: Any? = null,
    @SerializedName("vip_limited_time_start") val vipLimitedTimeStart: Any? = null,
    @SerializedName("single_sale") val singleSale: Any? = null,
    @SerializedName("sale_point") val salePoint: Any? = null,
    @SerializedName("can_play") val canPlay: Any? = null,
    @SerializedName("can_play_status") val canPlayStatus: Any? = null,
    @SerializedName("can_play_msg") val canPlayMsg: String? = null,
    @SerializedName("preview_video") val previewVideo: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("likes") val likes: Any? = null,
    @SerializedName("dislikes") val dislikes: Any? = null,
    @SerializedName("like_rate") val likeRate: Any? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("user_avatar") val userAvatar: String? = null,
    @SerializedName("thank_vendor_text") val thankVendorText: String? = null,
    @SerializedName("thank_vendor_url") val thankVendorUrl: String? = null,
    @SerializedName("group_id") val groupId: Any? = null,
    @SerializedName("group_order") val groupOrder: Any? = null,
    @SerializedName("video_url")
    @JsonAdapter(LenientStringListDeserializer::class)
    val videoUrls: List<String>? = emptyList(),

    @SerializedName("video_url_vip")
    @JsonAdapter(LenientStringListDeserializer::class)
    val videoUrlsVip: List<String>? = emptyList(),

    @SerializedName("video_url_mp4")
    @JsonAdapter(LenientStringListDeserializer::class)
    val videoUrlsMp4: List<String>? = emptyList(),

    @SerializedName("video_url_mp4_vip")
    @JsonAdapter(LenientStringListDeserializer::class)
    val videoUrlsMp4Vip: List<String>? = emptyList(),

    @SerializedName("video_url_compat")
    @JsonAdapter(LenientStringListDeserializer::class)
    val videoUrlsCompat: List<String>? = emptyList(),

    @SerializedName("video_url_compat_vip")
    @JsonAdapter(LenientStringListDeserializer::class)
    val videoUrlsCompatVip: List<String>? = emptyList(),

    @SerializedName("comments")
    @JsonAdapter(LenientCommentListDeserializer::class)
    val comments: List<CosComment>? = emptyList(),

    @SerializedName("cnxh")
    @JsonAdapter(LenientVideoItemListDeserializer::class)
    val relatedVideos: List<CosVideoItem>? = emptyList()
) {

    val displayCover: String
        get() = photo?.takeIf { it.isNotEmpty() }
            ?: videoImg?.takeIf { it.isNotEmpty() }
            ?: img?.takeIf { it.isNotEmpty() }
            ?: cover ?: ""

    val isVipEarlyAccess: Boolean
        get() {
            val exclusive = when (isExclusive) {
                is Boolean -> isExclusive
                is Number -> isExclusive.toInt() == 1
                is String -> isExclusive == "1" || isExclusive.equals("true", ignoreCase = true)
                else -> false
            }
            val cantPlay = when (canPlay) {
                is Boolean -> !canPlay
                is String -> canPlay == "0" || canPlay.equals("false", ignoreCase = true)
                is Number -> canPlay.toInt() == 0
                else -> false
            }
            val hasUnlockMsg = canPlayMsg?.isNotEmpty() == true && (
                canPlayMsg.contains("解锁") || canPlayMsg.contains("VIP") || canPlayMsg.contains("天后")
            )
            // If in video detail view (where canPlay / canPlayMsg are provided by API)
            if (canPlay != null || canPlayMsg != null) {
                return (cantPlay && (hasUnlockMsg || canPlayStatus != null)) || (exclusive && cantPlay)
            }
            // In video list view, match original cosav behavior strictly based on is_exclusive
            return exclusive
        }

    val tagList: List<String>
        get() = when (tags) {
            is List<*> -> tags.filterIsInstance<String>()
            is String -> tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }

    val displayViews: Long
        get() {
            viewnumber?.toLongOrNull()?.let { return it }
            when (hits) {
                is Number -> return hits.toLong()
                is String -> hits.toLongOrNull()?.let { return it }
                else -> {}
            }
            when (views) {
                is Number -> return views.toLong()
                is String -> views.toLongOrNull()?.let { return it }
                else -> {}
            }
            return 0L
        }

    val formattedDuration: String
        get() {
            val dur = duration ?: return ""
            if (dur.contains(":")) return dur
            val totalSecs = dur.toDoubleOrNull()?.toInt() ?: return dur
            val hours = totalSecs / 3600
            val minutes = (totalSecs % 3600) / 60
            val seconds = totalSecs % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    /**
     * All available playback lines, with VIP nodes merged seamlessly
     */
    val allVideoLines: List<String>
        get() {
            val list = mutableListOf<String>()
            videoUrls?.let { list.addAll(it) }
            videoUrlsVip?.let {
                for (url in it) {
                    if (!list.contains(url)) list.add(url)
                }
            }
            videoUrlsCompat?.let {
                for (url in it) {
                    if (!list.contains(url)) list.add(url)
                }
            }
            videoUrlsCompatVip?.let {
                for (url in it) {
                    if (!list.contains(url)) list.add(url)
                }
            }
            return list
        }

    /**
     * All available MP4 lines for direct download / playback
     */
    val allMp4Lines: List<String>
        get() {
            val list = mutableListOf<String>()
            videoUrlsMp4?.let { list.addAll(it) }
            videoUrlsMp4Vip?.let {
                for (url in it) {
                    if (!list.contains(url)) list.add(url)
                }
            }
            return list
        }
}

data class CosComment(
    @SerializedName("CID") val cid: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("user_avatar") val userAvatar: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("comment") val comment: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("addtime") val addtime: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
) {
    val displayAuthor: String
        get() = username?.takeIf { it.isNotEmpty() } ?: nickname ?: "匿名用户"

    val displayAvatar: String
        get() = userAvatar?.takeIf { it.isNotEmpty() } ?: avatar ?: ""

    val displayContent: String
        get() = comment?.takeIf { it.isNotEmpty() } ?: content ?: ""
}
