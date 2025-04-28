package com.veshikov.yousify.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veshikov.yousify.data.YousifyRepository
import com.veshikov.yousify.data.model.PlaylistEntity
import com.veshikov.yousify.data.model.TrackEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class YousifyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YousifyRepository(app)

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists.asStateFlow()

    private val _tracks = MutableStateFlow<List<TrackEntity>>(emptyList())
    val tracks: StateFlow<List<TrackEntity>> = _tracks.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId.asStateFlow()

    fun loadPlaylists() {
        viewModelScope.launch {
            repo.getPlaylists().collect { _playlists.value = it }
        }
    }

    fun selectPlaylist(id: String) {
        _selectedPlaylistId.value = id
        viewModelScope.launch {
            repo.getTracks(id).collect { _tracks.value = it }
        }
    }

    fun sync() {
        viewModelScope.launch {
            repo.syncPlaylistsAndTracks()
        }
    }
}
