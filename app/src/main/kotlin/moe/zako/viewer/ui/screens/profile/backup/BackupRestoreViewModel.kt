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
import moe.zako.viewer.data.backup.BackupRestoreManager
import moe.zako.viewer.ui.components.ZakoToast
import java.io.File
import java.io.IOException

data class BackupRestoreUiState(
    val includeDownloadedVideos: Boolean = false,
    val isExporting: Boolean = false,
    val isRestoring: Boolean = false,
    val exportResult: ExportResult? = null,
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
            _uiState.update { it.copy(isExporting = true, exportResult = null, restoreStatusMessage = null) }
            try {
                val result = backupRestoreManager.exportBackup(_uiState.value.includeDownloadedVideos)
                _uiState.update { it.copy(isExporting = false, exportResult = result) }
            } catch (e: Exception) {
                ZakoLog.e("BackupRestoreViewModel", "Failed to export backup", e)
                _uiState.update { it.copy(isExporting = false, restoreStatusMessage = "备份导出失败: ${e.message}") }
                ZakoToast.show("备份导出失败: ${e.message}")
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, restoreStatusMessage = null) }
            val tempFile = File(context.cacheDir, "temp_restore_file")
            try {
                if (tempFile.exists()) {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IOException("无法读取所选备份文件")
                }
                val ok = backupRestoreManager.restoreBackup(tempFile)
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreStatusMessage = if (ok) "数据恢复成功！" else "恢复失败，文件损坏或未找到有效备份数据"
                    )
                }
                if (ok) {
                    ZakoToast.show("数据备份已成功恢复")
                } else {
                    ZakoToast.show("恢复失败，未找到有效备份数据")
                }
            } catch (e: Exception) {
                ZakoLog.e("BackupRestoreViewModel", "Restore error", e)
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreStatusMessage = "恢复失败: ${e.message}"
                    )
                }
                ZakoToast.show("恢复失败: ${e.message}")
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
            _uiState.update { it.copy(isRestoring = true, restoreStatusMessage = null) }
            try {
                val ok = backupRestoreManager.restoreBackup(file)
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreStatusMessage = if (ok) "数据恢复成功！" else "恢复失败，文件损坏或未找到有效备份数据"
                    )
                }
                if (ok) {
                    ZakoToast.show("数据备份已成功恢复")
                } else {
                    ZakoToast.show("恢复失败，未找到有效备份数据")
                }
            } catch (e: Exception) {
                ZakoLog.e("BackupRestoreViewModel", "Restore error from file", e)
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreStatusMessage = "恢复失败: ${e.message}"
                    )
                }
                ZakoToast.show("恢复失败: ${e.message}")
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

    fun clearStatus() {
        _uiState.update { it.copy(exportResult = null, restoreStatusMessage = null) }
    }
}
