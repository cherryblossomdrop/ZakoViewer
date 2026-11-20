package moe.zako.viewer.core.download

import moe.zako.viewer.core.logger.ZakoLog
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "MultiNodeDownloader"
        private const val DEFAULT_CHUNKS_PER_NODE = 2
    }

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedFlags = ConcurrentHashMap<String, AtomicBoolean>()

    private fun readMeta(metaFile: File, expectedTotalLength: Long): List<DownloadChunk>? {
        if (!metaFile.exists() || metaFile.length() == 0L) return null
        return try {
            val lines = metaFile.readLines()
            if (lines.isEmpty()) return null
            val header = lines.first().split(":")
            if (header.size < 3 || header[0] != "TOTAL") return null
            val total = header[1].toLongOrNull() ?: return null
            if (total != expectedTotalLength) return null

            val chunks = mutableListOf<DownloadChunk>()
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue
                val parts = line.split(":")
                if (parts.size >= 4) {
                    val idx = parts[0].toIntOrNull() ?: continue
                    val start = parts[1].toLongOrNull() ?: continue
                    val end = parts[2].toLongOrNull() ?: continue
                    val downloaded = parts[3].toLongOrNull() ?: 0L
                    val url = if (parts.size >= 5) parts[4] else ""
                    chunks.add(DownloadChunk(idx, start, end, downloaded, url))
                }
            }
            if (chunks.isNotEmpty()) chunks else null
        } catch (e: Exception) {
            ZakoLog.w(TAG, "Failed to read meta file: ${e.message}")
            null
        }
    }

    private fun saveMeta(metaFile: File, totalLength: Long, chunks: List<DownloadChunk>) {
        val parent = metaFile.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
        val tmpFile = File(parent, "${metaFile.name}.tmp")
        try {
            tmpFile.bufferedWriter().use { writer ->
                writer.write("TOTAL:$totalLength:${chunks.size}\n")
                chunks.forEach { chunk ->
                    writer.write("${chunk.chunkIndex}:${chunk.startByte}:${chunk.endByte}:${chunk.downloadedBytes}:${chunk.assignedCdnUrl}\n")
                }
            }
            if (tmpFile.exists()) {
                if (metaFile.exists()) metaFile.delete()
                tmpFile.renameTo(metaFile)
            }
        } catch (e: Exception) {
            ZakoLog.w(TAG, "Failed to save meta: ${e.message}")
        }
    }

    /**
     * Starts multi-node multi-threaded concurrent chunk download with breakpoint resumption
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

        // Cancel previous job if any
        pauseDownload(videoId)

        val isPaused = AtomicBoolean(false)
        pausedFlags[videoId] = isPaused

        val job = launch {
            try {
                ZakoLog.i(TAG, "Starting multi-node download for '$title' using ${cdnUrls.size} CDN nodes")

                // Step 1: Probe content length from working node
                var totalLength = -1L
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
                                if (totalLength > 0) return@use
                            }
                        }
                    } catch (_: Exception) {}
                    if (totalLength > 0) break
                }

                val targetDir = outputFile.parentFile ?: File(".")
                if (!targetDir.exists()) targetDir.mkdirs()

                val partFile = File(targetDir, "${outputFile.name}.part")
                val metaFile = File(targetDir, "${outputFile.name}.meta")

                if (totalLength <= 0) {
                    ZakoLog.w(TAG, "Server did not provide Content-Length; falling back to single stream")
                    downloadSingleStream(videoId, cdnUrls.first(), partFile, outputFile, isPaused, onProgress, onComplete, onError)
                    return@launch
                }

                // Step 2: Check for existing checkpoint (.meta & .part)
                var chunks = readMeta(metaFile, totalLength)
                val numNodes = cdnUrls.size

                if (chunks == null || !partFile.exists() || partFile.length() != totalLength) {
                    // Create new chunk allocation
                    val totalChunks = (numNodes * DEFAULT_CHUNKS_PER_NODE).coerceAtLeast(4)
                    val chunkSize = totalLength / totalChunks
                    val newChunks = mutableListOf<DownloadChunk>()
                    for (i in 0 until totalChunks) {
                        val start = i * chunkSize
                        val end = if (i == totalChunks - 1) totalLength - 1 else (start + chunkSize - 1)
                        val assignedNodeUrl = cdnUrls[i % numNodes]
                        newChunks.add(DownloadChunk(i, start, end, 0L, assignedNodeUrl))
                    }
                    chunks = newChunks

                    // Pre-allocate .part file
                    RandomAccessFile(partFile, "rw").use { it.setLength(totalLength) }
                    saveMeta(metaFile, totalLength, chunks)
                    ZakoLog.i(TAG, "Initialized new download chunks ($totalChunks chunks, total $totalLength bytes)")
                } else {
                    // Resume existing chunks: update assigned CDN URLs if needed
                    chunks.forEachIndexed { i, chunk ->
                        if (chunk.assignedCdnUrl.isEmpty()) {
                            chunk.assignedCdnUrl = cdnUrls[i % numNodes]
                        }
                    }
                    val existingDownloaded = chunks.sumOf { it.downloadedBytes }
                    ZakoLog.i(TAG, "Resuming download from checkpoint: $existingDownloaded / $totalLength bytes (${(existingDownloaded * 100 / totalLength)}%)")
                }

                val totalDownloaded = AtomicLong(chunks.sumOf { it.downloadedBytes })
                var lastSpeedCheckTime = System.currentTimeMillis()
                var lastBytesCount = totalDownloaded.get()
                var ticks = 0

                // Progress & Checkpoint timer
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

                        ticks++
                        if (ticks % 2 == 0) {
                            // Checkpoint to disk every 1s
                            saveMeta(metaFile, totalLength, chunks)
                        }

                        if (currentDownloaded >= totalLength) break
                    }
                }

                // Step 3: Dispatch concurrent chunk workers
                val workerDeferreds = chunks.map { chunk ->
                    async(Dispatchers.IO) {
                        downloadChunkWithFallback(
                            chunk = chunk,
                            allCdnUrls = cdnUrls,
                            partFile = partFile,
                            isPaused = isPaused,
                            totalDownloaded = totalDownloaded
                        )
                    }
                }

                workerDeferreds.awaitAll()
                progressJob.cancel()

                // Save final state of chunks
                saveMeta(metaFile, totalLength, chunks)

                if (isPaused.get()) {
                    ZakoLog.i(TAG, "Download paused for '$title' at ${totalDownloaded.get()} / $totalLength bytes")
                } else if (totalDownloaded.get() >= totalLength) {
                    ZakoLog.i(TAG, "Download finished successfully: '$title' ($totalLength bytes)")
                    // Finalize: rename .part to output file and remove .meta
                    if (outputFile.exists()) outputFile.delete()
                    val renameSuccess = partFile.renameTo(outputFile)
                    if (!renameSuccess) {
                        partFile.copyTo(outputFile, overwrite = true)
                        partFile.delete()
                    }
                    metaFile.delete()

                    onProgress(totalLength, totalLength, 0L, numNodes)
                    onComplete(outputFile)
                } else {
                    val msg = "下载未完全完成 (已下载 ${totalDownloaded.get()} / $totalLength 字节)，可重试继续断点续传"
                    ZakoLog.w(TAG, msg)
                    onError(IllegalStateException(msg))
                }
            } catch (e: Exception) {
                ZakoLog.e(TAG, "Download error for '$title'", e)
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
        partFile: File,
        isPaused: AtomicBoolean,
        totalDownloaded: AtomicLong
    ) = withContext(Dispatchers.IO) {
        val expectedBytes = chunk.endByte - chunk.startByte + 1
        if (chunk.downloadedBytes >= expectedBytes) {
            return@withContext
        }

        var currentUrl = chunk.assignedCdnUrl.ifEmpty { allCdnUrls.first() }
        var success = false
        var attempts = 0
        val maxAttempts = (allCdnUrls.size * 5).coerceAtLeast(15)

        while (!success && attempts < maxAttempts && !isPaused.get()) {
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
                    val isPartialExpected = actualStart > 0L
                    val codeValid = if (isPartialExpected) resp.code == 206 else (resp.isSuccessful || resp.code == 206)

                    if (codeValid) {
                        val body = resp.body ?: return@use
                        val buffer = ByteArray(32768)
                        val inStream = body.byteStream()

                        RandomAccessFile(partFile, "rw").use { raf ->
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
                    } else {
                        ZakoLog.w(TAG, "Chunk ${chunk.chunkIndex} on $currentUrl returned code ${resp.code} for range request")
                    }
                }
            } catch (e: Exception) {
                ZakoLog.w(TAG, "Chunk ${chunk.chunkIndex} attempt $attempts failed on $currentUrl: ${e.message}")
            }

            if (!success && !isPaused.get()) {
                attempts++
                currentUrl = allCdnUrls[attempts % allCdnUrls.size]
                chunk.assignedCdnUrl = currentUrl
                delay(400)
            }
        }
    }

    private suspend fun downloadSingleStream(
        videoId: String,
        url: String,
        partFile: File,
        outputFile: File,
        isPaused: AtomicBoolean,
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
                val buffer = ByteArray(32768)

                partFile.outputStream().use { out ->
                    var downloaded = 0L
                    var lastTime = System.currentTimeMillis()
                    var lastBytes = 0L

                    while (!isPaused.get()) {
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
                    if (!isPaused.get()) {
                        onProgress(downloaded, totalLength, 0L, 1)
                        if (outputFile.exists()) outputFile.delete()
                        partFile.renameTo(outputFile)
                        onComplete(outputFile)
                    }
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
        ZakoLog.i(TAG, "Paused download job for video $videoId")
    }

    fun isDownloading(videoId: String): Boolean = activeJobs.containsKey(videoId)
}
