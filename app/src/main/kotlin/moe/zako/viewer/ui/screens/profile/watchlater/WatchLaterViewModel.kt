package moe.zako.viewer.ui.screens.profile.watchlater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.zako.viewer.data.local.entities.WatchLaterEntity
import moe.zako.viewer.data.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchLaterUiState(
    val list: List<WatchLaterEntity> = emptyList()
)

class WatchLaterViewModel(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchLaterUiState())
    val uiState: StateFlow<WatchLaterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            historyRepository.getWatchLaterFlow().collect { items ->
                _uiState.update { it.copy(list = items) }
            }
        }
    }

    fun remove(videoId: String) {
        viewModelScope.launch {
            historyRepository.removeFromWatchLater(videoId)
        }
    }
}
