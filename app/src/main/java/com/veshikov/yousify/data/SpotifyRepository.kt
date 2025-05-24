package com.veshikov.yousify.data.repository // Убедитесь, что это правильный пакет

import android.content.Context
import com.veshikov.yousify.data.api.RetrofitClient
import com.veshikov.yousify.data.api.SpotifyApiService // Убедитесь, что импортируется
// Импортируем модели из вашего основного пакета моделей
import com.veshikov.yousify.data.model.Playlist
import com.veshikov.yousify.data.model.TrackItem
// Импортируем классы-обертки для ответов (убедитесь, что они в com.veshikov.yousify.data.model)
import com.veshikov.yousify.data.model.SpotifyPlaylistTracksResponse
import com.veshikov.yousify.data.model.SpotifyUserPlaylistsResponse
import com.veshikov.yousify.utils.Logger // Импортируем ваш Logger
import kotlinx.coroutines.Dispatchers // Импорт для Dispatchers
import kotlinx.coroutines.withContext // Импорт для withContext

class SpotifyRepository(private val applicationContextExt: Context) { // Переименовал параметр, чтобы избежать путаницы
    // Передаем applicationContextExt в getSpotifyService
    private val spotifyService: SpotifyApiService = RetrofitClient.getSpotifyService(applicationContextExt)

    private suspend fun <T> safeApiCall(
        errorMessage: String,
        apiCall: suspend () -> retrofit2.Response<T>
    ): T? {
        return try {
            val response = apiCall()
            if (response.isSuccessful) {
                response.body()
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Logger.e("$errorMessage. Code: ${response.code()}, Message: ${response.message()}, Body: $errorBody")
                null
            }
        } catch (e: Exception) {
            Logger.e("$errorMessage. Exception: ${e.message}", e)
            null
        }
    }

    suspend fun getCurrentUserPlaylists(): List<Playlist>? = withContext(Dispatchers.IO) {
        val allPlaylists = mutableListOf<Playlist>()
        var offset = 0
        var hasMore = true

        // Используем applicationContextExt для SecurePrefs
        if (com.veshikov.yousify.auth.SecurePrefs.accessToken(applicationContextExt) == null) {
            Logger.w("SpotifyRepository: No access token available for getCurrentUserPlaylists.")
            return@withContext null
        }

        while (hasMore) {
            val responseWrapper: SpotifyUserPlaylistsResponse? = safeApiCall("Failed to fetch current user playlists") {
                // Вызываем метод без параметра token, он теперь в Interceptor/Authenticator
                spotifyService.getCurrentUserPlaylists(offset = offset, limit = 50)
            }

            if (responseWrapper != null) {
                allPlaylists.addAll(responseWrapper.items)
                offset += responseWrapper.items.size
                hasMore = responseWrapper.next != null && responseWrapper.items.isNotEmpty()
            } else {
                hasMore = false
                if (offset > 0 && allPlaylists.isEmpty()) return@withContext null
            }
        }
        Logger.i("SpotifyRepository: Fetched ${allPlaylists.size} playlists for current user.")
        return@withContext allPlaylists
    }

    suspend fun getPlaylistTracks(playlistId: String): List<TrackItem>? = withContext(Dispatchers.IO) {
        val allTracks = mutableListOf<TrackItem>()
        var offset = 0
        var hasMore = true

        // Используем applicationContextExt для SecurePrefs
        if (com.veshikov.yousify.auth.SecurePrefs.accessToken(applicationContextExt) == null) {
            Logger.w("SpotifyRepository: No access token available for getPlaylistTracks (playlistId: $playlistId).")
            return@withContext null
        }

        while (hasMore) {
            val responseWrapper: SpotifyPlaylistTracksResponse? = safeApiCall("Failed to fetch tracks for playlist $playlistId") {
                // Вызываем метод без параметра token
                spotifyService.getPlaylistTracks(playlistId = playlistId, offset = offset, limit = 100)
            }

            if (responseWrapper != null) {
                val validTracks = responseWrapper.items.filter { trackItem ->
                    trackItem.track != null && trackItem.track.id != null
                }
                allTracks.addAll(validTracks)

                if (responseWrapper.items.isEmpty() && responseWrapper.next == null) {
                    hasMore = false
                } else {
                    offset += responseWrapper.items.size
                    hasMore = responseWrapper.next != null && responseWrapper.items.isNotEmpty()
                }
            } else {
                hasMore = false
                if (offset > 0 && allTracks.isEmpty()) return@withContext null
            }
        }
        Logger.i("SpotifyRepository: Fetched ${allTracks.size} tracks for playlist $playlistId.")
        return@withContext allTracks
    }
}