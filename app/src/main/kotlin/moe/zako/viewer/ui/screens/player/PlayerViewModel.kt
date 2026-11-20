package moe.zako.viewer.ui.screens.player

import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.core.cdn.CdnNode
import moe.zako.viewer.core.cdn.CdnSmartRouter
import moe.zako.viewer.core.external.ExternalLauncher
import moe.zako.viewer.core.logger.NeoLog
import moe.zako.viewer.core.player.AspectRatioMode
import moe.zako.viewer.core.player.HwDecMode
import moe.zako.viewer.core.player.MultiNodeStreamBuffer
import moe.zako.viewer.core.player.NeoPlayer
import moe.zako.viewer.core.player.PlayerEngine
import moe.zako.viewer.core.player.VideoPlayerState
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.repository.FavoriteRepository
import moe.zako.viewer.data.repository.HistoryRepository
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.data.source.cosav.CosApiClient
import moe.zako.viewer.data.source.cosav.CosVideoItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    val player: NeoPlayer,
    private val apiClient: CosApiClient = CosApiClient.instance,
    private val cdnSmartRouter: CdnSmartRouter = CdnSmartRouter(),
    private val favoriteRepository: FavoriteRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

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
                    else -> HwDecMode.MEDIACODEC
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
        viewModelScope.launch {
            try {
                val detail = apiClient.getVideoDetail(videoId)
                _uiState.update { it.copy(video = detail) }

                detail?.let { vid ->
                    val lines = vid.allVideoLines.ifEmpty { vid.allMp4Lines }
                    if (lines.isNotEmpty()) {
                        NeoLog.i(TAG, "Initializing multi-node streaming buffer on ${lines.size} CDN nodes")
                        val streamBuffer = MultiNodeStreamBuffer.getInstance(player.context)
                        streamBuffer.onBufferProgress = { bufferedMs, _, _ ->
                            player.updateBufferedPosition(bufferedMs)
                        }

                        // Start continuous multi-node background buffering to video end
                        val streamUrl = streamBuffer.startBuffering(
                            videoId = vid.id,
                            cdnUrls = lines,
                            headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Linux; Android 14)",
                                "Referer" to "https://api.cos-dkop.net/"
                            )
                        )

                        // Select best node for fallback / display metadata
                        cdnSmartRouter.autoSelectFastestNode(lines)

                        player.loadVideo(
                            url = streamUrl,
                            title = vid.title,
                            headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Linux; Android 14)",
                                "Referer" to "https://api.cos-dkop.net/"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                NeoLog.e(TAG, "Error starting playback for $videoId", e)
            }
        }
    }

    // --- Controls and Menu ---
    fun toggleControls() {
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
        _uiState.update { it.copy(showMoreMenu = show) }
    }

    fun showEngineDialog(show: Boolean) {
        _uiState.update { it.copy(showEngineDialog = show) }
    }

    fun switchEngine(engine: PlayerEngine) {
        player.setEngine(engine)
        viewModelScope.launch {
            val key = when (engine) {
                PlayerEngine.MPV -> "mpv"
                PlayerEngine.SYSTEM -> "system"
                PlayerEngine.AUTO -> "auto"
            }
            settingsRepository.setPlayerEngine(key)
        }
        showEngineDialog(false)
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

    // --- Gesture Actions ---
    fun onDoubleTapCenter() {
        if (_uiState.value.playerState.isLocked) return
        player.togglePlayPause()
        _uiState.update { it.copy(showPlayPauseIndicator = true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _uiState.update { it.copy(showPlayPauseIndicator = false) }
        }
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
        viewModelScope.launch {
            _uiState.update { it.copy(showMoreMenu = false) }
            val lines = _uiState.value.video?.allVideoLines ?: emptyList()
            if (lines.isNotEmpty()) {
                cdnSmartRouter.autoSelectFastestNode(lines)
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

    override fun onCleared() {
        super.onCleared()
        MultiNodeStreamBuffer.getInstance(player.context).stopBuffering(videoId)
        player.release()
    }
}
