package com.veshikov.yousify.data

import android.content.Context
import com.veshikov.yousify.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.veshikov.yousify.youtube.NewPipeHelper
import com.veshikov.yousify.youtube.SearchEngine
import androidx.room.withTransaction
// import com.veshikov.yousify.auth.AuthManager // Не используется напрямую здесь
// import com.veshikov.yousify.data.SpotifyApiWrapper // Уже импортирован и используется через getInstance

class YousifyRepository(private val ctx: Context) {
    private val db = YousifyDatabase.getInstance(ctx)
    private val playlistDao = db.playlistDao()
    private val trackDao = db.trackDao()
    private val ytTrackCacheDao = db.ytTrackCacheDao()

    fun getPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
    fun getTracks(playlistId: String): Flow<List<TrackEntity>> = trackDao.getTracksForPlaylist(playlistId)

    suspend fun syncPlaylistsAndTracks() = db.withTransaction {
        // ИСПРАВЛЕНО: Передаем контекст в getInstance
        val apiWrapper = SpotifyApiWrapper.getInstance(ctx)
        val playlists = apiWrapper.getUserPlaylists() ?: emptyList()
        val playlistEntities = playlists.map { playlist ->
            PlaylistEntity(
                id = playlist.id,
                name = playlist.name,
                owner = playlist.owner?.displayName ?: ""
            )
        }
        // Ensure database operations are on the correct dispatcher if needed, though withTransaction handles this.
        val oldPlaylists = playlistDao.getAllPlaylists().first() // .first() может быть блокирующим, убедитесь, что это IO
        val toDelete = oldPlaylists.filter { oldPlaylist -> playlistEntities.none { newPlaylist -> newPlaylist.id == oldPlaylist.id } }

        // Удаляем старые плейлисты ПЕРЕД вставкой новых, чтобы избежать конфликтов при замене,
        // если OnConflictStrategy.REPLACE не обрабатывает каскадное удаление зависимых треков так, как ожидается.
        // Однако, если PlaylistEntity.id это primary key и OnConflictStrategy.REPLACE, то порядок может быть не так важен
        // для самих плейлистов, но важен для треков.
        toDelete.forEach { playlistDao.delete(it) } // Это вызовет каскадное удаление треков, если настроено
        playlistDao.insertAll(playlistEntities)


        val allPlaylistIds = playlistEntities.map { it.id }
        // Получаем существующие треки ПОСЛЕ возможного удаления плейлистов (и их треков через CASCADE)
        // и ПЕРЕД вставкой новых, чтобы правильно определить toDeleteTrackIds
        val existingTracks = trackDao.getTracksForPlaylistIds(allPlaylistIds)
        val newTracks = mutableListOf<TrackEntity>()

        for (playlist in playlists) {
            // ИСПРАВЛЕНО: Передаем контекст в getInstance
            val tracks = apiWrapper.getPlaylistTracks(playlist.id) ?: emptyList()
            tracks.forEach { trackItem ->
                val track = trackItem.track ?: return@forEach
                track.id?.let { trackId -> // Проверяем track.id на null
                    newTracks.add(
                        TrackEntity(
                            id = trackId,
                            playlistId = playlist.id,
                            title = track.name ?: "",
                            artist = track.artists?.joinToString(", ") { artist -> artist.name ?: "" } ?: "",
                            isrc = track.externalIds?.get("isrc"),
                            durationMs = track.durationMs ?: 0L
                        )
                    )
                }
            }
        }
        val newTrackIds = newTracks.map { it.id }.toSet()
        // Определяем треки для удаления: те, что были в БД для актуальных плейлистов, но не пришли из API
        val toDeleteTrackIds = existingTracks.filter { it.id !in newTrackIds && allPlaylistIds.contains(it.playlistId) }.map { it.id }

        if (toDeleteTrackIds.isNotEmpty()) trackDao.deleteByIds(toDeleteTrackIds)
        if (newTracks.isNotEmpty()) trackDao.insertAll(newTracks) // Вставляем только если есть что вставлять
    }

    suspend fun getOrSearchYoutubeId(track: TrackEntity): YtTrackCacheEntity? {
        ytTrackCacheDao.getBySpotifyId(track.id)?.let { return it }
        // ИСПРАВЛЕНО: передаем контекст в SearchEngine.findBestYoutube, если он там нужен для инициализации
        val result = SearchEngine.findBestYoutube(track, ctx) ?: return null
        val audioUrl = NewPipeHelper.getBestAudioUrl(result.videoId)
        val cached   = result.copy(audioUrl = audioUrl)
        ytTrackCacheDao.insert(cached)
        return cached
    }
}