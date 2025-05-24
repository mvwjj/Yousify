package com.veshikov.yousify.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veshikov.yousify.auth.AuthEvents // ИМПОРТ
import com.veshikov.yousify.auth.AuthManager
import com.veshikov.yousify.auth.SecurePrefs
import com.veshikov.yousify.data.YousifyRepository
import com.veshikov.yousify.data.api.RetrofitClient // ИМПОРТ для clearInstance
import com.veshikov.yousify.data.model.PlaylistEntity
import com.veshikov.yousify.data.model.TrackEntity
import com.veshikov.yousify.data.model.YousifyDatabase
import com.veshikov.yousify.player.SponsorBlockDatabase
import com.veshikov.yousify.ui.components.MiniPlayerState
import com.veshikov.yousify.youtube.SearchCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YousifyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YousifyRepository(app)
    private val TAG = "YousifyViewModel"

    sealed class PlaybackCommand {
        data class PlayTrack(val track: TrackEntity, val playlistContext: List<TrackEntity>) : PlaybackCommand()
        data class UpdateSponsorBlockStatus(val videoId: String, val hasSegments: Boolean) : PlaybackCommand()
        object RequestPause : PlaybackCommand()
        object RequestResume : PlaybackCommand()
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

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isLoadingPlaylists = MutableStateFlow(false)
    val isLoadingPlaylists: StateFlow<Boolean> = _isLoadingPlaylists.asStateFlow()

    private val _miniPlayerState = MutableStateFlow(MiniPlayerState.HIDDEN)
    val miniPlayerState: StateFlow<MiniPlayerState> = _miniPlayerState.asStateFlow()

    private val _isUserLoggedIn = MutableStateFlow(SecurePrefs.accessToken(app) != null)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()


    init {
        if (_isUserLoggedIn.value) {
            loadPlaylistsFromDb()
        }
        // Подписываемся на события принудительного выхода
        viewModelScope.launch {
            AuthEvents.forceLogoutEvent.collect {
                Log.w(TAG, "Received force logout event. Logging out user.")
                _isUserLoggedIn.value = false // Обновляем состояние
                _playlists.value = emptyList()
                _tracksForSelectedPlaylist.value = emptyList()
                _currentPlaylist.value = emptyList()
                clearPlaybackState()
                // RetrofitClient.clearInstance() // Вызывается в AuthManager.logout()
                Log.i(TAG, "User forcibly logged out due to invalid token. ViewModel state cleared.")
            }
        }
    }

    fun userJustLoggedIn() {
        _isUserLoggedIn.value = true
        syncSpotifyData() // Запускаем синхронизацию при входе
        loadPlaylistsFromDb()
    }


    fun loadPlaylistsFromDb() {
        viewModelScope.launch {
            if (!_isUserLoggedIn.value) return@launch
            _isLoadingPlaylists.value = true
            try {
                repo.getPlaylists().collectLatest { fetchedPlaylists ->
                    _playlists.value = fetchedPlaylists
                    Log.d(TAG, "Loaded ${fetchedPlaylists.size} playlists from DB.")
                }
            } finally {
                _isLoadingPlaylists.value = false
            }
        }
    }

    fun selectPlaylistForViewing(playlistId: String) {
        viewModelScope.launch {
            _tracksForSelectedPlaylist.value = emptyList()
            Log.d(TAG, "Fetching tracks for playlist ID: $playlistId for viewing.")
            repo.getTracks(playlistId).collectLatest { tracks ->
                _tracksForSelectedPlaylist.value = tracks
                Log.d(TAG, "Loaded ${tracks.size} tracks for playlist ID: $playlistId for viewing.")
            }
        }
    }

    fun setCurrentPlaylistContext(playlist: List<TrackEntity>, currentTrackInContext: TrackEntity) {
        _currentPlaylist.value = playlist
        val index = playlist.indexOfFirst { it.id == currentTrackInContext.id }
        _currentTrackIndex.value = if (index != -1) index else 0
        updateCurrentTrackFromIndex()
        Log.d(TAG, "Context set. Playlist size: ${playlist.size}, Current track: ${_currentTrack.value?.title} at index ${_currentTrackIndex.value}")
    }

    fun playTrackInContext(trackToPlay: TrackEntity, playlistContext: List<TrackEntity>) {
        setCurrentPlaylistContext(playlistContext, trackToPlay)
        viewModelScope.launch {
            Log.d(TAG, "Emitting PlayTrack command for: ${trackToPlay.title}")
            _playbackCommand.emit(PlaybackCommand.PlayTrack(trackToPlay, playlistContext))
            _miniPlayerState.value = MiniPlayerState.LOADING
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
            _miniPlayerState.value = MiniPlayerState.HIDDEN
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
                _miniPlayerState.value = MiniPlayerState.LOADING
            }
        } ?: run {
            _miniPlayerState.value = MiniPlayerState.HIDDEN
        }
    }

    fun skipToPreviousTrack() {
        val currentList = _currentPlaylist.value
        if (currentList.isEmpty()) {
            Log.d(TAG, "SkipPrevious: Playlist is empty.")
            _miniPlayerState.value = MiniPlayerState.HIDDEN
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
                _miniPlayerState.value = MiniPlayerState.LOADING
            }
        } ?: run {
            _miniPlayerState.value = MiniPlayerState.HIDDEN
        }
    }

    fun pauseCurrentTrack() {
        viewModelScope.launch {
            _playbackCommand.emit(PlaybackCommand.RequestPause)
            _miniPlayerState.value = MiniPlayerState.PAUSED
        }
    }

    fun resumeCurrentTrack() {
        viewModelScope.launch {
            _playbackCommand.emit(PlaybackCommand.RequestResume)
            if (_currentTrack.value != null) {
                _miniPlayerState.value = MiniPlayerState.LOADING
            }
        }
    }

    fun clearPlaybackState() {
        _currentTrack.value = null
        _currentTrackIndex.value = -1
        _miniPlayerState.value = MiniPlayerState.HIDDEN
        Log.d(TAG, "Playback state (current track, index, and player state) cleared.")
    }

    fun updateSponsorBlockInfo(videoId: String, hasSegments: Boolean) {
        viewModelScope.launch {
            _playbackCommand.emit(PlaybackCommand.UpdateSponsorBlockStatus(videoId, hasSegments))
        }
    }

    fun syncSpotifyData() {
        if (!_isUserLoggedIn.value) {
            Log.w(TAG, "Cannot sync, user not logged in.")
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            Log.d(TAG, "Starting Spotify data synchronization.")
            try {
                repo.syncPlaylistsAndTracks()
                _playlists.value = repo.getPlaylists().firstOrNull() ?: emptyList()
                Log.d(TAG, "Spotify data synchronization completed. Playlists updated.")
            } catch (e: Exception) {
                Log.e(TAG, "Error during Spotify data synchronization", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    suspend fun clearYouTubeCaches() {
        withContext(Dispatchers.IO) {
            val appCtx = getApplication<Application>()
            YousifyDatabase.getInstance(appCtx).ytTrackCacheDao().clearAll()
            Log.i(TAG, "Cleared YousifyDatabase YT track cache.")

            SearchCacheDatabase.getInstance(appCtx).searchCacheDao().deleteOlderThan(System.currentTimeMillis())
            Log.i(TAG, "Cleared SearchCacheDatabase.")

            Log.w(TAG,"SponsorBlock cache clear not fully implemented (no clearAll in DAO).")
        }
    }

    fun logout() {
        viewModelScope.launch {
            AuthManager.logout(getApplication()) // Это вызовет SecurePrefs.clear() и RetrofitClient.clearInstance()
            // _isUserLoggedIn.value = false // Это значение обновится через AuthEvents, если logout() в AuthManager его вызовет,
            // или просто при следующем чтении SecurePrefs. Но для немедленного UI-отклика лучше явно:
            if (_isUserLoggedIn.value) { // Предотвращаем повторный вызов, если уже вышли через AuthEvents
                _isUserLoggedIn.value = false
            }
            _playlists.value = emptyList()
            _tracksForSelectedPlaylist.value = emptyList()
            _currentPlaylist.value = emptyList()
            clearPlaybackState()
            Log.i(TAG, "User logged out. ViewModel state cleared.")
        }
    }
}