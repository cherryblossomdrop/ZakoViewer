package moe.zako.viewer.core.cdn

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.player.MPVLib
import moe.zako.viewer.core.player.MultiNodeStreamBuffer
import moe.zako.viewer.core.player.PlayerEngine
import moe.zako.viewer.data.repository.SettingsRepository

/**
 * 在线视频起播与 CDN 选线调度协调器 (PlaybackSourceCoordinator)
 *
 * 【跨模块门面契约】：
 * 1. 统筹管理在线流媒体起播线路决策：结合 SettingsRepository 全局数据源配置（自动选优 / 默认节点），提供自动测速选优、默认索引越界回退与优雅降级机制；
 * 2. 统筹 CDN 测速面板全生命周期：实时暴露节点状态流、测速中状态与选中节点流；支持打开面板并发测速、测速完成自动应用最优线路、用户手动选线即刻覆盖（并幂等忽略未决自动回调）；
 * 3. 屏蔽底层流媒体分发拓扑：针对 MPV 原生直接交付真实 CDN 域名 URL，针对 ExoPlayer/系统内核调度 MultiNodeStreamBuffer 组装多节点备选本地代理流；
 * 4. 协程安全与生命周期防线：所有挂起操作严格放行 CancellationException，状态更新与测速回调支持外部注入活性校验校验（如匹配 videoId、页面未退出）。
 */
class PlaybackSourceCoordinator(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val router: CdnSmartRouter = CdnSmartRouter()
) {
    companion object {
        private const val TAG = "PlaybackSourceCoordinator"

        /**
         * 跨模块全局统一视频流媒体请求头
         */
        val DEFAULT_PLAYBACK_HEADERS: Map<String, String> = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14)",
            "Referer" to "https://api.cos-dkop.net/"
        )
    }

    /**
     * 解析后的起播源结果 DTO
     */
    data class ResolvedPlaybackSource(
        val streamUrl: String,
        val selectedNode: CdnNode,
        val headers: Map<String, String> = DEFAULT_PLAYBACK_HEADERS
    )

    // 状态流代理
    val nodesFlow: StateFlow<List<CdnNode>> = router.nodesFlow
    val selectedNode: StateFlow<CdnNode?> = router.selectedNode
    val isTesting: StateFlow<Boolean> = router.isTesting

    private var speedTestJob: Job? = null
    @Volatile private var userManuallySelected: Boolean = false

    /**
     * 预初始化可用节点列表
     */
    fun initializeNodes(lines: List<String>) {
        router.initializeNodes(lines)
    }

    /**
     * 统筹在线视频起播线路决策：
     * 读取全局数据源设置（自动选优开关 + 默认固定 CDN 节点）
     * - 开启自动选优且存在多条线路：并发测速并产出综合评分最佳线路；
     * - 关闭或仅单线路：使用默认配置节点（不可用或越界时优雅回退至首个可用节点）。
     *
     * @param videoId 视频 ID
     * @param lines CDN 线路 URL 列表
     * @param engine 当前播放器引擎 (PlayerEngine)
     * @param onBufferProgress 流代理缓冲进度回调（非 MPV 内核生效）
     * @param isContextActive 外部调用方校验当前页面或任务是否仍有效的闭包
     * @return 解析完成的起播源（含最终流 URL、选中节点及协议头），若上下文已失效或线路为空则返回 null
     */
    suspend fun resolveInitialPlaybackSource(
        videoId: String,
        lines: List<String>,
        engine: PlayerEngine,
        onBufferProgress: ((Long, Long, Float) -> Unit)? = null,
        isContextActive: () -> Boolean = { true }
    ): ResolvedPlaybackSource? {
        if (lines.isEmpty()) return null

        try {
            val autoSelect = settingsRepository.autoSelectCdnFlow.first()
            val defaultIndex = settingsRepository.defaultCdnIndexFlow.first()

            val chosenNode: CdnNode = if (autoSelect && lines.size > 1) {
                ZakoLog.i(TAG, "Auto-selecting fastest CDN node for $videoId among ${lines.size} lines")
                router.autoSelectFastestNode(lines)
            } else {
                router.initializeNodes(lines)
                val targetIdx = defaultIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
                val targetNode = router.nodesFlow.value.getOrNull(targetIdx)
                    ?: router.nodesFlow.value.firstOrNull()
                    ?: CdnNode(0, "线路 1", lines.first())
                router.selectNode(targetNode)
                ZakoLog.i(TAG, "Using default CDN node: ${targetNode.name} (${targetNode.url})")
                targetNode
            }

            if (!isContextActive()) {
                ZakoLog.i(TAG, "Playback source resolution aborted: context inactive for $videoId")
                return null
            }

            val streamUrl = buildStreamUrl(
                videoId = videoId,
                node = chosenNode,
                allLines = lines,
                engine = engine,
                onBufferProgress = onBufferProgress
            )

            return ResolvedPlaybackSource(
                streamUrl = streamUrl,
                selectedNode = chosenNode,
                headers = DEFAULT_PLAYBACK_HEADERS
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ZakoLog.e(TAG, "Error resolving initial playback source for $videoId", e)
            throw e
        }
    }

    /**
     * 根据指定节点及播放内核构建对应的播放流 URL
     * MPV 内核直连选中节点真实域名 URL；非 MPV 启用 MultiNodeStreamBuffer 本地微代理网关。
     */
    suspend fun buildStreamUrl(
        videoId: String,
        node: CdnNode,
        allLines: List<String>,
        engine: PlayerEngine,
        onBufferProgress: ((Long, Long, Float) -> Unit)? = null
    ): String {
        val isMpv = engine == PlayerEngine.MPV || (engine == PlayerEngine.AUTO && MPVLib.isReady())
        return if (isMpv) {
            ZakoLog.i(TAG, "MPV engine ready, directly streaming from CDN node: ${node.name} (${node.url})")
            node.url
        } else {
            ZakoLog.i(TAG, "Initializing multi-node stream buffer for $videoId on ${allLines.size} CDN nodes")
            val streamBuffer = MultiNodeStreamBuffer.getInstance(context)
            if (onBufferProgress != null) {
                streamBuffer.onBufferProgress = onBufferProgress
            }
            val orderedUrls = listOf(node.url) + allLines.filter { it != node.url }
            streamBuffer.startBuffering(
                videoId = videoId,
                cdnUrls = orderedUrls,
                headers = DEFAULT_PLAYBACK_HEADERS
            )
        }
    }

    /**
     * 启动测速面板并发测速会话
     * 自动重置人工选线标记；测速完成若用户未手动选线且上下文仍有效，触发自动切换回调。
     *
     * @param coroutineScope 宿主协程作用域（如 viewModelScope）
     * @param lines 待测速 CDN 线路 URL 列表
     * @param isContextActive 校验上下文活性（如弹窗仍开启、videoId 未变、未退出）
     * @param onAutoSelected 测速完成自动选优回调
     */
    fun startSpeedTestSession(
        coroutineScope: CoroutineScope,
        lines: List<String>,
        isContextActive: () -> Boolean = { true },
        onAutoSelected: ((CdnNode) -> Unit)? = null
    ) {
        userManuallySelected = false
        speedTestJob?.cancel()

        if (lines.isEmpty()) {
            ZakoLog.w(TAG, "Cannot start speed test session: lines is empty")
            return
        }

        if (router.nodesFlow.value.isEmpty() || router.nodesFlow.value.size != lines.size) {
            router.initializeNodes(lines)
        }

        speedTestJob = coroutineScope.launch {
            try {
                val fastest = router.autoSelectFastestNode(lines)
                if (!userManuallySelected && isActive && isContextActive()) {
                    onAutoSelected?.invoke(fastest)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                ZakoLog.w(TAG, "Speed test session failed: ${e.message}")
            }
        }
    }

    /**
     * 用户手动在面板中选择目标节点
     * 标记人工选择标志位，并取消/忽略未完成的自动选优回调。
     */
    fun onUserSelectNode(node: CdnNode): CdnNode {
        userManuallySelected = true
        router.selectNode(node)
        return node
    }

    /**
     * 显式指定选中节点
     */
    fun selectNode(node: CdnNode) {
        router.selectNode(node)
    }

    /**
     * 取消当前进行中的测速会话
     */
    fun cancelSpeedTestSession() {
        speedTestJob?.cancel()
        speedTestJob = null
        router.cancelTesting()
    }

    /**
     * 释放协调器资源并安全停止指定视频的本地流代理
     */
    fun release(videoId: String? = null) {
        cancelSpeedTestSession()
        if (videoId != null) {
            MultiNodeStreamBuffer.stopIfActive(videoId)
        }
    }
}
