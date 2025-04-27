package com.veshikov.yousify.data

import android.content.Context
import com.veshikov.yousify.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.veshikov.yousify.youtube.NewPipeHelper
import com.veshikov.yousify.youtube.SearchEngine
import androidx.room.withTransaction
import com.veshikov.yousify.auth.AuthManager
import com.veshikov.yousify.data.SpotifyApiWrapper

class YousifyRepository(private val ctx: Context) {
    private val db = YousifyDatabase.getInstance(ctx)
    private val playlistDao = db.playlistDao()
    private val trackDao = db.trackDao()
    private val ytTrackCacheDao = db.ytTrackCacheDao()

    fun getPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
    fun getTracks(playlistId: String): Flow<List<TrackEntity>> = trackDao.getTracksForPlaylist(playlistId)

    suspend fun syncPlaylistsAndTracks() = db.withTransaction {
        // Получаем плейлисты пользователя через ваш SpotifyApiWrapper
        val playlists = SpotifyApiWrapper.getInstance().getUserPlaylists() ?: emptyList()
        val playlistEntities = playlists.map { playlist ->
            PlaylistEntity(
                id = playlist.id,
                name = playlist.name,
                owner = playlist.owner?.displayName ?: ""
            )
        }
        val oldPlaylists = playlistDao.getAllPlaylists().first()
        val toDelete = oldPlaylists.filter { oldPlaylist -> playlistEntities.none { newPlaylist -> newPlaylist.id == oldPlaylist.id } }
        playlistDao.insertAll(playlistEntities)
        toDelete.forEach { playlistDao.delete(it) }

        val allPlaylistIds = playlistEntities.map { it.id }
        val existingTracks = trackDao.getTracksForPlaylistIds(allPlaylistIds)
        val newTracks = mutableListOf<TrackEntity>()
        for (playlist in playlists) {
            val tracks = SpotifyApiWrapper.getInstance().getPlaylistTracks(playlist.id) ?: emptyList()
            tracks.forEach { trackItem ->
                val track = trackItem.track ?: return@forEach
                newTracks.add(
                    TrackEntity(
                        id = track.id ?: return@forEach,
                        playlistId = playlist.id,
                        title = track.name ?: "",
                        artist = track.artists?.joinToString(", ") { artist -> artist.name ?: "" } ?: "",
                        isrc = track.externalIds?.get("isrc"),
                        durationMs = track.durationMs ?: 0L
                    )
                )
            }
        }
        val newTrackIds = newTracks.map { it.id }.toSet()
        val toDeleteTrackIds = existingTracks.filter { it.id !in newTrackIds }.map { it.id }
        if (toDeleteTrackIds.isNotEmpty()) trackDao.deleteByIds(toDeleteTrackIds)
        trackDao.insertAll(newTracks)
    }

    suspend fun getOrSearchYoutubeId(track: TrackEntity): YtTrackCacheEntity? {
        ytTrackCacheDao.getBySpotifyId(track.id)?.let { return it }
        val result = SearchEngine.findBestYoutube(track) ?: return null
        val audioUrl = NewPipeHelper.getBestAudioUrl(result.videoId)
        val cached   = result.copy(audioUrl = audioUrl)
        ytTrackCacheDao.insert(cached)
        return cached
    }
}
