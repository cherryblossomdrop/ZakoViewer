package moe.zako.viewer.core.player

import android.content.Context
import moe.zako.viewer.core.logger.ZakoLog
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Multi-node concurrent video stream buffering engine.
 * Dispatches segment downloads across all available CDN nodes in parallel,
 * continuously caches ahead to the end of the video, and exposes a local
 * zero-latency HTTP streaming proxy for MPV and System MediaPlayer.
 */
class MultiNodeStreamBuffer(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "MultiNodeStreamBuffer"
        private const val THREADS_PER_NODE = 2

        @Volatile
        private var instance: MultiNodeStreamBuffer? = null

        fun getInstance(context: Context): MultiNodeStreamBuffer {
            return instance ?: synchronized(this) {
                instance ?: MultiNodeStreamBuffer(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var proxyPort: Int = 0
    private val isServerRunning = AtomicBoolean(false)

    // Current active buffer session
    private var activeVideoId: String? = null
    private var activeJob: Job? = null
    private val activeSegments = ConcurrentHashMap<Int, Boolean>()

    // Priority demand channel for seeking ahead
    private val prioritySegment = AtomicInteger(-1)

    // Callbacks
    var onBufferProgress: ((bufferedMs: Long, totalDurationMs: Long, percent: Float) -> Unit)? = null

    init {
        startLocalProxyServer()
    }

    private fun startLocalProxyServer() {
        try {
            val server = ServerSocket(0)
            serverSocket = server
            proxyPort = server.localPort
            isServerRunning.set(true)
            ZakoLog.i(TAG, "Local streaming proxy server started on 127.0.0.1:$proxyPort")

            scope.launch {
                while (isServerRunning.get() && !server.isClosed) {
                    try {
                        val clientSocket = server.accept()
                        launch {
                            handleClientSocket(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (isServerRunning.get()) {
                            ZakoLog.w(TAG, "Proxy server accept error", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            ZakoLog.e(TAG, "Failed to start local proxy server", e)
        }
    }

    private fun handleClientSocket(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = BufferedOutputStream(socket.getOutputStream())

            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val path = parts[1]

            // Parse headers, specifically Range header
            var rangeHeader: String? = null
            var headerLine: String?
            while (input.readLine().also { headerLine = it } != null) {
                if (headerLine.isNullOrBlank()) break
                if (headerLine!!.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = headerLine!!.substringAfter(':').trim()
                }
            }

            // Format: /video/{videoId}/index.m3u8 or /video/{videoId}/seg_{index}.ts or /video/{videoId}/xxx.mp4
            val segments = path.trim('/').split('/')
            if (segments.size >= 3 && segments[0] == "video") {
                val videoId = segments[1]
                val filename = segments[2].substringBefore('?')

                val videoDir = File(File(context.cacheDir, "stream_buffer"), videoId)
                val targetFile = File(videoDir, filename)

                if (filename.endsWith(".m3u8")) {
                    serveM3u8Playlist(targetFile, output)
                } else if (filename.endsWith(".ts")) {
                    val index = filename.removePrefix("seg_").removeSuffix(".ts").toIntOrNull()
                    if (index != null && !targetFile.exists()) {
                        // Priority fetch for immediate player playback / seek
                        prioritySegment.set(index)
                        // Wait up to 10s for segment to arrive
                        var waitedMs = 0
                        while (!targetFile.exists() && waitedMs < 10000) {
                            Thread.sleep(100)
                            waitedMs += 100
                        }
                    }
                    serveMediaFile(targetFile, output, "video/mp2t", rangeHeader)
                } else {
                    serveMediaFile(targetFile, output, "video/mp4", rangeHeader)
                }
            }
        } catch (_: java.net.SocketException) {
            // Client closed connection during seek or cancel - completely normal
        } catch (e: Exception) {
            if (e is IOException && (e.message?.contains("Broken pipe", ignoreCase = true) == true ||
                                    e.message?.contains("Connection reset", ignoreCase = true) == true)) {
                // Client closed socket normally
            } else {
                ZakoLog.d(TAG, "Client socket handled: ${e.message}")
            }
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun serveM3u8Playlist(file: File, output: OutputStream) {
        if (!file.exists()) {
            val response = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            output.write(response.toByteArray())
            output.flush()
            return
        }
        val bytes = file.readBytes()
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/vnd.apple.mpegurl\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun serveMediaFile(file: File, output: OutputStream, mimeType: String, rangeHeader: String? = null) {
        if (!file.exists()) {
            val response = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            output.write(response.toByteArray())
            output.flush()
            return
        }
        val fileLength = file.length()
        var start = 0L
        var end = fileLength - 1L
        var isRange = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
            val rangeSpec = rangeHeader.substringAfter("bytes=").trim()
            val rangeParts = rangeSpec.split("-")
            val rangeStart = rangeParts.getOrNull(0)?.toLongOrNull()
            val rangeEnd = rangeParts.getOrNull(1)?.toLongOrNull()

            if (rangeStart != null) {
                start = rangeStart.coerceIn(0L, fileLength - 1L)
                if (rangeEnd != null && rangeEnd >= start) {
                    end = rangeEnd.coerceIn(start, fileLength - 1L)
                }
                isRange = true
            }
        }

        val contentLength = end - start + 1L

        val headers = if (isRange) {
            "HTTP/1.1 206 Partial Content\r\n" +
                    "Content-Type: $mimeType\r\n" +
                    "Content-Length: $contentLength\r\n" +
                    "Content-Range: bytes $start-$end/$fileLength\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Connection: close\r\n\r\n"
        } else {
            "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: $mimeType\r\n" +
                    "Content-Length: $fileLength\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Connection: close\r\n\r\n"
        }

        try {
            output.write(headers.toByteArray(Charsets.UTF_8))
            output.flush()

            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buf = ByteArray(32768)
                var remaining = contentLength
                while (remaining > 0L) {
                    val toRead = minOf(remaining, buf.size.toLong()).toInt()
                    val bytesRead = raf.read(buf, 0, toRead)
                    if (bytesRead == -1) break
                    output.write(buf, 0, bytesRead)
                    remaining -= bytesRead
                }
            }
            output.flush()
        } catch (_: java.net.SocketException) {
            // Client seeked away or cancelled stream
        } catch (e: IOException) {
            if (e.message?.contains("Broken pipe", ignoreCase = true) != true &&
                e.message?.contains("Connection reset", ignoreCase = true) != true) {
                ZakoLog.w(TAG, "serveMediaFile IOException: ${e.message}")
            }
        }
    }

    data class HlsSegment(
        val index: Int,
        val relativeUri: String,
        val durationSec: Double
    )

    /**
     * Starts continuous multi-node background buffering for a given video stream.
     * Returns the local proxy URL that the player should play.
     */
    suspend fun startBuffering(
        videoId: String,
        cdnUrls: List<String>,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val primaryUrl = cdnUrls.firstOrNull() ?: return@withContext ""
        if (videoId.isBlank() || videoId.equals("null", ignoreCase = true)) {
            return@withContext primaryUrl
        }
        if (activeVideoId != null && activeVideoId != videoId) {
            stopBuffering(activeVideoId)
        }
        activeVideoId = videoId
        activeSegments.clear()
        prioritySegment.set(-1)

        val videoDir = File(File(context.cacheDir, "stream_buffer"), videoId)
        if (!videoDir.exists()) videoDir.mkdirs()

        val isHls = primaryUrl.contains(".m3u8", ignoreCase = true)

        if (!isHls || proxyPort == 0) {
            // Return direct URL if proxy unavailable or not HLS
            return@withContext primaryUrl
        }

        // Step 1: Fetch and parse original M3U8 playlist
        val playlistText = fetchM3u8Content(primaryUrl, headers)
        if (playlistText == null) {
            ZakoLog.w(TAG, "Failed to fetch master m3u8 playlist, falling back to direct stream")
            return@withContext primaryUrl
        }

        val segments = parseSegments(playlistText)
        if (segments.isEmpty()) {
            return@withContext primaryUrl
        }

        val totalDurationMs = (segments.sumOf { it.durationSec } * 1000).toLong()

        // Step 2: Write local proxy playlist pointing to 127.0.0.1:$proxyPort
        val localPlaylistFile = File(videoDir, "index.m3u8")
        val rewrittenPlaylist = rewritePlaylistForLocalProxy(playlistText, videoId)
        localPlaylistFile.writeText(rewrittenPlaylist)

        // Step 3: Launch multi-node concurrent buffering worker
        activeJob = scope.launch {
            bufferAllSegmentsConcurrently(videoId, cdnUrls, segments, videoDir, headers, totalDurationMs)
        }

        val proxyUrl = "http://127.0.0.1:$proxyPort/video/$videoId/index.m3u8"
        ZakoLog.i(TAG, "Multi-node buffer initialized for $videoId with ${segments.size} segments across ${cdnUrls.size} CDN nodes. Playing via $proxyUrl")
        return@withContext proxyUrl
    }

    private fun fetchM3u8Content(url: String, headers: Map<String, String>): String? {
        return try {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            ZakoLog.w(TAG, "fetchM3u8Content error", e)
            null
        }
    }

    private fun parseSegments(playlist: String): List<HlsSegment> {
        val list = mutableListOf<HlsSegment>()
        var currentDuration = 0.0
        var segIndex = 0

        playlist.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("#EXTINF:")) {
                val durStr = line.substringAfter("#EXTINF:").substringBefore(",")
                currentDuration = durStr.toDoubleOrNull() ?: 10.0
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                list.add(HlsSegment(segIndex, line, currentDuration))
                segIndex++
            }
        }
        return list
    }

    private fun rewritePlaylistForLocalProxy(original: String, videoId: String): String {
        val sb = StringBuilder()
        var segIndex = 0
        original.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isNotEmpty() && !line.startsWith("#")) {
                sb.append("http://127.0.0.1:$proxyPort/video/$videoId/seg_$segIndex.ts\n")
                segIndex++
            } else {
                sb.append(rawLine).append("\n")
            }
        }
        return sb.toString()
    }

    private suspend fun bufferAllSegmentsConcurrently(
        videoId: String,
        cdnUrls: List<String>,
        segments: List<HlsSegment>,
        videoDir: File,
        headers: Map<String, String>,
        totalDurationMs: Long
    ) = coroutineScope {
        val numNodes = cdnUrls.size.coerceAtLeast(1)
        val concurrency = (numNodes * THREADS_PER_NODE).coerceIn(4, 12)
        val nextIndex = AtomicInteger(0)

        // Mark existing cached files
        for (seg in segments) {
            val file = File(videoDir, "seg_${seg.index}.ts")
            if (file.exists() && file.length() > 0) {
                activeSegments[seg.index] = true
            }
        }

        val workers = List(concurrency) { workerId ->
            launch {
                while (isActive && activeVideoId == videoId) {
                    // Check if player demanded a high-priority seek segment
                    val priority = prioritySegment.getAndSet(-1)
                    val targetIndex = if (priority in segments.indices && activeSegments[priority] != true) {
                        priority
                    } else {
                        nextIndex.getAndIncrement()
                    }

                    if (targetIndex >= segments.size) break
                    if (activeSegments[targetIndex] == true) continue

                    val seg = segments[targetIndex]
                    val targetFile = File(videoDir, "seg_${seg.index}.ts")
                    if (targetFile.exists() && targetFile.length() > 0) {
                        activeSegments[seg.index] = true
                        continue
                    }

                    // Choose CDN node in round-robin fashion
                    val cdnNodeUrl = cdnUrls[(workerId + targetIndex) % numNodes]
                    val fullSegUrl = resolveSegmentUrl(cdnNodeUrl, seg.relativeUri)

                    val downloaded = downloadSegmentWithFallback(fullSegUrl, targetFile, cdnUrls, seg.relativeUri, headers)
                    if (downloaded) {
                        activeSegments[seg.index] = true
                        val bufferedCount = activeSegments.size
                        val bufferedMs = (segments.take(bufferedCount).sumOf { it.durationSec } * 1000).toLong().coerceIn(0L, totalDurationMs)
                        val percent = if (totalDurationMs > 0) bufferedMs.toFloat() / totalDurationMs.toFloat() else 0f
                        onBufferProgress?.invoke(bufferedMs, totalDurationMs, percent)
                    }
                }
            }
        }
        workers.joinAll()
        ZakoLog.i(TAG, "Multi-node background buffer fully completed to the end of video $videoId")
    }

    private fun resolveSegmentUrl(baseCdnUrl: String, relativeUri: String): String {
        return if (relativeUri.startsWith("http://") || relativeUri.startsWith("https://")) {
            relativeUri
        } else {
            val base = baseCdnUrl.substringBeforeLast('/') + "/"
            base + relativeUri
        }
    }

    private fun downloadSegmentWithFallback(
        primaryUrl: String,
        targetFile: File,
        allCdnUrls: List<String>,
        relativeUri: String,
        headers: Map<String, String>
    ): Boolean {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        val candidateUrls = mutableListOf(primaryUrl)
        for (node in allCdnUrls) {
            val u = resolveSegmentUrl(node, relativeUri)
            if (!candidateUrls.contains(u)) candidateUrls.add(u)
        }

        for (url in candidateUrls) {
            try {
                val reqBuilder = Request.Builder().url(url)
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            tempFile.renameTo(targetFile)
                            return true
                        }
                    }
                }
            } catch (_: Exception) {
                tempFile.delete()
            }
        }
        return false
    }

    /**
     * Stops and cancels all background buffering jobs for the current video.
     */
    fun stopBuffering(videoId: String? = null) {
        val target = activeVideoId ?: return
        if (target.isBlank() || target.equals("null", ignoreCase = true)) {
            activeVideoId = null
            return
        }
        if (videoId == null || videoId == target) {
            ZakoLog.i(TAG, "Stopping multi-node buffer for $target")
            activeJob?.cancel()
            activeJob = null
            activeVideoId = null
            activeSegments.clear()
            prioritySegment.set(-1)
        }
    }

    /**
     * Clears all local video segment cache in cacheDir/stream_buffer.
     */
    fun clearCache() {
        stopBuffering()
        try {
            val bufferDir = File(context.cacheDir, "stream_buffer")
            if (bufferDir.exists()) {
                bufferDir.deleteRecursively()
            }
        } catch (e: Exception) {
            ZakoLog.w(TAG, "Error clearing stream buffer cache", e)
        }
    }
}
