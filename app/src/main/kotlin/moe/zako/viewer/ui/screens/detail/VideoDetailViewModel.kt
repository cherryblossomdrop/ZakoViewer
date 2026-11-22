package moe.zako.viewer.ui.screens.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.core.cdn.CdnNode
import moe.zako.viewer.core.cdn.PlaybackSourceCoordinator
import moe.zako.viewer.core.download.DownloadManager
import moe.zako.viewer.core.external.ExternalLauncher
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.logger.ZakoLogger
import moe.zako.viewer.core.player.AspectRatioMode
import moe.zako.viewer.core.player.HwDecMode
import moe.zako.viewer.core.player.MultiNodeStreamBuffer
import moe.zako.viewer.core.player.ZakoPlayer
import moe.zako.viewer.core.player.VideoPlayerState
import moe.zako.viewer.core.player.PlayerEngine
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.repository.FavoriteRepository
import moe.zako.viewer.data.repository.HistoryRepository
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.data.source.cosav.CosApiClient
import moe.zako.viewer.data.source.cosav.CosVideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.isActive

data class VideoDetailUiState(
    val video: CosVideoItem? = null,
    val isLoading: Boolean = true,
    val isCommentsExpanded: Boolean = false, // 留言区默认处于折叠收起状态
    val isPreviewPlaying: Boolean = false,
    val isFavorite: Boolean = false,
    val isWatchLater: Boolean = false,
    val downloadStatus: String = "NONE", // "NONE", "DOWNLOADING", "COMPLETED"
    val isLongPressShareUnlocked: Boolean = false,
    val isLongPressShareEnabled: Boolean = false,
    val allFolders: List<FavoriteFolderEntity> = emptyList(),
    val showFolderSelector: Boolean = false,
    val showDownloadChoiceDialog: Boolean = false,
    val externalDownloaderPkg: String = "",
    val externalPlayerPkg: String = "",
    val errorMessage: String? = null,
    val isInlinePlaying: Boolean = false,
    val isFullscreen: Boolean = false,
    val showControls: Boolean = true,
    val playerState: VideoPlayerState = VideoPlayerState(),
    val showPlayPauseIndicator: Boolean = false,
    val isIndicatorPlaying: Boolean = false,
    val seekDeltaSeconds: Int? = null,
    val seekTargetMs: Long? = null,
    val brightnessPercent: Int? = null,
    val volumePercent: Int? = null,
    val isSpeedBoosted: Boolean = false,
    val boostSpeed: Float = 2.0f,
    val showSpeedDialog: Boolean = false,
    val showMoreMenu: Boolean = false,
    val showCdnDialog: Boolean = false,
    val cdnNodes: List<CdnNode> = emptyList(),
    val currentCdnNode: CdnNode? = null,
    val isCdnTesting: Boolean = false,
    val showEngineDialog: Boolean = false,
    val showHwDecDialog: Boolean = false
)

class VideoDetailViewModel(
    val videoId: String,
    private val apiClient: CosApiClient = CosApiClient.instance,
    private val favoriteRepository: FavoriteRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadManager: DownloadManager,
    val player: ZakoPlayer,
    private val playbackCoordinator: PlaybackSourceCoordinator = PlaybackSourceCoordinator(player.context, settingsRepository)
) : ViewModel() {
    companion object {
        private const val TAG = "VideoDetailViewModel"
    }

    private val _uiState = MutableStateFlow(VideoDetailUiState())
    val uiState: StateFlow<VideoDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
        observeDataSources()
        loadFolders()
        observePlayer()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val detail = apiClient.getVideoDetail(videoId)
                if (detail != null) {
                    var related = detail.relatedVideos?.toMutableList() ?: mutableListOf()
                    if (related.size < 10) {
                        val supplementKeyword = detail.tagList.firstOrNull()?.trim()
                            ?: detail.cosWorks?.trim()?.split(" ")?.firstOrNull()
                            ?: detail.cosRole?.trim()?.split(" ")?.firstOrNull()
                            ?: ""
                        if (supplementKeyword.isNotEmpty()) {
                            try {
                                val searchRes = apiClient.getVideoLists(keyword = supplementKeyword, limit = 20)
                                val extraItems = searchRes.items.filter { it.id != detail.id && related.none { r -> r.id == it.id } }
                                related.addAll(extraItems)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                ZakoLog.w(TAG, "Failed to supplement related videos by keyword $supplementKeyword", e)
                            }
                        }
                        if (related.size < 10) {
                            try {
                                val recs = apiClient.getVideoRecommend()
                                val extraRecs = recs.filter { it.id != detail.id && related.none { r -> r.id == it.id } }
                                related.addAll(extraRecs)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                ZakoLog.w(TAG, "Failed to supplement related videos by recommendations", e)
                            }
                        }
                    }
                    val cappedRelated = related.take(20)
                    val enrichedDetail = detail.copy(relatedVideos = cappedRelated)
                    _uiState.update { it.copy(video = enrichedDetail, isLoading = false) }

                    // Record in watch history
                    historyRepository.recordWatch(
                        videoId = enrichedDetail.id,
                        title = enrichedDetail.title,
                        coverUrl = enrichedDetail.displayCover,
                        duration = enrichedDetail.duration ?: "",
                        isVip = enrichedDetail.isVipEarlyAccess
                    )
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "视频信息为空") }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                ZakoLogger.e(TAG, "Failed to load video detail for ID $videoId", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun observeDataSources() {
        // 观察收藏状态
        viewModelScope.launch {
            favoriteRepository.isFavoriteFlow(videoId).collect { isFav ->
                _uiState.update { it.copy(isFavorite = isFav) }
            }
        }

        // 观察稍后再看状态
        viewModelScope.launch {
            historyRepository.isWatchLaterFlow(videoId).collect { isLater ->
                _uiState.update { it.copy(isWatchLater = isLater) }
            }
        }

        // 观察下载状态 (NONE / DOWNLOADING / COMPLETED)
        viewModelScope.launch {
            downloadManager.getDownloadStatusFlow(videoId).collect { entity ->
                val status = when {
                    entity == null -> "NONE"
                    entity.status == "COMPLETED" -> "COMPLETED"
                    else -> "DOWNLOADING"
                }
                _uiState.update { it.copy(downloadStatus = status) }
            }
        }

        // 观察设置
        viewModelScope.launch {
            settingsRepository.externalDownloaderFlow.collect { pkg ->
                _uiState.update { it.copy(externalDownloaderPkg = pkg) }
            }
        }
        viewModelScope.launch {
            settingsRepository.longPressShareUnlockedFlow.collect { unlocked ->
                _uiState.update { it.copy(isLongPressShareUnlocked = unlocked) }
            }
        }
        viewModelScope.launch {
            settingsRepository.longPressShareEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(isLongPressShareEnabled = enabled) }
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

    private fun observePlayer() {
        viewModelScope.launch {
            player.state.collect { pState ->
                _uiState.update { it.copy(playerState = pState) }
            }
        }
        viewModelScope.launch {
            playbackCoordinator.nodesFlow.collect { nodes ->
                _uiState.update { it.copy(cdnNodes = nodes) }
            }
        }
        viewModelScope.launch {
            playbackCoordinator.selectedNode.collect { current ->
                _uiState.update { it.copy(currentCdnNode = current) }
            }
        }
        viewModelScope.launch {
            playbackCoordinator.isTesting.collect { testing ->
                _uiState.update { it.copy(isCdnTesting = testing) }
            }
        }
        viewModelScope.launch {
            settingsRepository.longPressSpeedFlow.collect { speed ->
                _uiState.update { it.copy(boostSpeed = speed) }
            }
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            val folders = favoriteRepository.getAllFolders()
            _uiState.update { it.copy(allFolders = folders) }
        }
    }

    fun toggleCommentsExpanded() {
        _uiState.update { it.copy(isCommentsExpanded = !it.isCommentsExpanded) }
    }

    fun togglePreview() {
        _uiState.update { it.copy(isPreviewPlaying = !it.isPreviewPlaying) }
    }

    fun onFavoriteClick() {
        viewModelScope.launch {
            if (_uiState.value.isFavorite) {
                favoriteRepository.removeFromFavorites(videoId)
            } else {
                _uiState.update { it.copy(showFolderSelector = true) }
            }
        }
    }

    fun toggleWatchLater() {
        viewModelScope.launch {
            val current = _uiState.value.video ?: return@launch
            if (_uiState.value.isWatchLater) {
                historyRepository.removeFromWatchLater(videoId)
            } else {
                historyRepository.addToWatchLater(
                    videoId = current.id,
                    title = current.title,
                    coverUrl = current.displayCover,
                    duration = current.duration ?: "",
                    isVip = current.isVipEarlyAccess
                )
            }
        }
    }

    fun showFavoriteFolderPicker() {
        loadFolders()
        _uiState.update { it.copy(showFolderSelector = true) }
    }

    fun dismissFolderPicker() {
        _uiState.update { it.copy(showFolderSelector = false) }
    }

    fun createFolder(name: String, parentId: Long?) {
        viewModelScope.launch {
            favoriteRepository.createFolder(name, parentId)
            loadFolders()
        }
    }

    fun addToFavoriteFolder(folderId: Long) {
        viewModelScope.launch {
            val current = _uiState.value.video ?: return@launch
            favoriteRepository.addToFavorites(
                folderId = folderId,
                videoId = current.id,
                title = current.title,
                coverUrl = current.displayCover,
                duration = current.duration ?: "",
                isVip = current.isVipEarlyAccess
            )
            _uiState.update { it.copy(showFolderSelector = false) }
        }
    }

    fun onDownloadClick() {
        startInternalDownload()
    }

    fun onDownloadLongClick() {
        // 长按：弹出选择菜单，允许选择内置下载器或配置的外部下载器
        _uiState.update { it.copy(showDownloadChoiceDialog = true) }
    }

    fun dismissDownloadChoice() {
        _uiState.update { it.copy(showDownloadChoiceDialog = false) }
    }

    fun startInternalDownload() {
        val current = _uiState.value.video ?: return
        val urls = current.allMp4Lines.ifEmpty { current.allVideoLines }
        downloadManager.enqueueDownload(
            videoId = current.id,
            title = current.title,
            coverUrl = current.displayCover,
            cdnUrls = urls,
            duration = current.duration ?: ""
        )
        _uiState.update { it.copy(showDownloadChoiceDialog = false) }
    }

    fun startExternalDownload(context: Context) {
        player.pause()
        val current = _uiState.value.video ?: return
        val targetUrl = current.allMp4Lines.firstOrNull() ?: current.allVideoLines.firstOrNull() ?: return
        ExternalLauncher.openExternalDownloader(
            context = context,
            downloadUrl = targetUrl,
            title = current.title,
            customPackage = _uiState.value.externalDownloaderPkg.ifEmpty { null }
        )
        _uiState.update { it.copy(showDownloadChoiceDialog = false) }
    }

    fun startInlinePlayback() {
        val current = _uiState.value.video ?: return
        val lines = current.allVideoLines.ifEmpty { current.allMp4Lines }
        if (lines.isEmpty()) {
            return
        }
        _uiState.update { it.copy(isInlinePlaying = true) }
        viewModelScope.launch {
            try {
                // 详情页内嵌播放强制走在线片源、不适用本地优先拦截
                val resolved = playbackCoordinator.resolveInitialPlaybackSource(
                    videoId = current.id,
                    lines = lines,
                    engine = player.state.value.engine,
                    isContextActive = { isActive }
                ) ?: return@launch

                player.loadVideo(
                    url = resolved.streamUrl,
                    title = current.title,
                    headers = resolved.headers
                )
                player.play()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                ZakoLog.e(TAG, "Error starting inline playback for $videoId", e)
            }
        }
    }

    /**
     * 切换内嵌播放器的全屏状态（页面内原地横屏沉浸）
     *
     * 【跨模块核心契约规则】：
     * 从视频详情页内嵌播放进入全屏播放时，必须严格维持当前在线片源（保留当前在线流、
     * 已选 CDN 线路节点及实时播放进度），严禁触发「本地已下载视频优先直进」拦截，
     * 绝不切换为本地文件播放，确保全屏与内嵌切换期间的纯在线流媒体播放连续性。
     */
    fun setFullscreen(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreen = fullscreen) }
    }

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

    fun cycleAspectRatio(): AspectRatioMode {
        val next = when (_uiState.value.playerState.aspectRatio) {
            AspectRatioMode.FIT -> AspectRatioMode.FILL
            AspectRatioMode.FILL -> AspectRatioMode.FIXED_16_9
            AspectRatioMode.FIXED_16_9 -> AspectRatioMode.FIXED_4_3
            AspectRatioMode.FIXED_4_3 -> AspectRatioMode.FIT
        }
        player.setAspectRatio(next)
        return next
    }

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

            val current = _uiState.value.video ?: return@launch
            val lines = current.allVideoLines.ifEmpty { current.allMp4Lines }
            if (lines.isNotEmpty()) {
                val currentPos = player.state.value.currentPositionMs
                val targetNode = _uiState.value.currentCdnNode ?: playbackCoordinator.selectedNode.value
                val nodeToUse = targetNode ?: CdnNode(0, "线路 1", lines.first())
                val streamUrl = playbackCoordinator.buildStreamUrl(
                    videoId = current.id,
                    node = nodeToUse,
                    allLines = lines,
                    engine = engine
                )
                player.loadVideo(
                    url = streamUrl,
                    title = current.title,
                    headers = PlaybackSourceCoordinator.DEFAULT_PLAYBACK_HEADERS,
                    resumePos = currentPos
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

    fun openAddToFavorites() {
        showFavoriteFolderPicker()
        _uiState.update { it.copy(showMoreMenu = false) }
    }

    fun triggerCdnSpeedTest() {
        showSelectCdnDialog()
    }

    fun showSelectCdnDialog() {
        _uiState.update { it.copy(showMoreMenu = false, showCdnDialog = true) }
        val current = _uiState.value.video ?: return
        val lines = current.allVideoLines.ifEmpty { current.allMp4Lines }
        if (lines.isNotEmpty()) {
            playbackCoordinator.startSpeedTestSession(
                coroutineScope = viewModelScope,
                lines = lines,
                isContextActive = { viewModelScope.isActive && _uiState.value.showCdnDialog },
                onAutoSelected = { fastest ->
                    val activeUrl = player.state.value.activeUrl
                    if (activeUrl != fastest.url) {
                        val currentPos = player.state.value.currentPositionMs
                        switchPlayToCdnNode(fastest, resumePos = currentPos)
                    }
                }
            )
        }
    }

    fun dismissCdnDialog() {
        playbackCoordinator.cancelSpeedTestSession()
        _uiState.update { it.copy(showCdnDialog = false) }
    }

    fun selectCdnNode(node: CdnNode) {
        playbackCoordinator.onUserSelectNode(node)
        val currentPos = player.state.value.currentPositionMs
        switchPlayToCdnNode(node, resumePos = currentPos)
        dismissCdnDialog()
    }

    private fun switchPlayToCdnNode(node: CdnNode, resumePos: Long = 0L) {
        viewModelScope.launch {
            val current = _uiState.value.video ?: return@launch
            val lines = current.allVideoLines.ifEmpty { current.allMp4Lines }.ifEmpty { listOf(node.url) }
            val streamUrl = playbackCoordinator.buildStreamUrl(
                videoId = current.id,
                node = node,
                allLines = lines,
                engine = player.state.value.engine
            )
            player.loadVideo(
                url = streamUrl,
                title = current.title,
                headers = PlaybackSourceCoordinator.DEFAULT_PLAYBACK_HEADERS,
                resumePos = resumePos
            )
        }
    }

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

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun seekBy(deltaSeconds: Int) {
        val currentMs = player.state.value.currentPositionMs
        val durationMs = player.state.value.durationMs
        val target = (currentMs + deltaSeconds * 1000L).coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
        player.seekTo(target)
    }

    fun openWithExternalPlayer(context: Context) {
        player.pause()
        val current = _uiState.value.video ?: return
        val currentUrl = player.state.value.activeUrl.ifEmpty {
            current.allMp4Lines.firstOrNull() ?: current.allVideoLines.firstOrNull() ?: ""
        }
        if (currentUrl.isNotEmpty()) {
            ExternalLauncher.openExternalPlayer(
                context = context,
                videoUrl = currentUrl,
                title = current.title,
                customPackage = _uiState.value.externalPlayerPkg.ifEmpty { null }
            )
        }
        _uiState.update { it.copy(showMoreMenu = false) }
    }

    private fun savePlaybackProgress() {
        val pos = player.state.value.currentPositionMs
        val dur = player.state.value.durationMs
        val vid = _uiState.value.video
        val title = vid?.title ?: ""
        val cover = vid?.displayCover ?: ""
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
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            savePlaybackProgress()
            playbackCoordinator.release(videoId)
            player.release()
        } catch (_: Exception) {}
    }
}
