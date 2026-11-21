package moe.zako.viewer.ui.screens.player

import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.core.cdn.CdnNode
import moe.zako.viewer.core.cdn.CdnSmartRouter
import moe.zako.viewer.core.external.ExternalLauncher
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.player.AspectRatioMode
import moe.zako.viewer.core.player.HwDecMode
import moe.zako.viewer.core.player.MultiNodeStreamBuffer
import moe.zako.viewer.core.player.ZakoPlayer
import moe.zako.viewer.core.player.PlayerEngine
import moe.zako.viewer.core.player.VideoPlayerState
import moe.zako.viewer.data.local.dao.DownloadDao
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.repository.FavoriteRepository
import moe.zako.viewer.data.repository.HistoryRepository
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.data.source.cosav.CosApiClient
import moe.zako.viewer.data.source.cosav.CosVideoItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import java.io.File

data class PlayerUiState(
    val video: CosVideoItem? = null,
    val playerState: VideoPlayerState = VideoPlayerState(),
    val cdnNodes: List<CdnNode> = emptyList(),
    val currentCdnNode: CdnNode? = null,
    val isCdnTesting: Boolean = false,
    val showControls: Boolean = true,
    val showMoreMenu: Boolean = false,
    val showCdnDialog: Boolean = false,
    val showEngineDialog: Boolean = false,
    val showHwDecDialog: Boolean = false,
    val showSpeedDialog: Boolean = false,
    val showFolderSelector: Boolean = false,
    val allFolders: List<FavoriteFolderEntity> = emptyList(),
    val isLandscape: Boolean = false,

    // Gesture Visual Feedback States
    val showPlayPauseIndicator: Boolean = false,
    val isIndicatorPlaying: Boolean = false,
    val seekDeltaSeconds: Int? = null,
    val seekTargetMs: Long? = null,
    val brightnessPercent: Int? = null,
    val volumePercent: Int? = null,
    val isSpeedBoosted: Boolean = false,
    val boostSpeed: Float = 2.0f,
    val externalPlayerPkg: String = ""
)

class PlayerViewModel(
    private val videoId: String,
    private val initialLocalPath: String = "",
    val player: ZakoPlayer,
    private val apiClient: CosApiClient = CosApiClient.instance,
    private val cdnSmartRouter: CdnSmartRouter = CdnSmartRouter(),
    private val favoriteRepository: FavoriteRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadDao: DownloadDao? = null
) : ViewModel() {
    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var speedTestJob: Job? = null
    @Volatile private var isExited = false

    init {
        observePlayerState()
        observeCdnRouter()
        observeSettings()
        loadFolders()
        loadAndStartPlayback()
    }

    private fun observePlayerState() {
        viewModelScope.launch {
            player.state.collect { state ->
                _uiState.update { it.copy(playerState = state) }
            }
        }
    }

    private fun observeCdnRouter() {
        viewModelScope.launch {
            cdnSmartRouter.nodesFlow.collect { nodes ->
                _uiState.update { it.copy(cdnNodes = nodes) }
            }
        }
        viewModelScope.launch {
            cdnSmartRouter.selectedNode.collect { node ->
                _uiState.update { it.copy(currentCdnNode = node) }
            }
        }
        viewModelScope.launch {
            cdnSmartRouter.isTesting.collect { testing ->
                _uiState.update { it.copy(isCdnTesting = testing) }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.longPressSpeedFlow.collect { speed ->
                player.setCustomBoostSpeed(speed)
                _uiState.update { it.copy(boostSpeed = speed) }
            }
        }
        viewModelScope.launch {
            settingsRepository.externalPlayerFlow.collect { pkg ->
                _uiState.update { it.copy(externalPlayerPkg = pkg) }
            }
        }
        viewModelScope.launch {
            settingsRepository.playerEngineFlow.collect { engStr ->
                val engine = when (engStr.lowercase()) {
                    "mpv" -> PlayerEngine.MPV
                    "system" -> PlayerEngine.SYSTEM
                    else -> PlayerEngine.AUTO
                }
                player.setEngine(engine)
            }
        }
        viewModelScope.launch {
            settingsRepository.hwDecModeFlow.collect { hwStr ->
                val mode = when (hwStr.lowercase()) {
                    "mediacodec" -> HwDecMode.MEDIACODEC
                    "mediacodec-copy" -> HwDecMode.MEDIACODEC_COPY
                    "auto" -> HwDecMode.AUTO
                    "no" -> HwDecMode.OFF
                    else -> HwDecMode.MEDIACODEC_COPY
                }
                player.setHwDecMode(mode)
            }
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            val folders = favoriteRepository.getAllFolders()
            _uiState.update { it.copy(allFolders = folders) }
        }
    }

    private fun loadAndStartPlayback() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            try {
                // 1. 优先检查本地是否存在已下载的完整视频文件
                var localFile: File? = if (initialLocalPath.isNotBlank()) File(initialLocalPath) else null
                val downloadRecord = downloadDao?.getDownloadByVideoId(videoId)

                if ((localFile == null || !localFile.exists() || localFile.length() == 0L) && downloadRecord != null) {
                    if (downloadRecord.status == DownloadEntity.STATUS_COMPLETED) {
                        val recordFile = File(downloadRecord.savePath)
                        if (recordFile.exists() && recordFile.length() > 0L) {
                            localFile = recordFile
                        }
                    }
                }

                if (localFile != null && localFile.exists() && localFile.length() > 0L) {
                    val title = downloadRecord?.title ?: "本地视频"
                    val cover = downloadRecord?.coverUrl ?: ""
                    ZakoLog.i(TAG, "Playing local downloaded video directly: ${localFile.absolutePath} for $videoId")
                    _uiState.update {
                        it.copy(
                            video = CosVideoItem(
                                id = videoId,
                                title = title,
                                cover = cover,
                                photo = cover
                            )
                        )
                    }

                    // 查询历史播放进度以支持续播
                    val existingHistory = historyRepository.getHistory(videoId)
                    val resumePos = if (existingHistory != null && existingHistory.playbackPositionMs > 1000L &&
                        (existingHistory.totalDurationMs <= 0L || existingHistory.playbackPositionMs < existingHistory.totalDurationMs - 3000L)) {
                        existingHistory.playbackPositionMs
                    } else {
                        0L
                    }

                    // 记录历史
                    historyRepository.recordWatch(
                        videoId = videoId,
                        title = title,
                        coverUrl = cover,
                        duration = "",
                        isVip = false,
                        positionMs = resumePos,
                        totalDurationMs = existingHistory?.totalDurationMs ?: 0L
                    )

                    if (isExited || !isActive) return@launch

                    // 强制直接加载本地文件播放，恢复历史进度
                    player.loadVideo(
                        url = localFile.absolutePath,
                        title = title,
                        headers = emptyMap(),
                        resumePos = resumePos
                    )
                    return@launch
                }

                // 2. 本地文件不存在时，请求在线视频流
                val detail = apiClient.getVideoDetail(videoId)
                _uiState.update { it.copy(video = detail) }

                detail?.let { vid ->
                    val lines = vid.allVideoLines.ifEmpty { vid.allMp4Lines }
                    if (lines.isNotEmpty()) {
                        val headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Linux; Android 14)",
                            "Referer" to "https://api.cos-dkop.net/"
                        )
                        val isMpv = player.state.value.engine == PlayerEngine.MPV ||
                                (player.state.value.engine == PlayerEngine.AUTO && moe.zako.viewer.core.player.MPVLib.isReady())

                        // 自动选线：若有多条线路并发测速选出最快节点
                        val targetNode = if (lines.size > 1) {
                            cdnSmartRouter.autoSelectFastestNode(lines)
                        } else {
                            cdnSmartRouter.initializeNodes(lines)
                            cdnSmartRouter.selectedNode.value ?: CdnNode(0, "线路 1", lines.first())
                        }

                        // 检查测速期间是否已退出或被取消
                        if (isExited || !isActive) {
                            ZakoLog.i(TAG, "Playback aborted: page exited during CDN speed test")
                            return@launch
                        }

                        val streamUrl = if (isMpv) {
                            ZakoLog.i(TAG, "libmpv is ready, directly streaming from selected CDN node: ${targetNode.name} (${targetNode.url})")
                            targetNode.url
                        } else {
                            ZakoLog.i(TAG, "Initializing multi-node streaming buffer on ${lines.size} CDN nodes")
                            val streamBuffer = MultiNodeStreamBuffer.getInstance(player.context)
                            streamBuffer.onBufferProgress = { bufferedMs, _, _ ->
                                player.updateBufferedPosition(bufferedMs)
                            }
                            streamBuffer.startBuffering(
                                videoId = vid.id,
                                cdnUrls = listOf(targetNode.url) + lines.filter { it != targetNode.url },
                                headers = headers
                            )
                        }

                        val existingHistory = historyRepository.getHistory(vid.id)
                        val resumePos = if (existingHistory != null && existingHistory.playbackPositionMs > 1000L &&
                            (existingHistory.totalDurationMs <= 0L || existingHistory.playbackPositionMs < existingHistory.totalDurationMs - 3000L)) {
                            existingHistory.playbackPositionMs
                        } else {
                            0L
                        }

                        historyRepository.recordWatch(
                            videoId = vid.id,
                            title = vid.title,
                            coverUrl = vid.cover ?: "",
                            positionMs = resumePos,
                            totalDurationMs = existingHistory?.totalDurationMs ?: 0L
                        )

                        if (isExited || !isActive) return@launch

                        player.loadVideo(
                            url = streamUrl,
                            title = vid.title,
                            headers = headers,
                            resumePos = resumePos
                        )
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    ZakoLog.e(TAG, "Error starting playback for $videoId", e)
                }
            }
        }
    }

    // --- Controls and Menu ---
    fun toggleControls() {
        if (_uiState.value.showMoreMenu) {
            return
        }
        if (!_uiState.value.playerState.isLocked) {
            _uiState.update { it.copy(showControls = !it.showControls) }
        }
    }

    fun toggleLock() {
        player.toggleLock()
        if (player.state.value.isLocked) {
            _uiState.update { it.copy(showControls = false) }
        }
    }

    fun toggleOrientation() {
        _uiState.update { it.copy(isLandscape = !it.isLandscape) }
    }

    fun cycleAspectRatio(): AspectRatioMode {
        val current = _uiState.value.playerState.aspectRatio
        val next = when (current) {
            AspectRatioMode.FIT -> AspectRatioMode.FILL
            AspectRatioMode.FILL -> AspectRatioMode.FIXED_16_9
            AspectRatioMode.FIXED_16_9 -> AspectRatioMode.FIXED_4_3
            AspectRatioMode.FIXED_4_3 -> AspectRatioMode.FIT
        }
        player.setAspectRatio(next)
        return next
    }

    fun showMoreMenu(show: Boolean) {
        _uiState.update { it.copy(showMoreMenu = show, showControls = if (show) true else it.showControls) }
    }

    fun showEngineDialog(show: Boolean) {
        _uiState.update { it.copy(showEngineDialog = show) }
    }

    fun switchEngine(engine: PlayerEngine) {
        player.setEngine(engine)
        showEngineDialog(false)
        viewModelScope.launch {
            val key = when (engine) {
                PlayerEngine.MPV -> "mpv"
                PlayerEngine.SYSTEM -> "system"
                PlayerEngine.AUTO -> "auto"
            }
            settingsRepository.setPlayerEngine(key)

            val vid = _uiState.value.video ?: return@launch
            val lines = vid.allVideoLines.ifEmpty { vid.allMp4Lines }
            val isLocal = player.state.value.activeUrl.startsWith("/") || File(player.state.value.activeUrl).exists()
            if (!isLocal && lines.isNotEmpty()) {
                val isMpv = engine == PlayerEngine.MPV || (engine == PlayerEngine.AUTO && moe.zako.viewer.core.player.MPVLib.isReady())
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14)",
                    "Referer" to "https://api.cos-dkop.net/"
                )
                val streamUrl = if (isMpv) {
                    lines.first()
                } else {
                    val streamBuffer = MultiNodeStreamBuffer.getInstance(player.context)
                    streamBuffer.onBufferProgress = { bufferedMs, _, _ ->
                        player.updateBufferedPosition(bufferedMs)
                    }
                    streamBuffer.startBuffering(
                        videoId = vid.id,
                        cdnUrls = lines,
                        headers = headers
                    )
                }
                player.loadVideo(
                    url = streamUrl,
                    title = vid.title,
                    headers = headers
                )
            }
        }
    }

    fun showHwDecDialog(show: Boolean) {
        _uiState.update { it.copy(showHwDecDialog = show) }
    }

    fun switchHwDecMode(mode: HwDecMode) {
        player.setHwDecMode(mode)
        viewModelScope.launch {
            settingsRepository.setHwDecMode(mode.code)
        }
        showHwDecDialog(false)
    }

    // --- Play / Pause Action ---
    fun togglePlayPause() {
        if (_uiState.value.playerState.isLocked) return
        val wasPlaying = player.state.value.isPlaying
        if (wasPlaying) {
            player.pause()
            _uiState.update { it.copy(showPlayPauseIndicator = true, isIndicatorPlaying = false) }
        } else {
            player.play()
            _uiState.update { it.copy(showPlayPauseIndicator = true, isIndicatorPlaying = true) }
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(650)
            _uiState.update { it.copy(showPlayPauseIndicator = false) }
        }
    }

    // --- Gesture Actions ---
    fun onDoubleTapCenter() {
        togglePlayPause()
    }

    fun onDoubleTapLeft() {
        if (_uiState.value.playerState.isLocked) return
        player.seekRelative(-10)
        _uiState.update { it.copy(seekDeltaSeconds = -10) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _uiState.update { it.copy(seekDeltaSeconds = null) }
        }
    }

    fun onDoubleTapRight() {
        if (_uiState.value.playerState.isLocked) return
        player.seekRelative(10)
        _uiState.update { it.copy(seekDeltaSeconds = 10) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _uiState.update { it.copy(seekDeltaSeconds = null) }
        }
    }

    fun onSeekGesture(deltaMs: Long) {
        if (_uiState.value.playerState.isLocked) return
        val current = _uiState.value.playerState.currentPositionMs
        val target = (current + deltaMs).coerceIn(0L, _uiState.value.playerState.durationMs)
        _uiState.update { it.copy(seekTargetMs = target) }
    }

    fun onSeekGestureEnd() {
        _uiState.value.seekTargetMs?.let { target ->
            player.seekTo(target)
        }
        _uiState.update { it.copy(seekTargetMs = null) }
    }

    fun onBrightnessGesture(percent: Int) {
        if (_uiState.value.playerState.isLocked) return
        _uiState.update { it.copy(brightnessPercent = percent) }
    }

    fun onBrightnessGestureEnd() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(brightnessPercent = null) }
        }
    }

    fun onVolumeGesture(percent: Int) {
        if (_uiState.value.playerState.isLocked) return
        _uiState.update { it.copy(volumePercent = percent) }
    }

    fun onVolumeGestureEnd() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(volumePercent = null) }
        }
    }

    fun onLongPressStart() {
        if (_uiState.value.playerState.isLocked) return
        player.setSpeedBoost(true)
        _uiState.update { it.copy(isSpeedBoosted = true) }
    }

    fun onLongPressEnd() {
        player.setSpeedBoost(false)
        _uiState.update { it.copy(isSpeedBoosted = false) }
    }

    fun showSpeedDialog(show: Boolean) {
        _uiState.update { it.copy(showSpeedDialog = show) }
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _uiState.update { it.copy(showSpeedDialog = false) }
    }

    // --- Top Bar "More Options" Menu (5 Items) ---
    // 1. 添加到收藏夹
    fun openAddToFavorites() {
        viewModelScope.launch {
            val folders = favoriteRepository.getAllFolders()
            _uiState.update { it.copy(showMoreMenu = false, showFolderSelector = true, allFolders = folders) }
        }
    }

    fun dismissFolderSelector() {
        _uiState.update { it.copy(showFolderSelector = false) }
    }

    fun createFolder(name: String, parentId: Long?) {
        viewModelScope.launch {
            favoriteRepository.createFolder(name, parentId)
            val folders = favoriteRepository.getAllFolders()
            _uiState.update { it.copy(allFolders = folders) }
        }
    }

    fun addToFavorites(folderId: Long) {
        viewModelScope.launch {
            val vid = _uiState.value.video ?: return@launch
            favoriteRepository.addToFavorites(
                folderId = folderId,
                videoId = vid.id,
                title = vid.title,
                coverUrl = vid.displayCover,
                duration = vid.duration ?: "",
                isVip = vid.isVipEarlyAccess
            )
            _uiState.update { it.copy(showFolderSelector = false) }
        }
    }

    // 2. 添加到稍后再看
    fun addToWatchLater() {
        viewModelScope.launch {
            val vid = _uiState.value.video ?: return@launch
            historyRepository.addToWatchLater(
                videoId = vid.id,
                title = vid.title,
                coverUrl = vid.displayCover,
                duration = vid.duration ?: "",
                isVip = vid.isVipEarlyAccess
            )
            _uiState.update { it.copy(showMoreMenu = false) }
        }
    }

    // 3. 使用外部播放器打开
    fun openWithExternalPlayer(context: Context) {
        player.pause()
        val currentUrl = _uiState.value.playerState.activeUrl
        val title = _uiState.value.video?.title ?: "视频播放"
        if (currentUrl.isNotEmpty()) {
            ExternalLauncher.openExternalPlayer(
                context = context,
                videoUrl = currentUrl,
                title = title,
                customPackage = _uiState.value.externalPlayerPkg.ifEmpty { null }
            )
        }
        _uiState.update { it.copy(showMoreMenu = false) }
    }

    // 4. 对 CDN 节点进行测速
    fun triggerCdnSpeedTest() {
        speedTestJob?.cancel()
        speedTestJob = viewModelScope.launch {
            _uiState.update { it.copy(showMoreMenu = false) }
            val lines = _uiState.value.video?.allVideoLines ?: emptyList()
            if (lines.isNotEmpty()) {
                val fastest = cdnSmartRouter.autoSelectFastestNode(lines)
                if (isActive && !isExited) {
                    selectCdnNode(fastest)
                }
            }
        }
    }

    // 5. 选择 CDN 节点播放 (弹窗选择)
    fun showSelectCdnDialog() {
        _uiState.update { it.copy(showMoreMenu = false, showCdnDialog = true) }
    }

    fun dismissCdnDialog() {
        _uiState.update { it.copy(showCdnDialog = false) }
    }

    fun selectCdnNode(node: CdnNode) {
        cdnSmartRouter.selectNode(node)
        val title = _uiState.value.video?.title ?: ""
        player.loadVideo(
            url = node.url,
            title = title,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14)",
                "Referer" to "https://api.cos-dkop.net/"
            )
        )
        _uiState.update { it.copy(showCdnDialog = false) }
    }

    fun savePlaybackProgress() {
        val pos = player.state.value.currentPositionMs
        val dur = player.state.value.durationMs
        val vid = _uiState.value.video
        val title = vid?.title ?: ""
        val cover = vid?.cover ?: ""
        if (pos > 0L) {
            try {
                runBlocking(Dispatchers.IO) {
                    historyRepository.recordWatch(
                        videoId = videoId,
                        title = title,
                        coverUrl = cover,
                        positionMs = pos,
                        totalDurationMs = dur
                    )
                }
                ZakoLog.i(TAG, "Saved playback progress for $videoId: ${pos}ms / ${dur}ms")
            } catch (e: Exception) {
                ZakoLog.w(TAG, "Failed to save playback progress for $videoId", e)
            }
        }
    }

    fun onExit() {
        if (isExited) return
        isExited = true
        savePlaybackProgress()
        playbackJob?.cancel()
        speedTestJob?.cancel()
        cdnSmartRouter.cancelTesting()
        try {
            player.pause()
            player.detachSurface()
        } catch (_: Exception) {}
        MultiNodeStreamBuffer.stopIfActive(videoId)
    }

    override fun onCleared() {
        super.onCleared()
        onExit()
        player.release()
    }
}
