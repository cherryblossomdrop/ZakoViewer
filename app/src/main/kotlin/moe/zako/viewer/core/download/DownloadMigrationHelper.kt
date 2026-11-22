package moe.zako.viewer.core.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.storage.StorageConstants
import moe.zako.viewer.data.local.dao.DownloadDao
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.data.repository.SettingsRepository
import java.io.File

/**
 * 离线下载物理存储目录迁移与历史版本平滑升级助手 (DownloadMigrationHelper)
 *
 * 【契约与职责边界】：
 * 1. 物理迁移职责专属收敛：统筹离线下载存储目录变更时的全量与增量物理文件迁移、旧版 NeoViewer 历史公共目录静默平滑兼容；
 * 2. 严格 I/O 安全与流式规范：全流程统一采用 [StorageConstants.DEFAULT_IO_BUFFER_SIZE] (64KB) 流式缓冲写入临时文件，校验文件完整性后原子重命名替换；
 * 3. 存储与权限安全校验：迁移执行前严格校验目标路径可写性与剩余存储空间容量，空间不足或权限缺失时及时熔断并回传明确错误提示；
 * 4. 数据库与配置同步原子性：迁移成功后同步更新 Room [DownloadDao] 的 savePath 与 SettingsRepository 自定义存储路径，源文件安全确认后物理清理；
 * 5. 迁移期间进度感知：通过高阶函数回调实时通知已迁移文件计数、总量与文件名，驱动 UI 进度弹窗与诊断日志输出。
 */
class DownloadMigrationHelper(
    private val downloadDao: DownloadDao,
    private val settingsRepository: SettingsRepository,
    private val onReconcile: () -> Unit,
    private val onPauseDownload: (String) -> Unit
) {
    companion object {
        private const val TAG = "DownloadMigrationHelper"

        /**
         * 校验目录是否具备安全写入权限
         */
        fun canWriteToDir(dir: File): Boolean {
            return try {
                if (!dir.exists() && !dir.mkdirs()) return false
                val testFile = File(dir, ".test_write_${System.currentTimeMillis()}")
                if (testFile.createNewFile()) {
                    testFile.delete()
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * 切换下载目录时安全迁移已有视频文件
     */
    suspend fun migrateDownloadsDirectory(
        newPath: String,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit
    ): MigrationResult = withContext(Dispatchers.IO) {
        if (newPath.isBlank()) {
            return@withContext MigrationResult(0, 0, "目标路径不能为空")
        }
        val targetDir = File(newPath)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return@withContext MigrationResult(0, 0, "无法创建目标目录，请检查文件写入权限")
        }

        // 1. 检查新目录写入权限
        if (!canWriteToDir(targetDir)) {
            return@withContext MigrationResult(0, 0, "新目录无写入权限，请重新授权或选择其他目录")
        }

        // 2. 优雅暂停所有正在下载的任务
        val allDownloads = downloadDao.getAllDownloadsList()
        for (d in allDownloads) {
            if (d.status == DownloadEntity.STATUS_DOWNLOADING) {
                onPauseDownload(d.videoId)
            }
        }

        // 3. 统计待迁移文件
        val targetCanonical = try { targetDir.canonicalPath } catch (_: Exception) { targetDir.absolutePath }
        val existingFiles = allDownloads.mapNotNull { entity ->
            val f = File(entity.savePath)
            if (f.exists() && f.isFile && f.length() > 0L) {
                entity to f
            } else {
                null
            }
        }.filter { (_, f) ->
            val srcCanonical = try { f.parentFile?.canonicalPath } catch (_: Exception) { f.parentFile?.absolutePath }
            srcCanonical != targetCanonical
        }

        if (existingFiles.isEmpty()) {
            settingsRepository.setDownloadPath(newPath)
            onReconcile()
            return@withContext MigrationResult(0, 0, null)
        }

        // 4. 存储空间检查
        val totalBytesNeeded = existingFiles.sumOf { (_, f) -> f.length() }
        val usableSpace = targetDir.usableSpace
        if (usableSpace in 1 until totalBytesNeeded) {
            val neededMb = totalBytesNeeded / (1024 * 1024)
            val usableMb = usableSpace / (1024 * 1024)
            return@withContext MigrationResult(
                0,
                existingFiles.size,
                "新目录可用存储空间不足（可用: ${usableMb}MB，所需: ${neededMb}MB）"
            )
        }

        // 5. 流式迁移 (统一 64KB 缓冲块)
        var successCount = 0
        ZakoLog.i(TAG, "Starting downloads migration to: $newPath. Total files to migrate: ${existingFiles.size}")

        for ((idx, pair) in existingFiles.withIndex()) {
            val (entity, srcFile) = pair
            val currentNumber = idx + 1
            onProgress(currentNumber, existingFiles.size, srcFile.name)
            ZakoLog.i(TAG, "Migrating file [$currentNumber/${existingFiles.size}]: ${srcFile.name} (${srcFile.length()} bytes)")

            val destFile = File(targetDir, srcFile.name)

            // 若新目录已存在同名且大小一致的文件，直接重用并对齐数据库记录
            if (destFile.exists() && destFile.length() == srcFile.length()) {
                downloadDao.updateSavePath(entity.videoId, destFile.absolutePath)
                val deleted = try {
                    srcFile.delete()
                } catch (e: Exception) {
                    ZakoLog.w(TAG, "Exception deleting source file ${srcFile.absolutePath}: ${e.message}")
                    false
                }
                if (!deleted) {
                    ZakoLog.w(TAG, "Failed to delete source file after reuse: ${srcFile.absolutePath}")
                } else {
                    ZakoLog.i(TAG, "Deleted source file successfully (reused): ${srcFile.absolutePath}")
                }
                successCount++
                continue
            }

            val tmpFile = File(targetDir, "${srcFile.name}.migrating_tmp")
            try {
                if (tmpFile.exists()) tmpFile.delete()

                srcFile.inputStream().use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output, StorageConstants.DEFAULT_IO_BUFFER_SIZE)
                        output.flush()
                    }
                }

                if (tmpFile.exists() && tmpFile.length() == srcFile.length()) {
                    if (destFile.exists()) destFile.delete()
                    if (tmpFile.renameTo(destFile)) {
                        downloadDao.updateSavePath(entity.videoId, destFile.absolutePath)
                        val deleted = try {
                            srcFile.delete()
                        } catch (e: Exception) {
                            ZakoLog.w(TAG, "Exception deleting source file ${srcFile.absolutePath}: ${e.message}")
                            false
                        }
                        if (!deleted) {
                            ZakoLog.w(TAG, "Failed to delete source file after migration: ${srcFile.absolutePath}")
                        } else {
                            ZakoLog.i(TAG, "Deleted source file successfully: ${srcFile.absolutePath}")
                        }
                        successCount++
                    } else {
                        ZakoLog.e(TAG, "Failed to rename temp file to ${destFile.name}")
                        tmpFile.delete()
                    }
                } else {
                    ZakoLog.e(TAG, "Migrated file size mismatch for ${srcFile.name}: expected ${srcFile.length()}, got ${tmpFile.length()}")
                    tmpFile.delete()
                }
            } catch (e: Exception) {
                ZakoLog.e(TAG, "Failed migrating file ${srcFile.name}: ${e.message}", e)
                try { if (tmpFile.exists()) tmpFile.delete() } catch (_: Exception) {}
            }
        }

        ZakoLog.i(TAG, "Migration completed: $successCount/${existingFiles.size} files migrated successfully to $newPath")

        // 6. 更新配置并刷新
        settingsRepository.setDownloadPath(newPath)
        onReconcile()

        MigrationResult(successCount, existingFiles.size, null)
    }

    /**
     * 兼容旧版 NeoViewer 目录向 ZakoViewer 标准目录平滑静默迁移
     */
    suspend fun migrateLegacyNeoViewerDirectory() = withContext(Dispatchers.IO) {
        try {
            val oldDir = File("${StorageConstants.DEFAULT_PRIMARY_STORAGE_ROOT}/Download/NeoViewer")
            val newDir = File(StorageConstants.DEFAULT_DOWNLOAD_DIR)

            // 1. 如果旧目录存在且包含文件，迁移文件到新目录
            if (oldDir.exists() && oldDir.isDirectory) {
                if (!newDir.exists()) newDir.mkdirs()
                val oldFiles = oldDir.listFiles() ?: emptyArray()
                for (oldFile in oldFiles) {
                    val newFile = File(newDir, oldFile.name)
                    if (!newFile.exists()) {
                        oldFile.renameTo(newFile)
                    }
                }
                if (oldDir.listFiles()?.isEmpty() == true) {
                    oldDir.delete()
                }
            }

            // 2. 同步更新数据库中保存的旧路径
            val allDownloads = downloadDao.getAllDownloadsList()
            for (item in allDownloads) {
                if (item.savePath.contains("/Download/NeoViewer/")) {
                    val updatedPath = item.savePath.replace("/Download/NeoViewer/", "/Download/ZakoViewer/")
                    downloadDao.insertOrUpdate(item.copy(savePath = updatedPath))
                }
            }

            // 3. 检查并迁移设置中的自定义下载路径
            val currentSettingPath = try { settingsRepository.downloadPathFlow.first() } catch (_: Exception) { "" }
            if (currentSettingPath.contains("/Download/NeoViewer")) {
                val updatedSettingPath = currentSettingPath.replace("/Download/NeoViewer", "/Download/ZakoViewer")
                settingsRepository.setDownloadPath(updatedSettingPath)
            }
        } catch (e: Exception) {
            ZakoLog.w(TAG, "Legacy NeoViewer directory migration warning: ${e.message}")
        }
    }
}

/**
 * 目录迁移执行结果数据模型
 */
data class MigrationResult(
    val successCount: Int,
    val totalCount: Int,
    val errorMessage: String? = null
)
