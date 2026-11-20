package moe.zako.viewer.core.cdn

import moe.zako.viewer.core.logger.NeoLog
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
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
        NeoLog.i(TAG, "Starting concurrent CDN speed test on ${urls.size} nodes")
        
        val deferreds = urls.mapIndexed { idx, url ->
            async {
                val node = testSingleNode(idx, url)
                onNodeTested?.invoke(node)
                node
            }
        }

        val results = deferreds.awaitAll()
        // Sort: successful nodes first by speed descending, then latency ascending
        results.sortedWith(
            compareByDescending<CdnNode> { it.testSuccess }
                .thenByDescending { it.speedBps }
                .thenBy { it.latencyMs }
        )
    }

    /**
     * Measures latency (TTFB) and throughput on a single CDN node
     */
    suspend fun testSingleNode(index: Int, url: String): CdnNode = withContext(Dispatchers.IO) {
        val nodeName = "线路 ${index + 1}"
        var latency = -1L
        var speedBps = -1L
        var success = false

        try {
            val startConnect = System.currentTimeMillis()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .header("Range", "bytes=0-${TEST_CHUNK_BYTES - 1}")
                .build()

            val response = client.newCall(request).execute()
            val connectDone = System.currentTimeMillis()
            latency = connectDone - startConnect

            if (response.isSuccessful || response.code == 206) {
                val body = response.body
                if (body != null) {
                    var totalRead = 0L
                    val buffer = ByteArray(16384)
                    val stream = body.byteStream()
                    val readStart = System.currentTimeMillis()

                    while (totalRead < TEST_CHUNK_BYTES) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        totalRead += read
                        // Early break if it takes more than 2.5 seconds
                        if (System.currentTimeMillis() - readStart > 2500) break
                    }
                    val readDurationMs = (System.currentTimeMillis() - readStart).coerceAtLeast(1L)
                    speedBps = (totalRead * 1000L) / readDurationMs
                    success = true
                }
            }
            NeoLog.d(TAG, "$nodeName test done: latency=${latency}ms, speed=${speedBps / 1024}KB/s, success=$success")
        } catch (e: Exception) {
            NeoLog.w(TAG, "$nodeName test failed: ${e.message}")
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
