package moe.zako.viewer.data.source.cosav

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.network.NetworkClientProvider
import moe.zako.viewer.core.network.isHttpUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.UUID

class CosApiClient(
    private val client: OkHttpClient = NetworkClientProvider.apiClient,
    private val gson: Gson = GsonBuilder()
        .setLenient()
        .registerTypeAdapter(
            TypeToken.getParameterized(List::class.java, CosComment::class.java).type,
            LenientCommentListDeserializer()
        )
        .registerTypeAdapter(
            TypeToken.getParameterized(List::class.java, CosVideoItem::class.java).type,
            LenientVideoItemListDeserializer()
        )
        .registerTypeAdapter(
            TypeToken.getParameterized(List::class.java, String::class.java).type,
            LenientStringListDeserializer()
        )
        .create()
) {
    companion object {
        private const val TAG = "CosApiClient"
        private const val API_CLOUD_SERVER = "https://di4n3-c01.tos-cn-hongkong.bytepluses.com/cossvr2025.txt"
        private const val DEFAULT_API_URL = "https://api.cos-dkop.net/api/app/v1"

        val instance: CosApiClient by lazy { CosApiClient() }
    }

    private var resolvedBaseUrl: String? = null
    private val deviceId: String = UUID.randomUUID().toString().replace("-", "").take(32)

    private fun generateTokenParam(): String {
        val timestamp = System.currentTimeMillis() / 1000
        return "CosAppMakeBigMoney,$timestamp"
    }

    /**
     * Resolves API base URL from cloud config or defaults
     */
    suspend fun getBaseUrl(): String = withContext(Dispatchers.IO) {
        resolvedBaseUrl?.let { return@withContext it }
        try {
            ZakoLog.d(TAG, "Resolving API base URL from cloud server: $API_CLOUD_SERVER")
            val request = Request.Builder()
                .url(API_CLOUD_SERVER)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val encryptedText = response.body?.string()?.trim() ?: ""
                if (encryptedText.isNotEmpty()) {
                    val decryptedUrl = CosCrypto.decrypt(encryptedText).trim()
                    if (decryptedUrl.isHttpUrl()) {
                        ZakoLog.i(TAG, "Successfully resolved API URL from cloud: $decryptedUrl")
                        resolvedBaseUrl = decryptedUrl
                        return@withContext decryptedUrl
                    }
                }
            }
        } catch (e: Exception) {
            ZakoLog.w(TAG, "Failed to resolve API URL from cloud, using fallback: ${e.message}")
        }
        resolvedBaseUrl = DEFAULT_API_URL
        DEFAULT_API_URL
    }

    /**
     * Sends GET request to cosav API, decrypts payload, and returns parsed JSON string
     */
    private suspend fun executeGet(pathWithQuery: String): String = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val fullUrl = if (pathWithQuery.startsWith("http")) pathWithQuery else "$baseUrl$pathWithQuery"
        
        ZakoLog.d(TAG, "GET: $fullUrl")
        val request = Request.Builder()
            .url(fullUrl)
            .header("Content-Type", "application/json")
            .header("Tokenparam", generateTokenParam())
            .header("userParams", "")
            .header("X-Device-Id", deviceId)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/120.0.0.0")
            .build()

        val call = client.newCall(request)
        val response = try {
            call.execute()
        } catch (e: Exception) {
            if (e is CancellationException) {
                call.cancel()
                throw e
            }
            throw e
        }
        ZakoLog.d(TAG, "Response code: ${response.code} for $fullUrl")
        if (!response.isSuccessful) {
            throw IllegalStateException("API error: HTTP ${response.code}")
        }

        val rawBody = response.body?.string() ?: throw IllegalStateException("Empty body")
        val apiResponse = gson.fromJson(rawBody, CosApiResponse::class.java)
        
        val encryptedData = apiResponse.data ?: return@withContext rawBody
        val decryptedData = CosCrypto.decrypt(encryptedData)
        if (decryptedData.isEmpty()) {
            ZakoLog.w(TAG, "Decrypted data was empty, fallback to raw")
            return@withContext rawBody
        }
        decryptedData
    }

    /**
     * Get video lists with pagination, sorting, keyword, or tag (mirroring original cosav Go(query, page, limit))
     * @param page 0-indexed page number (0 for first page, matching cosav API)
     * @param order "mv" (most views / 热门), "mr" (most recent / 最新), or empty
     * @param keyword search keyword (kw=)
     * @param tags video tag filter (tags=)
     */
    suspend fun getVideoLists(
        page: Int = 0,
        order: String = "",
        keyword: String = "",
        tags: String = "",
        limit: Int = 20
    ): CosVideoListResponse = withContext(Dispatchers.IO) {
        val params = mutableListOf<String>()
        if (keyword.isNotEmpty()) {
            params.add("kw=" + URLEncoder.encode(keyword, "UTF-8"))
        }
        if (tags.isNotEmpty()) {
            params.add("tags=" + URLEncoder.encode(tags, "UTF-8"))
        }
        if (order.isNotEmpty()) {
            params.add("order=$order")
        }
        params.add("page=$page")
        params.add("limit=$limit")

        val queryString = "/video/lists?" + params.joinToString("&")
        val json = executeGet(queryString)
        val parsed = gson.fromJson(json, CosVideoListResponse::class.java) ?: CosVideoListResponse()
        ZakoLog.i(TAG, "getVideoLists ($queryString) returned ${parsed.items.size} items (total: ${parsed.totalCount})")
        parsed
    }

    /**
     * Get recommendation video lists (mirroring original cosav /video/recommend)
     */
    suspend fun getVideoRecommend(): List<CosVideoItem> = withContext(Dispatchers.IO) {
        try {
            val json = executeGet("/video/recommend")
            val trimmed = json.trim()
            val list = if (trimmed.startsWith("[")) {
                val array = gson.fromJson(json, Array<CosVideoItem>::class.java)
                array?.toList() ?: emptyList()
            } else {
                val listResponse = gson.fromJson(json, CosVideoListResponse::class.java)
                listResponse?.items ?: emptyList()
            }
            ZakoLog.i(TAG, "getVideoRecommend returned ${list.size} items")
            list
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ZakoLog.e(TAG, "getVideoRecommend error", e)
            emptyList()
        }
    }

    /**
     * Get complete video detail by ID (including all CDN lines and comments)
     */
    suspend fun getVideoDetail(id: String): CosVideoItem? = withContext(Dispatchers.IO) {
        var json: String? = null
        try {
            json = executeGet("/video/videoinfo?id=$id")
            gson.fromJson(json, CosVideoItem::class.java)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ZakoLog.e(TAG, "Failed to parse video detail for ID $id. Raw response body:\n$json", e)
            null
        }
    }

    /**
     * Search videos by keyword
     */
    suspend fun searchVideos(keyword: String, page: Int = 1): CosVideoListResponse {
        return getVideoLists(page = page, order = "mv", keyword = keyword)
    }
}
