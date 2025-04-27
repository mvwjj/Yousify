package com.veshikov.try1.data

import com.veshikov.try1.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.veshikov.try1.utils.Logger

class SpotifyRepository {
    private val apiWrapper = SpotifyApiWrapper.getInstance()

    fun getUserPlaylists(): Flow<List<Playlist>> = flow {
        try {
            val list = apiWrapper.getUserPlaylists() ?: emptyList<Playlist>()
            emit(list)
        } catch (e: Exception) {
            Logger.e("Repo getUserPlaylists", e)
            throw e
        }
    }

    fun getPlaylistTracks(id: String): Flow<List<TrackItem>> = flow {
        try {
            val list = apiWrapper.getPlaylistTracks(id) ?: emptyList<TrackItem>()
            emit(list)
        } catch (e: Exception) {
            Logger.e("Repo getPlaylistTracks", e)
            throw e
        }
    }
}