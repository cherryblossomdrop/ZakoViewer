package moe.zako.viewer.core.cdn

import moe.zako.viewer.core.logger.ZakoLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CdnSmartRouter(
    private val speedTester: CdnSpeedTester = CdnSpeedTester()
) {
    companion object {
        private const val TAG = "CdnSmartRouter"
    }

    private val _nodesFlow = MutableStateFlow<List<CdnNode>>(emptyList())
    val nodesFlow: StateFlow<List<CdnNode>> = _nodesFlow.asStateFlow()

    private val _selectedNode = MutableStateFlow<CdnNode?>(null)
    val selectedNode: StateFlow<CdnNode?> = _selectedNode.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    fun initializeNodes(urls: List<String>) {
        val initialList = urls.mapIndexed { idx, url ->
            CdnNode(
                index = idx,
                name = "线路 ${idx + 1}",
                url = url
            )
        }
        _nodesFlow.value = initialList
        if (initialList.isNotEmpty() && _selectedNode.value == null) {
            _selectedNode.value = initialList.first()
        }
    }

    /**
     * Executes automatic concurrent speed test across all nodes and selects the fastest node
     */
    suspend fun autoSelectFastestNode(urls: List<String>): CdnNode {
        initializeNodes(urls)
        _isTesting.value = true

        ZakoLog.i(TAG, "Executing auto CDN speed-testing for ${urls.size} nodes")
        val testedList = speedTester.testAllNodes(urls) { testedNode ->
            // Update node in place as soon as test completes
            _nodesFlow.value = _nodesFlow.value.map {
                if (it.index == testedNode.index) testedNode else it
            }
        }

        _nodesFlow.value = testedList
        _isTesting.value = false

        val bestNode = testedList.firstOrNull { it.testSuccess } ?: testedList.firstOrNull()
        if (bestNode != null) {
            _selectedNode.value = bestNode
            ZakoLog.i(TAG, "Auto-selected best CDN node: ${bestNode.name} (${bestNode.displayMetric})")
            return bestNode
        }

        val fallback = CdnNode(0, "线路 1", urls.firstOrNull() ?: "")
        _selectedNode.value = fallback
        return fallback
    }

    /**
     * Select a specific CDN node manually
     */
    fun selectNode(node: CdnNode) {
        ZakoLog.i(TAG, "Manually selected CDN node: ${node.name}")
        _selectedNode.value = node
    }
}
