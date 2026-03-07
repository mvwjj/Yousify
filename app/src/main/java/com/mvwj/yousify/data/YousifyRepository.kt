package com.mvwj.yousify.data

import android.content.Context
import androidx.room.withTransaction
import com.mvwj.yousify.data.model.PlaylistEntity
import com.mvwj.yousify.data.model.TrackEntity
import com.mvwj.yousify.data.model.YousifyDatabase
import com.mvwj.yousify.data.model.YtTrackCacheEntity
import com.mvwj.yousify.data.repository.SpotifyRepository
import com.mvwj.yousify.utils.Logger
import com.mvwj.yousify.youtube.NewPipeHelper
import com.mvwj.yousify.youtube.SearchEngine
import kotlinx.coroutines.flow.Flow

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

            val validPlaylists = playlistsFromApi.filterNot { playlist ->
                val shouldSkip = playlist.id.isBlank()
                if (shouldSkip) {
                    Logger.w("YousifyRepository: Skipping playlist with blank ID, name=${playlist.name}.")
                }
                shouldSkip
            }

            val playlistEntities = validPlaylists.map { playlist ->
                PlaylistEntity(
                    id = playlist.id,
                    name = playlist.name,
                    owner = playlist.owner?.displayName.orEmpty(),
                    imageUrl = playlist.images.firstOrNull()?.url
                )
            }

            val syncedTracksByPlaylist = linkedMapOf<String, List<TrackEntity>>()
            validPlaylists.forEach { playlist ->
                val tracksFromApi = spotifyApiRepository.getPlaylistTracks(playlist.id)
                if (tracksFromApi == null) {
                    Logger.w("YousifyRepository: Failed to fetch tracks for playlist ${playlist.name} (${playlist.id}). Keeping existing DB entries.")
                    return@forEach
                }

                val mappedTracks = tracksFromApi.mapIndexedNotNull { index, trackItem ->
                    val track = trackItem.resolvedTrack
                    if (track?.id.isNullOrBlank() || track?.name.isNullOrBlank()) {
                        Logger.w("YousifyRepository: Skipping invalid track in playlist ${playlist.name}.")
                        null
                    } else {
                        TrackEntity(
                            playlistId = playlist.id,
                            position = index,
                            id = track!!.id!!,
                            title = track.name!!,
                            artist = track.artists?.joinToString(", ") { artist -> artist.name ?: "Unknown Artist" }
                                ?: "Various Artists",
                            imageUrl = track.album?.images?.firstOrNull()?.url,
                            isrc = track.externalIds?.get("isrc"),
                            durationMs = track.durationMs ?: 0L
                        )
                    }
                }

                playlist.tracks?.total?.let { expectedTrackCount ->
                    if (mappedTracks.size < expectedTrackCount) {
                        Logger.w("YousifyRepository: Playlist ${playlist.name} expected $expectedTrackCount tracks, fetched ${mappedTracks.size}.")
                    }
                }

                syncedTracksByPlaylist[playlist.id] = mappedTracks
            }

            db.withTransaction {
                val oldPlaylists = playlistDao.getAllPlaylistsSnapshot()
                val removedPlaylists = oldPlaylists.filter { oldPlaylist ->
                    playlistEntities.none { newPlaylist -> newPlaylist.id == oldPlaylist.id }
                }

                if (removedPlaylists.isNotEmpty()) {
                    Logger.i("YousifyRepository: Deleting ${removedPlaylists.size} removed playlists.")
                    removedPlaylists.forEach { playlistDao.delete(it) }
                }

                if (playlistEntities.isNotEmpty()) {
                    playlistDao.insertAll(playlistEntities)
                    Logger.i("YousifyRepository: Inserted/Updated ${playlistEntities.size} playlists.")
                }

                val successfulPlaylistIds = syncedTracksByPlaylist.keys.toList()
                if (successfulPlaylistIds.isNotEmpty()) {
                    trackDao.deleteForPlaylists(successfulPlaylistIds)
                    Logger.i("YousifyRepository: Cleared tracks for ${successfulPlaylistIds.size} successfully synced playlists.")
                }

                val syncedTracks = syncedTracksByPlaylist.values.flatten()
                if (syncedTracks.isNotEmpty()) {
                    trackDao.insertAll(syncedTracks)
                    Logger.i("YousifyRepository: Inserted ${syncedTracks.size} tracks.")
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
