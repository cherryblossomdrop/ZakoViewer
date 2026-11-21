package moe.zako.viewer.ui.screens.profile.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.core.storage.ExportResult
import moe.zako.viewer.core.storage.PublicStorageHelper
import moe.zako.viewer.data.backup.BackupProgress
import moe.zako.viewer.data.backup.BackupRestoreManager
import moe.zako.viewer.data.backup.RestoreResult
import moe.zako.viewer.ui.components.ZakoToast
import java.io.File
import java.io.IOException

data class BackupRestoreUiState(
    val includeDownloadedVideos: Boolean = false,
    val isExporting: Boolean = false,
    val isRestoring: Boolean = false,
    val progressStage: String = "",
    val progressDetail: String = "",
    val progressPercent: Float? = null,
    val exportResult: ExportResult? = null,
    val restoreResult: RestoreResult? = null,
    val restoreStatusMessage: String? = null
)

class BackupRestoreViewModel(
    private val context: Context,
    private val backupRestoreManager: BackupRestoreManager = BackupRestoreManager(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    init {
        // 清理此前意外残留的临时恢复文件
        try {
            val leftover = File(context.cacheDir, "temp_restore_file")
            if (leftover.exists()) {
                leftover.delete()
            }
        } catch (_: Exception) {}
    }

    fun toggleIncludeVideos(include: Boolean) {
        _uiState.update { it.copy(includeDownloadedVideos = include) }
    }

    fun exportBackup() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExporting = true,
                    progressStage = "正在生成备份…",
                    progressDetail = "准备导出数据...",
                    progressPercent = null,
                    exportResult = null,
                    restoreStatusMessage = null
                )
            }
            try {
                val result = backupRestoreManager.exportBackup(_uiState.value.includeDownloadedVideos) { progress ->
                    _uiState.update {
                        it.copy(
                            progressStage = progress.stage,
                            progressDetail = progress.detail,
                            progressPercent = progress.percent
                        )
                    }
                }
                _uiState.update { it.copy(isExporting = false, exportResult = result) }
            } catch (e: Exception) {
                ZakoLog.e("BackupRestoreViewModel", "Failed to export backup", e)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        restoreStatusMessage = "备份导出失败: ${e.message}"
                    )
                }
                ZakoToast.show("备份导出失败: ${e.message}")
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRestoring = true,
                    progressStage = "正在读取备份文件…",
                    progressDetail = "准备读取...",
                    progressPercent = null,
                    restoreResult = null,
                    restoreStatusMessage = null
                )
            }
            val tempFile = File(context.cacheDir, "temp_restore_file")
            try {
                if (tempFile.exists()) {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
                withContext(Dispatchers.IO) {
                    val totalBytes = try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            pfd.statSize
                        } ?: -1L
                    } catch (_: Exception) {
                        -1L
                    }

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(65536)
                            var bytesRead: Int
                            var copiedBytes = 0L
                            var lastReportTime = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                copiedBytes += bytesRead

                                val now = System.currentTimeMillis()
                                if (now - lastReportTime > 80) {
                                    lastReportTime = now
                                    if (totalBytes > 0) {
                                        val percent = (copiedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                        val copiedStr = PublicStorageHelper.formatBytes(copiedBytes)
                                        val totalStr = PublicStorageHelper.formatBytes(totalBytes)
                                        _uiState.update {
                                            it.copy(
                                                progressStage = "正在读取备份文件…",
                                                progressDetail = "$copiedStr / $totalStr",
                                                progressPercent = percent
                                            )
                                        }
                                    } else {
                                        val copiedStr = PublicStorageHelper.formatBytes(copiedBytes)
                                        _uiState.update {
                                            it.copy(
                                                progressStage = "正在读取备份文件…",
                                                progressDetail = "已读取 $copiedStr",
                                                progressPercent = null
                                            )
                                        }
                                    }
                                }
                            }
                            output.flush()
                        }
                    } ?: throw IOException("无法读取所选备份文件")
                }

                val result = backupRestoreManager.restoreBackup(tempFile) { progress ->
                    _uiState.update {
                        it.copy(
                            progressStage = progress.stage,
                            progressDetail = progress.detail,
                            progressPercent = progress.percent
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreResult = result,
                        restoreStatusMessage = if (result.success) "数据恢复成功！" else (result.errorMessage ?: "恢复失败")
                    )
                }
            } catch (e: Exception) {
                ZakoLog.e("BackupRestoreViewModel", "Restore error", e)
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreResult = RestoreResult(
                            success = false,
                            errorMessage = e.message ?: "未知读取错误"
                        ),
                        restoreStatusMessage = "恢复失败: ${e.message}"
                    )
                }
            } finally {
                try {
                    if (tempFile.exists()) {
                        tempFile.delete()
                        ZakoLog.i("BackupRestoreViewModel", "temp_restore_file cleaned up successfully in finally")
                    }
                } catch (e: Exception) {
                    ZakoLog.w("BackupRestoreViewModel", "Failed to delete temp_restore_file: ${e.message}")
                }
            }
        }
    }

    fun restoreBackup(file: File) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRestoring = true,
                    progressStage = "正在准备恢复…",
                    progressDetail = "读取备份文件",
                    progressPercent = null,
                    restoreResult = null,
                    restoreStatusMessage = null
                )
            }
            try {
                val result = backupRestoreManager.restoreBackup(file) { progress ->
                    _uiState.update {
                        it.copy(
                            progressStage = progress.stage,
                            progressDetail = progress.detail,
                            progressPercent = progress.percent
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreResult = result,
                        restoreStatusMessage = if (result.success) "数据恢复成功！" else (result.errorMessage ?: "恢复失败")
                    )
                }
            } catch (e: Exception) {
                ZakoLog.e("BackupRestoreViewModel", "Restore error from file", e)
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreResult = RestoreResult(
                            success = false,
                            errorMessage = e.message ?: "未知恢复错误"
                        ),
                        restoreStatusMessage = "恢复失败: ${e.message}"
                    )
                }
            } finally {
                try {
                    if (file.name == "temp_restore_file" && file.exists()) {
                        file.delete()
                        ZakoLog.i("BackupRestoreViewModel", "temp_restore_file cleaned up successfully in finally")
                    }
                } catch (e: Exception) {
                    ZakoLog.w("BackupRestoreViewModel", "Failed to delete temp_restore_file: ${e.message}")
                }
            }
        }
    }

    fun dismissRestoreResult() {
        _uiState.update { it.copy(restoreResult = null) }
    }

    fun clearStatus() {
        _uiState.update { it.copy(exportResult = null, restoreResult = null, restoreStatusMessage = null) }
    }
}
