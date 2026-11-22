package moe.zako.viewer.core.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * 全局共享的 OkHttpClient 权威提供者与网络工具底座。
 * 集中维护单一底层连接池 (ConnectionPool) 与分发器 (Dispatcher)，
 * 各模块通过 newBuilder() 派生自定义超时客户端，杜绝多套连接池资源浪费。
 */
object NetworkClientProvider {

    private const val DEFAULT_CONNECT_TIMEOUT_SEC = 15L
    private const val DEFAULT_READ_TIMEOUT_SEC = 15L

    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/120.0.0.0"
    const val REFERER_COS = "https://api.cos-dkop.net/"

    /**
     * 全局共享的根 OkHttpClient 单例。
     * 维护全局唯一的 ConnectionPool，复用底层 TCP/TLS 链路连接。
     */
    val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 用于远端 API 调用的客户端（20s 读取超时）。
     */
    val apiClient: OkHttpClient by lazy {
        sharedClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 用于 Coil 封面与图像异步加载的客户端（注入统一 Header）。
     */
    val imageClient: OkHttpClient by lazy {
        sharedClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER_COS)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    /**
     * 用于 CDN 节点测速的高敏感客户端（短超时以快速检出高延迟线路）。
     */
    val speedTestClient: OkHttpClient by lazy {
        sharedClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 用于多节点离线分片下载的客户端。
     */
    val downloadClient: OkHttpClient by lazy {
        sharedClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 用于本地微型流媒体代理网关的客户端。
     */
    val streamClient: OkHttpClient by lazy {
        sharedClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 用于封面缓存管理器按需下载的客户端（长读取超时）。
     */
    val coverCacheClient: OkHttpClient by lazy {
        sharedClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * 权威 URL 有效性校验扩展方法，全局收敛判断散落。
 */
@OptIn(ExperimentalContracts::class)
fun String?.isHttpUrl(): Boolean {
    contract {
        returns(true) implies (this@isHttpUrl != null)
    }
    if (this.isNullOrBlank()) return false
    return startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
}
