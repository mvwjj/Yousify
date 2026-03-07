package com.mvwj.yousify.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ÐŸÐµÑ€ÐµÑ‡Ð¸ÑÐ»ÐµÐ½Ð¸Ðµ Ð²Ð¾Ð·Ð¼Ð¾Ð¶Ð½Ñ‹Ñ… ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸Ð¹ Ð¼Ð¸Ð½Ð¸-Ð¿Ð»ÐµÐµÑ€Ð°
 */
enum class MiniPlayerState {
    HIDDEN,     // ÐŸÐ»ÐµÐµÑ€ ÑÐºÑ€Ñ‹Ñ‚
    LOADING,    // Ð—Ð°Ð³Ñ€ÑƒÐ·ÐºÐ° Ñ‚Ñ€ÐµÐºÐ°
    PLAYING,    // Ð’Ð¾ÑÐ¿Ñ€Ð¾Ð¸Ð·Ð²ÐµÐ´ÐµÐ½Ð¸Ðµ
    PAUSED,     // ÐŸÐ°ÑƒÐ·Ð°
    ERROR       // ÐžÑˆÐ¸Ð±ÐºÐ°
}

/**
 * Ð”Ð°Ð½Ð½Ñ‹Ðµ Ñ‚Ñ€ÐµÐºÐ° Ð´Ð»Ñ Ð¾Ñ‚Ð¾Ð±Ñ€Ð°Ð¶ÐµÐ½Ð¸Ñ Ð² Ð¼Ð¸Ð½Ð¸-Ð¿Ð»ÐµÐµÑ€Ðµ
 */
data class MiniPlayerData(
    val trackId: String,
    val title: String,
    val artist: String,
    val imageUrl: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasSponsorBlockSegments: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Ð˜Ð½Ñ‚ÐµÑ€Ñ„ÐµÐ¹Ñ Ð´Ð»Ñ ÑÐ¾Ð±Ñ‹Ñ‚Ð¸Ð¹ Ð²Ð·Ð°Ð¸Ð¼Ð¾Ð´ÐµÐ¹ÑÑ‚Ð²Ð¸Ñ Ñ Ð¼Ð¸Ð½Ð¸-Ð¿Ð»ÐµÐµÑ€Ð¾Ð¼
 */
interface MiniPlayerEvents {
    fun onPlayPause()
    fun onSkipNext()
    fun onSkipPrevious()
    fun onSeekTo(positionMs: Long)
    fun onClose()
    fun onExpand()
    fun onRetry()
}

/**
 * ÐšÐ»Ð°ÑÑ Ð´Ð»Ñ ÑƒÐ¿Ñ€Ð°Ð²Ð»ÐµÐ½Ð¸Ñ ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸ÐµÐ¼ UI Ð¼Ð¸Ð½Ð¸-Ð¿Ð»ÐµÐµÑ€Ð°
 */
class MiniPlayerUiState {
    // Ð¡Ð¾ÑÑ‚Ð¾ÑÐ½Ð¸Ðµ Ð¾Ñ‚Ð¾Ð±Ñ€Ð°Ð¶ÐµÐ½Ð¸Ñ
    private val _state = MutableStateFlow(MiniPlayerState.HIDDEN)
    
    // Ð”Ð°Ð½Ð½Ñ‹Ðµ Ñ‚Ñ€ÐµÐºÐ°
    private val _data = MutableStateFlow<MiniPlayerData?>(null)
    
    // Ð¡Ð¾ÑÑ‚Ð¾ÑÐ½Ð¸Ðµ Ñ€Ð°ÑÑˆÐ¸Ñ€ÐµÐ½Ð¸Ñ (ÑÐ²ÐµÑ€Ð½ÑƒÑ‚/Ñ€Ð°Ð·Ð²ÐµÑ€Ð½ÑƒÑ‚)
    private val _isExpanded = MutableStateFlow(false)
    
    // ÐŸÑƒÐ±Ð»Ð¸Ñ‡Ð½Ñ‹Ðµ StateFlow Ð´Ð»Ñ Ð½Ð°Ð±Ð»ÑŽÐ´ÐµÐ½Ð¸Ñ
    fun getStateFlow(): StateFlow<MiniPlayerState> = _state.asStateFlow()
    fun getDataFlow(): StateFlow<MiniPlayerData?> = _data.asStateFlow()
    fun getExpandedFlow(): StateFlow<Boolean> = _isExpanded.asStateFlow()
    
    /**
     * ÐÐ°Ñ‡Ð°Ñ‚ÑŒ Ð·Ð°Ð³Ñ€ÑƒÐ·ÐºÑƒ Ñ‚Ñ€ÐµÐºÐ°
     */
    fun startLoading(data: MiniPlayerData) {
        _state.value = MiniPlayerState.LOADING
        _data.value = data
    }
    
    /**
     * ÐžÐ±Ð½Ð¾Ð²Ð¸Ñ‚ÑŒ Ð¸Ð½Ñ„Ð¾Ñ€Ð¼Ð°Ñ†Ð¸ÑŽ Ð¾ Ñ‚Ñ€ÐµÐºÐµ
     */
    fun update(
        newState: MiniPlayerState = _state.value,
        newData: MiniPlayerData? = _data.value
    ) {
        _state.value = newState
        newData?.let { _data.value = it }
    }
    
    /**
     * ÐŸÐµÑ€ÐµÐ¹Ñ‚Ð¸ Ð² ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸Ðµ Ð²Ð¾ÑÐ¿Ñ€Ð¾Ð¸Ð·Ð²ÐµÐ´ÐµÐ½Ð¸Ñ
     */
    fun playTrack() {
        _state.value = MiniPlayerState.PLAYING
    }
    
    /**
     * ÐžÐ±Ð½Ð¾Ð²Ð¸Ñ‚ÑŒ ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸Ðµ Ð¿Ñ€Ð¸ Ð¾ÑˆÐ¸Ð±ÐºÐµ
     */
    fun errorPlaying(errorMessage: String) {
        val currentData = _data.value
        if (currentData != null) {
            _data.value = currentData.copy(errorMessage = errorMessage)
        }
        _state.value = MiniPlayerState.ERROR
    }
    
    /**
     * Ð¡ÐºÑ€Ñ‹Ñ‚ÑŒ Ð¼Ð¸Ð½Ð¸-Ð¿Ð»ÐµÐµÑ€
     */
    fun hide() {
        _state.value = MiniPlayerState.HIDDEN
        _data.value = null
        _isExpanded.value = false
    }
    
    /**
     * ÐŸÐµÑ€ÐµÐºÐ»ÑŽÑ‡Ð¸Ñ‚ÑŒ ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸Ðµ Ñ€Ð°Ð·Ð²ÐµÑ€Ð½ÑƒÑ‚Ð¾ÑÑ‚Ð¸
     */
    fun toggleExpanded() {
        _isExpanded.value = !_isExpanded.value
    }
    
    /**
     * Ð£ÑÑ‚Ð°Ð½Ð¾Ð²Ð¸Ñ‚ÑŒ ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸Ðµ Ñ€Ð°Ð·Ð²ÐµÑ€Ð½ÑƒÑ‚Ð¾ÑÑ‚Ð¸
     */
    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }
}
