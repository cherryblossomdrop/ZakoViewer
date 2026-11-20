package moe.zako.viewer.ui.screens.profile.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val longPressSpeed: Float = 2.0f,
    val externalPlayerPkg: String = "",
    val externalDownloaderPkg: String = "",
    val downloadPath: String = "",
    val playerEngine: String = "system",
    val hwDecMode: String = "mediacodec",
    val dynamicColor: Boolean = true,
    val customTheme: String = "#686BDD",
    val coverBlurEnabled: Boolean = true, // 默认开启
    val appIconFollowTheme: Boolean = false, // 图标颜色跟随应用主题色（默认关闭）
    val isLongPressShareUnlocked: Boolean = false,
    val isLongPressShareEnabled: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                listOf(
                    settingsRepository.longPressSpeedFlow,
                    settingsRepository.externalPlayerFlow,
                    settingsRepository.externalDownloaderFlow,
                    settingsRepository.downloadPathFlow,
                    settingsRepository.playerEngineFlow,
                    settingsRepository.hwDecModeFlow,
                    settingsRepository.dynamicColorFlow,
                    settingsRepository.customThemeFlow,
                    settingsRepository.coverBlurFlow,
                    settingsRepository.appIconFollowThemeFlow,
                    settingsRepository.longPressShareUnlockedFlow,
                    settingsRepository.longPressShareEnabledFlow
                )
            ) { args: Array<Any> ->
                SettingsUiState(
                    longPressSpeed = args[0] as Float,
                    externalPlayerPkg = args[1] as String,
                    externalDownloaderPkg = args[2] as String,
                    downloadPath = args[3] as String,
                    playerEngine = args[4] as String,
                    hwDecMode = args[5] as String,
                    dynamicColor = args[6] as Boolean,
                    customTheme = args[7] as String,
                    coverBlurEnabled = args[8] as Boolean,
                    appIconFollowTheme = args[9] as Boolean,
                    isLongPressShareUnlocked = args[10] as Boolean,
                    isLongPressShareEnabled = args[11] as Boolean
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setSpeed(speed: Float) {
        viewModelScope.launch { settingsRepository.setLongPressSpeed(speed) }
    }

    fun setExternalPlayer(pkg: String) {
        viewModelScope.launch { settingsRepository.setExternalPlayer(pkg) }
    }

    fun setExternalDownloader(pkg: String) {
        viewModelScope.launch { settingsRepository.setExternalDownloader(pkg) }
    }

    fun setDownloadPath(path: String) {
        viewModelScope.launch { settingsRepository.setDownloadPath(path) }
    }

    fun setPlayerEngine(engine: String) {
        viewModelScope.launch { settingsRepository.setPlayerEngine(engine) }
    }

    fun setHwDecMode(mode: String) {
        viewModelScope.launch { settingsRepository.setHwDecMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setCustomTheme(theme: String) {
        viewModelScope.launch { settingsRepository.setCustomTheme(theme) }
    }

    fun setCoverBlur(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCoverBlur(enabled) }
    }

    fun setAppIconFollowTheme(follow: Boolean) {
        viewModelScope.launch { settingsRepository.setAppIconFollowTheme(follow) }
    }

    fun setLongPressShareEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLongPressShareEnabled(enabled) }
    }
}
