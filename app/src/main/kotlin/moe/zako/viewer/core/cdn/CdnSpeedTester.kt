package moe.zako.viewer.core.cdn

import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.network.NetworkClientProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.math.roundToInt

data class CdnNode(
    val index: Int,
    val name: String,
    val url: String,
    val latencyMs: Long = -1L,
    val speedBps: Long = -1L,
    val isTesting: Boolean = false,
    val testSuccess: Boolean = false
) {
    val displayLatency: String
        get() = if (latencyMs >= 0) "${latencyMs}ms" else "--"

    val displaySpeed: String
        get() = when {
            speedBps < 0 -> "--"
            speedBps >= 1024 * 1024 -> String.format("%.1f MB/s", speedBps.toDouble() / (1024 * 1024))
            speedBps >= 1024 -> "${(speedBps / 1024.0).roundToInt()} KB/s"
            else -> "$speedBps B/s"
        }

    val displayMetric: String
        get() = if (testSuccess) "$displayLatency | $displaySpeed" else if (isTesting) "测速中..." else "未测速"
}

class CdnSpeedTester(
    private val client: OkHttpClient = NetworkClientProvider.speedTestClient
) {
    companion object {
        private const val TAG = "CdnSpeedTester"
        private const val TEST_CHUNK_BYTES = 512 * 1024 // 512 KB sample
    }

    /**
     * Concurrently tests latency and throughput across all CDN node URLs
     */
    suspend fun testAllNodes(
        urls: List<String>,
        onNodeTested: ((CdnNode) -> Unit)? = null
    ): List<CdnNode> = withContext(Dispatchers.IO) {
        ZakoLog.i(TAG, "Starting concurrent CDN speed test on ${urls.size} nodes")
        
        try {
            val deferreds = urls.mapIndexed { idx, url ->
                async {
                    val node = testSingleNode(idx, url)
                    onNodeTested?.invoke(node)
                    node
                }
            }

            val results = deferreds.awaitAll()
            results.sortedWith(
                compareByDescending<CdnNode> { it.testSuccess }
                    .thenByDescending { it.speedBps }
                    .thenBy { it.latencyMs }
            )
        } catch (e: Exception) {
            if (e is CancellationException) {
                ZakoLog.d(TAG, "Concurrent CDN speed test cancelled")
                throw e
            }
            throw e
        }
    }

    /**
     * Measures latency (TTFB) and throughput on a single CDN node,
     * tightly bound to coroutine cancellation to immediately abort active network requests.
     */
    suspend fun testSingleNode(index: Int, url: String): CdnNode = withContext(Dispatchers.IO) {
        val nodeName = "线路 ${index + 1}"
        var latency = -1L
        var speedBps = -1L
        var success = false

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            .header("Range", "bytes=0-${TEST_CHUNK_BYTES - 1}")
            .build()
        val call = client.newCall(request)

        try {
            val startConnect = System.currentTimeMillis()

            // 绑定协程取消：一旦外层 Job 取消，立即主动调用 call.cancel() 瞬间掐断底层连接
            val response: Response = suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation {
                    call.cancel()
                }
                call.enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        cont.resume(response) {
                            response.close()
                        }
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) {
                            cont.resumeWith(Result.failure(e))
                        }
                    }
                })
            }

            val connectDone = System.currentTimeMillis()
            latency = connectDone - startConnect

            if (response.isSuccessful || response.code == 206) {
                response.body?.use { body ->
                    var totalRead = 0L
                    val buffer = ByteArray(16384)
                    val stream = body.byteStream()
                    val readStart = System.currentTimeMillis()

                    while (totalRead < TEST_CHUNK_BYTES && isActive) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        totalRead += read
                        // Early break if it takes more than 2.5 seconds
                        if (System.currentTimeMillis() - readStart > 2500) break
                    }

                    if (!isActive) {
                        call.cancel()
                        throw CancellationException("CDN test cancelled during stream read")
                    }

                    val readDurationMs = (System.currentTimeMillis() - readStart).coerceAtLeast(1L)
                    speedBps = (totalRead * 1000L) / readDurationMs
                    success = true
                }
            }
            ZakoLog.d(TAG, "$nodeName test done: latency=${latency}ms, speed=${speedBps / 1024}KB/s, success=$success")
        } catch (e: Exception) {
            call.cancel()
            if (e is CancellationException) {
                ZakoLog.d(TAG, "$nodeName speed test cancelled")
                throw e
            }
            ZakoLog.w(TAG, "$nodeName test failed: ${e.message}")
            success = false
        }

        CdnNode(
            index = index,
            name = nodeName,
            url = url,
            latencyMs = latency,
            speedBps = speedBps,
            isTesting = false,
            testSuccess = success
        )
    }
}
