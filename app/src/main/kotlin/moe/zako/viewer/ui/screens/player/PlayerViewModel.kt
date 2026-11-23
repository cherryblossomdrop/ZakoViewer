package moe.zako.viewer.ui.screens.player

import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.core.cdn.CdnNode
import moe.zako.viewer.core.cdn.PlaybackSourceCoordinator
import moe.zako.viewer.core.download.DownloadManager
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
import moe.zako.viewer.data.source.DataSourceRegistry
import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.ui.components.player.PlayerControlDefaults
import moe.zako.viewer.ui.components.ZakoToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
    val isLocalPlayback: Boolean = false,
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
    private val forceOnline: Boolean = false,
    val player: ZakoPlayer,
    private val favoriteRepository: FavoriteRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadDao: DownloadDao? = null,
    private val downloadManager: DownloadManager = DownloadManager.getInstance(player.context),
    private val playbackCoordinator: PlaybackSourceCoordinator = PlaybackSourceCoordinator(player.context, settingsRepository)
) : ViewModel() {
    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    @Volatile private var isExited = false

    init {
        observePlayerState()
        observeCdnCoordinator()
        observeSettings()
        loadFolders()
        loadAndStartPlayback()
        resetControlsTimeout()
    }

    private fun observePlayerState() {
        viewModelScope.launch {
            player.state.collect { state ->
                _uiState.update { it.copy(playerState = state) }
            }
        }
    }

    private fun observeCdnCoordinator() {
        viewModelScope.launch {
            playbackCoordinator.nodesFlow.collect { nodes ->
                _uiState.update { it.copy(cdnNodes = nodes) }
            }
        }
        viewModelScope.launch {
            playbackCoordinator.selectedNode.collect { node ->
                _uiState.update { it.copy(currentCdnNode = node) }
            }
        }
        viewModelScope.launch {
            playbackCoordinator.isTesting.collect { testing ->
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
                // 1. 若非强制在线播放，优先检测本地是否已有完整已下载视频（统一检测与校验）
                val localInfo = if (!forceOnline) {
                    downloadManager.findLocalCompletedVideo(videoId, initialLocalPath)
                } else null

                if (localInfo != null) {
                    val localFile = localInfo.file
                    val title = localInfo.title
                    val localCover = localInfo.coverUrl
                    ZakoLog.i(TAG, "Playing local downloaded video directly: ${localFile.absolutePath} for $videoId")

                    // 读取历史播放进度
                    val existingHistory = historyRepository.getHistory(videoId)
                    val resumePos = if (existingHistory != null && existingHistory.playbackPositionMs > 1000L &&
                        (existingHistory.totalDurationMs <= 0L || existingHistory.playbackPositionMs < existingHistory.totalDurationMs - 3000L)) {
                        existingHistory.playbackPositionMs
                    } else {
                        0L
                    }

                    // 保护历史网络封面：若已有历史网络封面则优先保留，避免被本地路径覆盖
                    val preservedCover = if (existingHistory?.coverUrl?.startsWith("http", ignoreCase = true) == true) {
                        existingHistory.coverUrl
                    } else if (localCover.startsWith("http", ignoreCase = true)) {
                        localCover
                    } else {
                        ""
                    }

                    _uiState.update {
                        it.copy(
                            isLocalPlayback = true,
                            video = CosVideoItem(
                                id = videoId,
                                title = title,
                                cover = preservedCover,
                                photo = preservedCover
                            )
                        )
                    }

                    // 记录历史
                    historyRepository.recordWatch(
                        videoId = videoId,
                        title = title,
                        coverUrl = preservedCover,
                        duration = existingHistory?.duration ?: "",
                        isVip = existingHistory?.isVip ?: false,
                        positionMs = resumePos,
                        totalDurationMs = existingHistory?.totalDurationMs ?: 0L
                    )

                    if (isExited || !isActive) return@launch

                    // 直接起播本地文件，跳过 CDN 测速与本地流代理
                    player.loadVideo(
                        url = localFile.absolutePath,
                        title = title,
                        headers = emptyMap(),
                        resumePos = resumePos
                    )

                    // 后台异步静默拉取详情（简介、标签、CDN线路列表等，离线或失败不影响本地播放）
                    launch(Dispatchers.IO) {
                        try {
                            val detail = DataSourceRegistry.getActiveDataSource(settingsRepository)?.getVideoDetail(videoId)
                            if (detail != null && !isExited) {
                                _uiState.update { current ->
                                    val mergedCover = if (detail.displayCover.startsWith("http", ignoreCase = true)) {
                                        detail.displayCover
                                    } else current.video?.cover ?: ""
                                    current.copy(
                                        video = detail.copy(cover = mergedCover, photo = mergedCover)
                                    )
                                }
                                val lines = detail.allVideoLines.ifEmpty { detail.allMp4Lines }
                                if (lines.isNotEmpty()) {
                                    playbackCoordinator.initializeNodes(lines)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    return@launch
                }

                // 2. 本地文件未命中，按数据源设置进入在线 CDN 路由流程
                _uiState.update { it.copy(isLocalPlayback = false) }
                val detail = DataSourceRegistry.getActiveDataSource(settingsRepository)?.getVideoDetail(videoId)
                _uiState.update { it.copy(video = detail) }

                detail?.let { vid ->
                    val lines = vid.allVideoLines.ifEmpty { vid.allMp4Lines }
                    if (lines.isNotEmpty()) {
                        val resolved = playbackCoordinator.resolveInitialPlaybackSource(
                            videoId = vid.id,
                            lines = lines,
                            engine = player.state.value.engine,
                            onBufferProgress = { bufferedMs, _, _ ->
                                player.updateBufferedPosition(bufferedMs)
                            },
                            isContextActive = { !isExited && isActive }
                        ) ?: return@launch

                        val existingHistory = historyRepository.getHistory(vid.id)
                        val resumePos = if (existingHistory != null && existingHistory.playbackPositionMs > 1000L &&
                            (existingHistory.totalDurationMs <= 0L || existingHistory.playbackPositionMs < existingHistory.totalDurationMs - 3000L)) {
                            existingHistory.playbackPositionMs
                        } else {
                            0L
                        }

                        val coverToSave = vid.displayCover.ifEmpty { vid.cover ?: "" }
                        historyRepository.recordWatch(
                            videoId = vid.id,
                            title = vid.title,
                            coverUrl = coverToSave,
                            duration = vid.duration ?: (existingHistory?.duration ?: ""),
                            isVip = vid.isVipEarlyAccess,
                            positionMs = resumePos,
                            totalDurationMs = existingHistory?.totalDurationMs ?: 0L
                        )

                        if (isExited || !isActive) return@launch

                        player.loadVideo(
                            url = resolved.streamUrl,
                            title = vid.title,
                            headers = resolved.headers,
                            resumePos = resumePos
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                ZakoLog.e(TAG, "Error starting playback for $videoId", e)
            }
        }
    }

    private var autoHideControlsJob: Job? = null

    fun resetControlsTimeout() {
        autoHideControlsJob?.cancel()
        if (_uiState.value.showControls && !_uiState.value.showMoreMenu &&
            !_uiState.value.showCdnDialog && !_uiState.value.showEngineDialog &&
            !_uiState.value.showHwDecDialog && !_uiState.value.showSpeedDialog
        ) {
            autoHideControlsJob = viewModelScope.launch {
                try {
                    delay(PlayerControlDefaults.AutoHideTimeoutMs)
                    _uiState.update { it.copy(showControls = false) }
                } catch (e: CancellationException) {
                    throw e
                }
            }
        }
    }

    // --- Controls and Menu ---
    fun toggleControls() {
        if (_uiState.value.showMoreMenu) {
            return
        }
        val nextShow = !_uiState.value.showControls
        _uiState.update { it.copy(showControls = nextShow) }
        if (nextShow) {
            resetControlsTimeout()
        } else {
            autoHideControlsJob?.cancel()
        }
    }

    fun toggleLock() {
        player.toggleLock()
        _uiState.update { it.copy(showControls = true) }
        resetControlsTimeout()
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
        if (show) autoHideControlsJob?.cancel() else resetControlsTimeout()
    }

    fun showEngineDialog(show: Boolean) {
        _uiState.update { it.copy(showEngineDialog = show) }
        if (show) autoHideControlsJob?.cancel() else resetControlsTimeout()
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

            val currentPos = player.state.value.currentPositionMs
            val activeUrl = player.state.value.activeUrl
            val isLocal = _uiState.value.isLocalPlayback || activeUrl.startsWith("/") || File(activeUrl).exists()

            if (isLocal) {
                player.loadVideo(
                    url = activeUrl,
                    title = _uiState.value.video?.title ?: "本地视频",
                    headers = emptyMap(),
                    resumePos = currentPos
                )
                return@launch
            }

            val vid = _uiState.value.video ?: return@launch
            val lines = vid.allVideoLines.ifEmpty { vid.allMp4Lines }
            if (lines.isNotEmpty()) {
                val targetNode = _uiState.value.currentCdnNode ?: playbackCoordinator.selectedNode.value
                val nodeToUse = targetNode ?: CdnNode(0, "线路 1", lines.first())
                val streamUrl = playbackCoordinator.buildStreamUrl(
                    videoId = vid.id,
                    node = nodeToUse,
                    allLines = lines,
                    engine = engine,
                    onBufferProgress = { bufferedMs, _, _ ->
                        player.updateBufferedPosition(bufferedMs)
                    }
                )
                player.loadVideo(
                    url = streamUrl,
                    title = vid.title,
                    headers = PlaybackSourceCoordinator.DEFAULT_PLAYBACK_HEADERS,
                    resumePos = currentPos
                )
            }
        }
    }

    fun showHwDecDialog(show: Boolean) {
        _uiState.update { it.copy(showHwDecDialog = show) }
        if (show) autoHideControlsJob?.cancel() else resetControlsTimeout()
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
        if (show) autoHideControlsJob?.cancel() else resetControlsTimeout()
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

    // 查看视频详情页（从本地播放页跳转详情页，避免后台播放冲突）
    fun onViewDetailClicked(onNavigate: (String) -> Unit) {
        player.pause()
        showMoreMenu(false)
        onNavigate(videoId)
    }

    // 4. 选择 CDN 节点播放 (弹窗打开即自动并发测速并自动选优，支持随时手动选线覆盖)
    fun showSelectCdnDialog() {
        _uiState.update { it.copy(showMoreMenu = false, showCdnDialog = true) }
        autoHideControlsJob?.cancel()

        viewModelScope.launch {
            // 确保获取到所有可用的在线 CDN 线路
            var lines = _uiState.value.video?.allVideoLines?.ifEmpty { _uiState.value.video?.allMp4Lines } ?: emptyList()
            if (lines.isEmpty()) {
                val detail = DataSourceRegistry.getActiveDataSource(settingsRepository)?.getVideoDetail(videoId)
                if (detail != null) {
                    _uiState.update { current ->
                        val mergedCover = if (detail.displayCover.startsWith("http", ignoreCase = true)) {
                            detail.displayCover
                        } else current.video?.cover ?: ""
                        current.copy(video = detail.copy(cover = mergedCover, photo = mergedCover))
                    }
                    lines = detail.allVideoLines.ifEmpty { detail.allMp4Lines }
                }
            }

            if (lines.isEmpty()) {
                ZakoLog.w(TAG, "No CDN lines available for speed test")
                return@launch
            }

            playbackCoordinator.startSpeedTestSession(
                coroutineScope = viewModelScope,
                lines = lines,
                isContextActive = { !isExited && viewModelScope.isActive && _uiState.value.showCdnDialog },
                onAutoSelected = { fastest ->
                    val activeUrl = player.state.value.activeUrl
                    if (_uiState.value.isLocalPlayback || activeUrl != fastest.url) {
                        val currentPos = player.state.value.currentPositionMs
                        switchPlayToCdnNode(fastest, resumePos = currentPos)
                        ZakoToast.show("已切至最快线路：${fastest.name}")
                    }
                }
            )
        }
    }

    fun dismissCdnDialog() {
        playbackCoordinator.cancelSpeedTestSession()
        _uiState.update { it.copy(showCdnDialog = false) }
        resetControlsTimeout()
    }

    fun selectCdnNode(node: CdnNode) {
        playbackCoordinator.onUserSelectNode(node)
        val activeUrl = player.state.value.activeUrl
        val currentPos = player.state.value.currentPositionMs

        if (!_uiState.value.isLocalPlayback && activeUrl == node.url) {
            _uiState.update { it.copy(showCdnDialog = false) }
            resetControlsTimeout()
            return
        }

        switchPlayToCdnNode(node, resumePos = currentPos)
        _uiState.update { it.copy(showCdnDialog = false) }
        resetControlsTimeout()
        ZakoToast.show("已切至: ${node.name}")
    }

    private fun switchPlayToCdnNode(node: CdnNode, resumePos: Long) {
        viewModelScope.launch {
            playbackCoordinator.selectNode(node)
            _uiState.update { it.copy(isLocalPlayback = false, currentCdnNode = node) }
            val title = _uiState.value.video?.title ?: "视频播放"
            val lines = _uiState.value.video?.allVideoLines?.ifEmpty { _uiState.value.video?.allMp4Lines } ?: listOf(node.url)
            val streamUrl = playbackCoordinator.buildStreamUrl(
                videoId = videoId,
                node = node,
                allLines = lines,
                engine = player.state.value.engine,
                onBufferProgress = { bufferedMs, _, _ ->
                    player.updateBufferedPosition(bufferedMs)
                }
            )

            if (isExited || !isActive) return@launch

            player.loadVideo(
                url = streamUrl,
                title = title,
                headers = PlaybackSourceCoordinator.DEFAULT_PLAYBACK_HEADERS,
                resumePos = resumePos
            )
        }
    }

    fun savePlaybackProgress() {
        val pos = player.state.value.currentPositionMs
        val dur = player.state.value.durationMs
        val vid = _uiState.value.video
        val title = vid?.title ?: ""
        val rawCover = vid?.cover ?: ""
        val cover = if (rawCover.startsWith("http", ignoreCase = true)) rawCover else ""
        if (pos > 0L) {
            try {
                runBlocking(Dispatchers.IO) {
                    historyRepository.recordWatch(
                        videoId = videoId,
                        title = title,
                        coverUrl = cover,
                        duration = vid?.duration ?: "",
                        isVip = vid?.isVipEarlyAccess ?: false,
                        positionMs = pos,
                        totalDurationMs = dur
                    )
                }
                ZakoLog.i(TAG, "Saved playback progress for $videoId: ${pos}ms / ${dur}ms")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                ZakoLog.w(TAG, "Failed to save playback progress for $videoId", e)
            }
        }
    }

    fun onExit() {
        if (isExited) return
        isExited = true
        autoHideControlsJob?.cancel()
        savePlaybackProgress()
        playbackJob?.cancel()
        playbackCoordinator.release(videoId)
        try {
            player.pause()
            player.detachSurface()
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        onExit()
        player.release()
    }
}
