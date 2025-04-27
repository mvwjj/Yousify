package com.veshikov.yousify.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.veshikov.yousify.data.model.TrackEntity
import com.veshikov.yousify.data.model.YousifyDatabase
import com.veshikov.yousify.ui.components.MiniPlayerData
import com.veshikov.yousify.ui.components.MiniPlayerEvents
import com.veshikov.yousify.ui.components.MiniPlayerState
import com.veshikov.yousify.ui.components.MiniPlayerUiState
import com.veshikov.yousify.youtube.SearchEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Контроллер для мини-плеера, связывающий UI с логикой воспроизведения
 */
class MiniPlayerController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onRequirePermissions: () -> Unit
) : MiniPlayerEvents {
    
    private val TAG = "MiniPlayerController"
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val uiState = MiniPlayerUiState()
    private var currentTrack: TrackEntity? = null
    private var audioPlayer: YtAudioPlayer? = null
    
    // Состояния для передачи в UI
    val miniPlayerState get() = uiState.getStateFlow()
    val miniPlayerData get() = uiState.getDataFlow()
    val isExpanded get() = uiState.getExpandedFlow()
    
    init {
        // TODO: Реализовать наблюдение за состоянием плеера, если появится соответствующий API
        // Пока отключено для сборки без ошибок
    }
    
    /**
     * Загружает и воспроизводит трек
     */
    fun playTrack(track: TrackEntity) {
        currentTrack = track
        val trackData = MiniPlayerData(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            imageUrl = null, // Нет album/images в TrackEntity
            hasSponsorBlockSegments = false
        )
        // Показываем мини-плеер в состоянии загрузки
        uiState.startLoading(trackData)
        coroutineScope.launch {
            try {
                val ytResult = findYouTubeVideo(track)
                if (ytResult == null) {
                    uiState.errorPlaying("Track not found on YouTube")
                    return@launch
                }
                // TODO: Проверка SponsorBlock, если реализовано
                uiState.update(newData = trackData)
                // TODO: Запуск воспроизведения через audioPlayer (реализовать корректно)
                // audioPlayer = ...
                // audioPlayer?.playYoutubeAudio(ytResult.videoId, track.title, track.artist)
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing permissions", e)
                uiState.errorPlaying("Missing permissions")
                onRequirePermissions()
            } catch (e: IOException) {
                Log.e(TAG, "Network error", e)
                uiState.errorPlaying("Network error: ${e.localizedMessage}")
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
                uiState.errorPlaying("Error: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * Находит видео YouTube для трека
     */
    private suspend fun findYouTubeVideo(track: TrackEntity): YtTrackResult? {
        // Сначала проверяем кэш
        val db = YousifyDatabase.getInstance(context)
        val cacheDao = db.ytTrackCacheDao()
        
        val cachedEntry = cacheDao.getBySpotifyId(track.id)
        if (cachedEntry != null) {
            Log.d(TAG, "Found cached videoId: ${cachedEntry.videoId} for track: ${track.id}")
            return YtTrackResult(cachedEntry.videoId, cachedEntry.score)
        }
        
        // Если в кэше нет, выполняем поиск
        Log.d(TAG, "Searching for track: ${track.title} by ${track.artist}")
        val result = SearchEngine.findBestYoutube(track, context)
        
        return if (result != null) {
            YtTrackResult(result.videoId, result.score)
        } else {
            null
        }
    }
    
    /**
     * Освобождает ресурсы
     */
    fun release() {
        // TODO: Реализовать освобождение ресурсов
    }
    
    // Реализация MiniPlayerEvents
    
    override fun onPlayPause() {
        // TODO: Реализовать управление воспроизведением
    }
    
    override fun onSkipNext() {
        // TODO: Реализовать навигацию к следующему треку в плейлисте
    }
    
    override fun onSkipPrevious() {
        // TODO: Реализовать навигацию к предыдущему треку в плейлисте
    }
    
    override fun onClose() {
        // TODO: Реализовать остановку воспроизведения
    }
    
    override fun onExpand() {
        uiState.toggleExpanded()
    }
    
    override fun onRetry() {
        currentTrack?.let { playTrack(it) }
    }
    
    /**
     * Результат поиска YouTube-видео
     */
    private data class YtTrackResult(val videoId: String, val score: Float)
}
