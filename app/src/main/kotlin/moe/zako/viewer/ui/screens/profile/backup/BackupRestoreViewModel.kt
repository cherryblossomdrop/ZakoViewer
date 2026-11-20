package moe.zako.viewer.ui.screens.profile.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.data.backup.BackupRestoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class BackupRestoreUiState(
    val includeDownloadedVideos: Boolean = false,
    val isExporting: Boolean = false,
    val isRestoring: Boolean = false,
    val exportSuccessFile: File? = null,
    val restoreStatusMessage: String? = null
)

class BackupRestoreViewModel(
    private val context: Context,
    private val backupRestoreManager: BackupRestoreManager = BackupRestoreManager(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    fun toggleIncludeVideos(include: Boolean) {
        _uiState.update { it.copy(includeDownloadedVideos = include) }
    }

    fun exportBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportSuccessFile = null, restoreStatusMessage = null) }
            try {
                val file = backupRestoreManager.exportBackup(_uiState.value.includeDownloadedVideos)
                _uiState.update { it.copy(isExporting = false, exportSuccessFile = file) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, restoreStatusMessage = "备份导出失败: ${e.message}") }
            }
        }
    }

    fun restoreBackup(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, restoreStatusMessage = null) }
            val ok = backupRestoreManager.restoreBackup(file)
            _uiState.update {
                it.copy(
                    isRestoring = false,
                    restoreStatusMessage = if (ok) "数据恢复成功！" else "恢复失败，文件损坏或格式不兼容"
                )
            }
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(exportSuccessFile = null, restoreStatusMessage = null) }
    }
}
