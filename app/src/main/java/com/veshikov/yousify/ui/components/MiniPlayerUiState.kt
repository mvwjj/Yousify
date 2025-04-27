package com.veshikov.yousify.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Перечисление возможных состояний мини-плеера
 */
enum class MiniPlayerState {
    HIDDEN,     // Плеер скрыт
    LOADING,    // Загрузка трека
    PLAYING,    // Воспроизведение
    PAUSED,     // Пауза
    ERROR       // Ошибка
}

/**
 * Данные трека для отображения в мини-плеере
 */
data class MiniPlayerData(
    val trackId: String,
    val title: String,
    val artist: String,
    val imageUrl: String? = null,
    val hasSponsorBlockSegments: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Интерфейс для событий взаимодействия с мини-плеером
 */
interface MiniPlayerEvents {
    fun onPlayPause()
    fun onSkipNext()
    fun onSkipPrevious()
    fun onClose()
    fun onExpand()
    fun onRetry()
}

/**
 * Класс для управления состоянием UI мини-плеера
 */
class MiniPlayerUiState {
    // Состояние отображения
    private val _state = MutableStateFlow(MiniPlayerState.HIDDEN)
    
    // Данные трека
    private val _data = MutableStateFlow<MiniPlayerData?>(null)
    
    // Состояние расширения (свернут/развернут)
    private val _isExpanded = MutableStateFlow(false)
    
    // Публичные StateFlow для наблюдения
    fun getStateFlow(): StateFlow<MiniPlayerState> = _state.asStateFlow()
    fun getDataFlow(): StateFlow<MiniPlayerData?> = _data.asStateFlow()
    fun getExpandedFlow(): StateFlow<Boolean> = _isExpanded.asStateFlow()
    
    /**
     * Начать загрузку трека
     */
    fun startLoading(data: MiniPlayerData) {
        _state.value = MiniPlayerState.LOADING
        _data.value = data
    }
    
    /**
     * Обновить информацию о треке
     */
    fun update(
        newState: MiniPlayerState = _state.value,
        newData: MiniPlayerData? = _data.value
    ) {
        _state.value = newState
        newData?.let { _data.value = it }
    }
    
    /**
     * Перейти в состояние воспроизведения
     */
    fun playTrack() {
        _state.value = MiniPlayerState.PLAYING
    }
    
    /**
     * Обновить состояние при ошибке
     */
    fun errorPlaying(errorMessage: String) {
        val currentData = _data.value
        if (currentData != null) {
            _data.value = currentData.copy(errorMessage = errorMessage)
        }
        _state.value = MiniPlayerState.ERROR
    }
    
    /**
     * Скрыть мини-плеер
     */
    fun hide() {
        _state.value = MiniPlayerState.HIDDEN
        _data.value = null
        _isExpanded.value = false
    }
    
    /**
     * Переключить состояние развернутости
     */
    fun toggleExpanded() {
        _isExpanded.value = !_isExpanded.value
    }
    
    /**
     * Установить состояние развернутости
     */
    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }
}
