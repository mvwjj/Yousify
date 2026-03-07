package com.mvwj.yousify.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvwj.yousify.auth.AuthEvents // Ð˜ÐœÐŸÐžÐ Ð¢
import com.mvwj.yousify.auth.AuthManager
import com.mvwj.yousify.auth.SecurePrefs
import com.mvwj.yousify.data.YousifyRepository
import com.mvwj.yousify.data.api.RetrofitClient // Ð˜ÐœÐŸÐžÐ Ð¢ Ð´Ð»Ñ clearInstance
import com.mvwj.yousify.data.model.PlaylistEntity
import com.mvwj.yousify.data.model.TrackEntity
import com.mvwj.yousify.data.model.YousifyDatabase
import com.mvwj.yousify.player.SponsorBlockDatabase
import com.mvwj.yousify.ui.components.MiniPlayerState
import com.mvwj.yousify.youtube.SearchCacheDatabase
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
    private val _manualQueue = MutableStateFlow<List<TrackEntity>>(emptyList())
    val manualQueue: StateFlow<List<TrackEntity>> = _manualQueue.asStateFlow()
    private val _isCurrentTrackFromManualQueue = MutableStateFlow(false)

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
        // ÐŸÐ¾Ð´Ð¿Ð¸ÑÑ‹Ð²Ð°ÐµÐ¼ÑÑ Ð½Ð° ÑÐ¾Ð±Ñ‹Ñ‚Ð¸Ñ Ð¿Ñ€Ð¸Ð½ÑƒÐ´Ð¸Ñ‚ÐµÐ»ÑŒÐ½Ð¾Ð³Ð¾ Ð²Ñ‹Ñ…Ð¾Ð´Ð°
        viewModelScope.launch {
            AuthEvents.forceLogoutEvent.collect {
                Log.w(TAG, "Received force logout event. Logging out user.")
                _isUserLoggedIn.value = false // ÐžÐ±Ð½Ð¾Ð²Ð»ÑÐµÐ¼ ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸Ðµ
                _playlists.value = emptyList()
                _tracksForSelectedPlaylist.value = emptyList()
                _currentPlaylist.value = emptyList()
                clearPlaybackState()
                // RetrofitClient.clearInstance() // Ð’Ñ‹Ð·Ñ‹Ð²Ð°ÐµÑ‚ÑÑ Ð² AuthManager.logout()
                Log.i(TAG, "User forcibly logged out due to invalid token. ViewModel state cleared.")
            }
        }
    }

    fun userJustLoggedIn() {
        _isUserLoggedIn.value = true
        syncSpotifyData() // Ð—Ð°Ð¿ÑƒÑÐºÐ°ÐµÐ¼ ÑÐ¸Ð½Ñ…Ñ€Ð¾Ð½Ð¸Ð·Ð°Ñ†Ð¸ÑŽ Ð¿Ñ€Ð¸ Ð²Ñ…Ð¾Ð´Ðµ
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
        val index = playlist.indexOfFirst {
            isSameTrack(it, currentTrackInContext)
        }
        _currentTrackIndex.value = if (index != -1) index else 0
        _currentTrack.value = currentTrackInContext
        _isCurrentTrackFromManualQueue.value = false
        Log.d(TAG, "Context set. Playlist size: ${playlist.size}, Current track: ${_currentTrack.value?.title} at index ${_currentTrackIndex.value}")
    }

    fun playTrackInContext(trackToPlay: TrackEntity, playlistContext: List<TrackEntity>) {
        setCurrentPlaylistContext(playlistContext, trackToPlay)
        viewModelScope.launch {
            emitPlayTrack(trackToPlay, playlistContext)
        }
    }

    fun updateCurrentPlaylistContext(playlistContext: List<TrackEntity>) {
        _currentPlaylist.value = playlistContext
        if (_isCurrentTrackFromManualQueue.value) {
            return
        }

        val current = _currentTrack.value
        _currentTrackIndex.value = if (current != null) {
            playlistContext.indexOfFirst { isSameTrack(it, current) }
        } else {
            -1
        }
    }

    fun enqueueTrack(track: TrackEntity): Boolean {
        val current = _currentTrack.value
        val alreadyQueued = _manualQueue.value.any { isSameTrack(it, track) }
        if ((current != null && isSameTrack(current, track)) || alreadyQueued) {
            return false
        }

        _manualQueue.value = _manualQueue.value + track
        return true
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
        val queuedTracks = _manualQueue.value
        if (queuedTracks.isNotEmpty()) {
            val nextQueuedTrack = queuedTracks.first()
            _manualQueue.value = queuedTracks.drop(1)
            _currentTrack.value = nextQueuedTrack
            _isCurrentTrackFromManualQueue.value = true
            viewModelScope.launch {
                emitPlayTrack(nextQueuedTrack, _currentPlaylist.value)
            }
            return
        }

        val currentList = _currentPlaylist.value
        if (currentList.isEmpty()) {
            Log.d(TAG, "SkipNext: Playlist is empty.")
            _miniPlayerState.value = MiniPlayerState.HIDDEN
            return
        }

        var nextIndex = if (_currentTrackIndex.value == -1) 0 else _currentTrackIndex.value + 1
        if (nextIndex >= currentList.size) {
            nextIndex = 0
        }
        _currentTrackIndex.value = nextIndex
        _isCurrentTrackFromManualQueue.value = false
        updateCurrentTrackFromIndex()

        _currentTrack.value?.let { nextTrack ->
            viewModelScope.launch {
                emitPlayTrack(nextTrack, currentList)
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

        if (_isCurrentTrackFromManualQueue.value) {
            _isCurrentTrackFromManualQueue.value = false
            updateCurrentTrackFromIndex()
        } else {
            var prevIndex = if (_currentTrackIndex.value == -1) currentList.lastIndex else _currentTrackIndex.value - 1
            if (prevIndex < 0) {
                prevIndex = currentList.size - 1
            }
            _currentTrackIndex.value = prevIndex
            updateCurrentTrackFromIndex()
        }

        _currentTrack.value?.let { prevTrack ->
            viewModelScope.launch {
                emitPlayTrack(prevTrack, currentList)
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
        _manualQueue.value = emptyList()
        _isCurrentTrackFromManualQueue.value = false
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
            AuthManager.logout(getApplication()) // Ð­Ñ‚Ð¾ Ð²Ñ‹Ð·Ð¾Ð²ÐµÑ‚ SecurePrefs.clear() Ð¸ RetrofitClient.clearInstance()
            // _isUserLoggedIn.value = false // Ð­Ñ‚Ð¾ Ð·Ð½Ð°Ñ‡ÐµÐ½Ð¸Ðµ Ð¾Ð±Ð½Ð¾Ð²Ð¸Ñ‚ÑÑ Ñ‡ÐµÑ€ÐµÐ· AuthEvents, ÐµÑÐ»Ð¸ logout() Ð² AuthManager ÐµÐ³Ð¾ Ð²Ñ‹Ð·Ð¾Ð²ÐµÑ‚,
            // Ð¸Ð»Ð¸ Ð¿Ñ€Ð¾ÑÑ‚Ð¾ Ð¿Ñ€Ð¸ ÑÐ»ÐµÐ´ÑƒÑŽÑ‰ÐµÐ¼ Ñ‡Ñ‚ÐµÐ½Ð¸Ð¸ SecurePrefs. ÐÐ¾ Ð´Ð»Ñ Ð½ÐµÐ¼ÐµÐ´Ð»ÐµÐ½Ð½Ð¾Ð³Ð¾ UI-Ð¾Ñ‚ÐºÐ»Ð¸ÐºÐ° Ð»ÑƒÑ‡ÑˆÐµ ÑÐ²Ð½Ð¾:
            if (_isUserLoggedIn.value) { // ÐŸÑ€ÐµÐ´Ð¾Ñ‚Ð²Ñ€Ð°Ñ‰Ð°ÐµÐ¼ Ð¿Ð¾Ð²Ñ‚Ð¾Ñ€Ð½Ñ‹Ð¹ Ð²Ñ‹Ð·Ð¾Ð², ÐµÑÐ»Ð¸ ÑƒÐ¶Ðµ Ð²Ñ‹ÑˆÐ»Ð¸ Ñ‡ÐµÑ€ÐµÐ· AuthEvents
                _isUserLoggedIn.value = false
            }
            _playlists.value = emptyList()
            _tracksForSelectedPlaylist.value = emptyList()
            _currentPlaylist.value = emptyList()
            _manualQueue.value = emptyList()
            clearPlaybackState()
            Log.i(TAG, "User logged out. ViewModel state cleared.")
        }
    }

    private suspend fun emitPlayTrack(track: TrackEntity, playlistContext: List<TrackEntity>) {
        Log.d(TAG, "Emitting PlayTrack command for: ${track.title}")
        _playbackCommand.emit(PlaybackCommand.PlayTrack(track, playlistContext))
        _miniPlayerState.value = MiniPlayerState.LOADING
    }

    private fun isSameTrack(first: TrackEntity, second: TrackEntity): Boolean {
        return first.playlistId == second.playlistId && first.position == second.position
    }
}
