package moe.zako.viewer.data.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.data.source.cosav.CosAvDataSource
import java.util.concurrent.ConcurrentHashMap

/**
 * 视频数据源插件统一注册与调度中心 (DataSourceRegistry)
 */
object DataSourceRegistry {
    private val registeredSources = ConcurrentHashMap<String, VideoDataSource>()

    init {
        // 注册内置 CosAV 数据源插件
        register(CosAvDataSource())
    }

    fun register(source: VideoDataSource) {
        registeredSources[source.id] = source
    }

    fun get(id: String): VideoDataSource? = registeredSources[id]

    fun getAllSources(): List<VideoDataSource> = registeredSources.values.toList()

    /**
     * 获取首个已启用的数据源（Flow 响应式监听）
     */
    fun getActiveDataSourceFlow(settingsRepository: SettingsRepository): Flow<VideoDataSource?> {
        return settingsRepository.enabledDataSourceIds.map { enabledIds ->
            enabledIds.firstNotNullOfOrNull { id -> registeredSources[id] }
        }
    }

    /**
     * 获取当前已启用的主数据源快照
     */
    suspend fun getActiveDataSource(settingsRepository: SettingsRepository): VideoDataSource? {
        val enabledIds = settingsRepository.getEnabledDataSourceIdsSnapshot()
        return enabledIds.firstNotNullOfOrNull { id -> registeredSources[id] }
    }
}
