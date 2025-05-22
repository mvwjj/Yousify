package com.veshikov.yousify.ui // или com.veshikov.yousify.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veshikov.yousify.data.YousifyRepository
import com.veshikov.yousify.data.model.PlaylistEntity
import com.veshikov.yousify.data.model.TrackEntity
import com.veshikov.yousify.ui.components.MiniPlayerUiState // Для доступа к состоянию, если нужно
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class YousifyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YousifyRepository(app)
    private val TAG = "YousifyViewModel"

    // MiniPlayerUiState теперь часть ViewModel, чтобы MainScreen мог его слушать
    // Однако, MiniPlayerController сам управляет своим UIState. ViewModel дает команды.
    // Чтобы избежать путаницы, ViewModel будет хранить только данные, а команды отправлять.
    // MainScreen будет слушать uiState из MiniPlayerController.
    // val miniPlayerUiState = MiniPlayerUiState() // Удаляем это. MainScreen слушает контроллер.

    sealed class PlaybackCommand {
        data class PlayTrack(val track: TrackEntity, val playlistContext: List<TrackEntity>) : PlaybackCommand()
        data class UpdateSponsorBlockStatus(val videoId: String, val hasSegments: Boolean) : PlaybackCommand()
        object RequestPause : PlaybackCommand()
        object RequestResume : PlaybackCommand()
        // object RequestStopPlayback : PlaybackCommand()
    }

    private val _playbackCommand = MutableSharedFlow<PlaybackCommand>(replay = 0)
    val playbackCommand: SharedFlow<PlaybackCommand> = _playbackCommand.asSharedFlow()

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists.asStateFlow()

    private val _tracksForSelectedPlaylist = MutableStateFlow<List<TrackEntity>>(emptyList())
    val tracksForSelectedPlaylist: StateFlow<List<TrackEntity>> = _tracksForSelectedPlaylist.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<List<TrackEntity>>(emptyList())
    val currentPlaylist: StateFlow<List<TrackEntity>> = _currentPlaylist.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(-1)
    // val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    init {
        loadPlaylistsFromDb()
        // Синхронизацию лучше вызывать по действию пользователя или при первом запуске после логина
    }

    fun loadPlaylistsFromDb() {
        viewModelScope.launch {
            repo.getPlaylists().collect { fetchedPlaylists ->
                _playlists.value = fetchedPlaylists
                Log.d(TAG, "Loaded ${fetchedPlaylists.size} playlists from DB.")
            }
        }
    }

    fun selectPlaylistForViewing(playlistId: String) {
        viewModelScope.launch {
            _tracksForSelectedPlaylist.value = emptyList()
            Log.d(TAG, "Fetching tracks for playlist ID: $playlistId for viewing.")
            repo.getTracks(playlistId).collect { tracks ->
                _tracksForSelectedPlaylist.value = tracks
                Log.d(TAG, "Loaded ${tracks.size} tracks for playlist ID: $playlistId for viewing.")
            }
        }
    }

    // Устанавливает контекст и ТЕКУЩИЙ трек, но не отправляет команду Play
    fun setCurrentPlaylistContext(playlist: List<TrackEntity>, currentTrackInContext: TrackEntity) {
        _currentPlaylist.value = playlist
        val index = playlist.indexOfFirst { it.id == currentTrackInContext.id }
        _currentTrackIndex.value = if (index != -1) index else 0 // Default to 0 if not found, though it should be
        updateCurrentTrackFromIndex() // Обновляем _currentTrack
        Log.d(TAG, "Context set. Playlist size: ${playlist.size}, Current track: ${_currentTrack.value?.title} at index ${_currentTrackIndex.value}")
    }

    // Вызывается из UI (например, TracksScreen или TrackDetailScreen) для начала/смены трека
    fun playTrackInContext(trackToPlay: TrackEntity, playlistContext: List<TrackEntity>) {
        setCurrentPlaylistContext(playlistContext, trackToPlay) // Устанавливаем контекст и текущий трек
        viewModelScope.launch {
            Log.d(TAG, "Emitting PlayTrack command for: ${trackToPlay.title}")
            _playbackCommand.emit(PlaybackCommand.PlayTrack(trackToPlay, playlistContext))
        }
    }


    private fun updateCurrentTrackFromIndex() {
        val currentIndex = _currentTrackIndex.value
        val currentList = _currentPlaylist.value
        if (currentList.isNotEmpty() && currentIndex >= 0 && currentIndex < currentList.size) {
            _currentTrack.value = currentList[currentIndex]
        } else if (currentList.isNotEmpty()) {
            _currentTrackIndex.value = 0
            _currentTrack.value = currentList[0]
            Log.w(TAG, "Invalid index, defaulting to first track: ${_currentTrack.value?.title}")
        } else {
            _currentTrack.value = null
            _currentTrackIndex.value = -1
        }
    }

    fun skipToNextTrack() {
        val currentList = _currentPlaylist.value
        if (currentList.isEmpty()) {
            Log.d(TAG, "SkipNext: Playlist is empty.")
            return
        }
        var nextIndex = _currentTrackIndex.value + 1
        if (nextIndex >= currentList.size) {
            nextIndex = 0
        }
        _currentTrackIndex.value = nextIndex
        updateCurrentTrackFromIndex()

        _currentTrack.value?.let { nextTrack ->
            Log.d(TAG, "SkipNext: Emitting PlayTrack command for: ${nextTrack.title}")
            viewModelScope.launch {
                _playbackCommand.emit(PlaybackCommand.PlayTrack(nextTrack, currentList))
            }
        }
    }

    fun skipToPreviousTrack() {
        val currentList = _currentPlaylist.value
        if (currentList.isEmpty()) {
            Log.d(TAG, "SkipPrevious: Playlist is empty.")
            return
        }
        var prevIndex = _currentTrackIndex.value - 1
        if (prevIndex < 0) {
            prevIndex = currentList.size - 1
        }
        _currentTrackIndex.value = prevIndex
        updateCurrentTrackFromIndex()

        _currentTrack.value?.let { prevTrack ->
            Log.d(TAG, "SkipPrevious: Emitting PlayTrack command for: ${prevTrack.title}")
            viewModelScope.launch {
                _playbackCommand.emit(PlaybackCommand.PlayTrack(prevTrack, currentList))
            }
        }
    }

    fun pauseCurrentTrack() {
        viewModelScope.launch {
            _playbackCommand.emit(PlaybackCommand.RequestPause)
        }
    }

    fun resumeCurrentTrack() {
        viewModelScope.launch {
            _playbackCommand.emit(PlaybackCommand.RequestResume)
        }
    }

    fun clearPlaybackState() {
        _currentTrack.value = null
        _currentTrackIndex.value = -1
        // _currentPlaylist.value = emptyList() // Оставляем плейлист для контекста
        Log.d(TAG, "Playback state (current track and index) cleared.")
    }

    fun updateSponsorBlockInfo(videoId: String, hasSegments: Boolean) {
        viewModelScope.launch {
            _playbackCommand.emit(PlaybackCommand.UpdateSponsorBlockStatus(videoId, hasSegments))
        }
    }

    fun syncSpotifyData() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting Spotify data synchronization.")
                // _playlists.value = emptyList() // Не обязательно очищать, Room Flow сам обновит
                repo.syncPlaylistsAndTracks()
                Log.d(TAG, "Spotify data synchronization completed.")
            } catch (e: Exception) {
                Log.e(TAG, "Error during Spotify data synchronization", e)
                // TODO: Показать ошибку пользователю
            }
        }
    }
}