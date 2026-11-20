package moe.zako.viewer.core.download

import moe.zako.viewer.core.logger.NeoLog
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class DownloadChunk(
    val chunkIndex: Int,
    val startByte: Long,
    val endByte: Long,
    var downloadedBytes: Long = 0,
    var assignedCdnUrl: String = ""
)

class MultiNodeDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "MultiNodeDownloader"
        private const val DEFAULT_CHUNKS_PER_NODE = 2
    }

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedFlags = ConcurrentHashMap<String, AtomicBoolean>()

    /**
     * Starts multi-node multi-threaded concurrent chunk download
     */
    suspend fun startDownload(
        videoId: String,
        title: String,
        cdnUrls: List<String>,
        outputFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBps: Long, activeNodes: Int) -> Unit,
        onComplete: (file: File) -> Unit,
        onError: (e: Throwable) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (cdnUrls.isEmpty()) {
            onError(IllegalArgumentException("No CDN URLs available"))
            return@withContext
        }

        val isPaused = AtomicBoolean(false)
        pausedFlags[videoId] = isPaused

        val job = launch {
            try {
                NeoLog.i(TAG, "Starting multi-node download for '$title' using ${cdnUrls.size} CDN nodes")
                
                // Step 1: Probe content length from the first working node
                var totalLength = -1L
                var workingUrl: String? = null
                for (url in cdnUrls) {
                    try {
                        val headReq = Request.Builder()
                            .url(url)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                            .header("Range", "bytes=0-0")
                            .build()
                        client.newCall(headReq).execute().use { resp ->
                            if (resp.isSuccessful || resp.code == 206) {
                                val contentRange = resp.header("Content-Range")
                                if (contentRange != null && contentRange.contains("/")) {
                                    totalLength = contentRange.substringAfter("/").toLongOrNull() ?: -1L
                                }
                                if (totalLength <= 0) {
                                    totalLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                                }
                                if (totalLength > 0) {
                                    workingUrl = url
                                    return@use
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    if (totalLength > 0) break
                }

                if (totalLength <= 0) {
                    // Fallback to single stream download if server does not provide range
                    NeoLog.w(TAG, "Server did not provide Content-Length; falling back to single stream")
                    downloadSingleStream(videoId, cdnUrls.first(), outputFile, onProgress, onComplete, onError)
                    return@launch
                }

                // Step 2: Allocate chunks across all available CDN nodes
                val numNodes = cdnUrls.size
                val totalChunks = (numNodes * DEFAULT_CHUNKS_PER_NODE).coerceAtLeast(4)
                val chunkSize = totalLength / totalChunks

                val chunks = mutableListOf<DownloadChunk>()
                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = if (i == totalChunks - 1) totalLength - 1 else (start + chunkSize - 1)
                    val assignedNodeUrl = cdnUrls[i % numNodes]
                    chunks.add(DownloadChunk(i, start, end, 0L, assignedNodeUrl))
                }

                // Pre-allocate destination file
                if (!outputFile.parentFile?.exists()!!) {
                    outputFile.parentFile?.mkdirs()
                }
                val raf = RandomAccessFile(outputFile, "rw")
                raf.setLength(totalLength)
                raf.close()

                val totalDownloaded = AtomicLong(0L)
                val speedTracker = AtomicLong(0L)
                var lastSpeedCheckTime = System.currentTimeMillis()
                var lastBytesCount = 0L

                // Progress reporter timer
                val progressJob = launch {
                    while (isActive && !isPaused.get()) {
                        delay(500)
                        val now = System.currentTimeMillis()
                        val duration = (now - lastSpeedCheckTime).coerceAtLeast(1L)
                        val currentDownloaded = totalDownloaded.get()
                        val bytesSinceLast = currentDownloaded - lastBytesCount
                        val speedBps = (bytesSinceLast * 1000L) / duration

                        lastSpeedCheckTime = now
                        lastBytesCount = currentDownloaded
                        onProgress(currentDownloaded, totalLength, speedBps, numNodes)

                        if (currentDownloaded >= totalLength) break
                    }
                }

                // Step 3: Dispatch concurrent download workers across EVERY CDN node
                val workerDeferreds = chunks.map { chunk ->
                    async(Dispatchers.IO) {
                        downloadChunkWithFallback(
                            chunk = chunk,
                            allCdnUrls = cdnUrls,
                            outputFile = outputFile,
                            isPaused = isPaused,
                            totalDownloaded = totalDownloaded
                        )
                    }
                }

                workerDeferreds.awaitAll()
                progressJob.cancel()

                if (isPaused.get()) {
                    NeoLog.i(TAG, "Download paused for '$title'")
                } else if (totalDownloaded.get() >= totalLength) {
                    NeoLog.i(TAG, "Download finished successfully: '$title' (${outputFile.length()} bytes)")
                    onProgress(totalLength, totalLength, 0L, numNodes)
                    onComplete(outputFile)
                } else {
                    onError(IllegalStateException("Incomplete download: got ${totalDownloaded.get()} of $totalLength bytes"))
                }
            } catch (e: Exception) {
                NeoLog.e(TAG, "Download error for '$title'", e)
                onError(e)
            } finally {
                activeJobs.remove(videoId)
                pausedFlags.remove(videoId)
            }
        }

        activeJobs[videoId] = job
    }

    private suspend fun downloadChunkWithFallback(
        chunk: DownloadChunk,
        allCdnUrls: List<String>,
        outputFile: File,
        isPaused: AtomicBoolean,
        totalDownloaded: AtomicLong
    ) = withContext(Dispatchers.IO) {
        var currentUrl = chunk.assignedCdnUrl
        var success = false
        var attempts = 0

        while (!success && attempts < allCdnUrls.size && !isPaused.get()) {
            try {
                val actualStart = chunk.startByte + chunk.downloadedBytes
                if (actualStart > chunk.endByte) {
                    success = true
                    break
                }

                val req = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    .header("Range", "bytes=$actualStart-${chunk.endByte}")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 206) {
                        val body = resp.body ?: return@use
                        val buffer = ByteArray(16384)
                        val inStream = body.byteStream()
                        
                        RandomAccessFile(outputFile, "rw").use { raf ->
                            raf.seek(actualStart)
                            while (!isPaused.get()) {
                                val bytesRead = inStream.read(buffer)
                                if (bytesRead == -1) break
                                raf.write(buffer, 0, bytesRead)
                                chunk.downloadedBytes += bytesRead
                                totalDownloaded.addAndGet(bytesRead.toLong())
                            }
                        }
                        if (chunk.startByte + chunk.downloadedBytes >= chunk.endByte + 1) {
                            success = true
                        }
                    }
                }
            } catch (e: Exception) {
                NeoLog.w(TAG, "Chunk ${chunk.chunkIndex} failed on $currentUrl, switching to mirror node...")
            }

            if (!success && !isPaused.get()) {
                attempts++
                // Failover to another CDN node in the pool
                currentUrl = allCdnUrls[attempts % allCdnUrls.size]
                delay(300)
            }
        }
    }

    private suspend fun downloadSingleStream(
        videoId: String,
        url: String,
        outputFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBps: Long, activeNodes: Int) -> Unit,
        onComplete: (file: File) -> Unit,
        onError: (e: Throwable) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val body = resp.body ?: throw IllegalStateException("Empty body")
                val totalLength = body.contentLength()
                val inStream = body.byteStream()
                val buffer = ByteArray(16384)

                outputFile.outputStream().use { out ->
                    var downloaded = 0L
                    var lastTime = System.currentTimeMillis()
                    var lastBytes = 0L

                    while (true) {
                        val read = inStream.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastTime >= 500) {
                            val speed = ((downloaded - lastBytes) * 1000L) / (now - lastTime)
                            lastTime = now
                            lastBytes = downloaded
                            onProgress(downloaded, totalLength, speed, 1)
                        }
                    }
                    onProgress(downloaded, totalLength, 0L, 1)
                    onComplete(outputFile)
                }
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun pauseDownload(videoId: String) {
        pausedFlags[videoId]?.set(true)
        activeJobs[videoId]?.cancel()
        activeJobs.remove(videoId)
        pausedFlags.remove(videoId)
        NeoLog.i(TAG, "Paused download job for video $videoId")
    }

    fun isDownloading(videoId: String): Boolean = activeJobs.containsKey(videoId)
}
