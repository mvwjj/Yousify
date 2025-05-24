package com.veshikov.yousify.data

import android.content.Context
import androidx.room.withTransaction
import com.veshikov.yousify.data.model.*
import com.veshikov.yousify.data.repository.SpotifyRepository
import com.veshikov.yousify.utils.Logger
import com.veshikov.yousify.youtube.NewPipeHelper
import com.veshikov.yousify.youtube.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext // Добавим, если где-то нужен будет

class YousifyRepository(private val ctx: Context) {
    private val db = YousifyDatabase.getInstance(ctx)
    private val playlistDao = db.playlistDao()
    private val trackDao = db.trackDao()
    private val ytTrackCacheDao = db.ytTrackCacheDao()
    private val spotifyApiRepository = SpotifyRepository(ctx.applicationContext)

    fun getPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
    fun getTracks(playlistId: String): Flow<List<TrackEntity>> = trackDao.getTracksForPlaylist(playlistId)

    suspend fun syncPlaylistsAndTracks() {
        Logger.i("YousifyRepository: Starting playlists and tracks synchronization.")
        try {
            val playlistsFromApi = spotifyApiRepository.getCurrentUserPlaylists()

            if (playlistsFromApi == null) {
                Logger.e("YousifyRepository: Failed to fetch playlists from API. Synchronization aborted.")
                return
            }

            db.withTransaction {
                val playlistEntities = playlistsFromApi.mapNotNull { playlist ->
                    if (playlist.id.isBlank()) {
                        Logger.w("syncPlaylistsAndTracks: Skipping playlist with blank ID, Name: ${playlist.name}")
                        null
                    } else {
                        PlaylistEntity(
                            id = playlist.id,
                            name = playlist.name,
                            owner = playlist.owner?.displayName ?: "Unknown Owner"
                        )
                    }
                }
                Logger.d("YousifyRepository: Mapped ${playlistEntities.size} playlists from API.")

                val oldPlaylists = playlistDao.getAllPlaylists().first()
                val toDeletePlaylistEntities = oldPlaylists.filter { oldPl ->
                    playlistEntities.none { newPl -> newPl.id == oldPl.id }
                }

                if (toDeletePlaylistEntities.isNotEmpty()) {
                    Logger.i("YousifyRepository: Deleting ${toDeletePlaylistEntities.size} old playlists.")
                    toDeletePlaylistEntities.forEach { playlistDao.delete(it) }
                }

                if (playlistEntities.isNotEmpty()) {
                    playlistDao.insertAll(playlistEntities)
                    Logger.i("YousifyRepository: Inserted/Updated ${playlistEntities.size} playlists.")
                }

                val allSyncedPlaylistIds = playlistEntities.map { it.id }
                val existingTracksInDb = trackDao.getTracksForPlaylistIds(allSyncedPlaylistIds)
                val newOrUpdatedTracks = mutableListOf<TrackEntity>()

                // Параллельное получение треков для всех плейлистов
                // Используем withContext(Dispatchers.IO) для самой операции маппинга с async
                val tracksFetchDeferred = withContext(Dispatchers.IO) {
                    playlistEntities.map { playlistEntity ->
                        async { // async запускает корутину и возвращает Deferred
                            spotifyApiRepository.getPlaylistTracks(playlistEntity.id) to playlistEntity // Возвращаем пару (список треков, плейлист)
                        }
                    }
                }

                val tracksResults = tracksFetchDeferred.awaitAll() // Ожидаем завершения всех запросов

                for ((tracksFromApiForPlaylist, playlistEntity) in tracksResults) {
                    if (tracksFromApiForPlaylist == null) {
                        Logger.w("YousifyRepository: Failed to fetch tracks for playlist ${playlistEntity.name} (ID: ${playlistEntity.id}). Skipping tracks for this playlist.")
                        continue
                    }

                    tracksFromApiForPlaylist.forEach { trackItem ->
                        val track = trackItem.track
                        if (track != null && track.id != null && track.name != null) {
                            newOrUpdatedTracks.add(
                                TrackEntity(
                                    id = track.id,
                                    playlistId = playlistEntity.id,
                                    title = track.name,
                                    artist = track.artists?.joinToString(", ") { artist -> artist.name ?: "Unknown Artist" } ?: "Various Artists",
                                    isrc = track.externalIds?.get("isrc"),
                                    durationMs = track.durationMs ?: 0L
                                )
                            )
                        } else {
                            Logger.w("YousifyRepository: Skipping a track in playlist ${playlistEntity.name} due to missing track data (ID: ${track?.id}, Name: ${track?.name}).")
                        }
                    }
                }
                Logger.d("YousifyRepository: Fetched ${newOrUpdatedTracks.size} tracks in total from API for synced playlists.")

                val newOrUpdatedTrackIds = newOrUpdatedTracks.map { it.id }.toSet()
                val toDeleteTrackEntities = existingTracksInDb.filter { existingTrack ->
                    allSyncedPlaylistIds.contains(existingTrack.playlistId) && existingTrack.id !in newOrUpdatedTrackIds
                }

                if (toDeleteTrackEntities.isNotEmpty()) {
                    trackDao.deleteByIds(toDeleteTrackEntities.map { it.id })
                    Logger.i("YousifyRepository: Deleted ${toDeleteTrackEntities.size} old tracks.")
                }

                if (newOrUpdatedTracks.isNotEmpty()) {
                    trackDao.insertAll(newOrUpdatedTracks)
                    Logger.i("YousifyRepository: Inserted/Updated ${newOrUpdatedTracks.size} tracks.")
                }
            }
            Logger.i("YousifyRepository: Synchronization complete.")
        } catch (e: Exception) {
            Logger.e("YousifyRepository: Error during syncPlaylistsAndTracks", e)
        }
    }

    suspend fun getOrSearchYoutubeId(track: TrackEntity): YtTrackCacheEntity? {
        val cachedFromDb = ytTrackCacheDao.getBySpotifyId(track.id)
        if (cachedFromDb != null && cachedFromDb.videoId.isNotBlank()) {
            if (cachedFromDb.audioUrl.isNullOrBlank()) {
                val audioUrl = NewPipeHelper.getBestAudioUrl(cachedFromDb.videoId)
                if (!audioUrl.isNullOrBlank()) {
                    val updatedEntry = cachedFromDb.copy(audioUrl = audioUrl, timestamp = System.currentTimeMillis())
                    ytTrackCacheDao.insert(updatedEntry)
                    Logger.i("YousifyRepository: Updated cached entry for ${track.title} with new audioUrl.")
                    return updatedEntry
                }
            }
            Logger.d("YousifyRepository: Found cached YouTube ID ${cachedFromDb.videoId} for track ${track.title}")
            return cachedFromDb
        }

        Logger.i("YousifyRepository: No valid cache for ${track.title}, searching on YouTube...")
        val searchResultEntity = SearchEngine.findBestYoutube(track, ctx)

        if (searchResultEntity == null || searchResultEntity.videoId.isBlank()) {
            Logger.w("YousifyRepository: YouTube search returned no result for ${track.title}")
            return null
        }
        Logger.i("YousifyRepository: YouTube search found ${searchResultEntity.videoId} for ${track.title}. Fetching audio URL...")

        val audioUrl = NewPipeHelper.getBestAudioUrl(searchResultEntity.videoId)
        val entityToCache = searchResultEntity.copy(
            spotifyId = track.id,
            audioUrl = audioUrl,
            timestamp = System.currentTimeMillis()
        )
        ytTrackCacheDao.insert(entityToCache)
        Logger.i("YousifyRepository: Cached YouTube ID ${entityToCache.videoId} with audio URL for ${track.title}")
        return entityToCache
    }
}