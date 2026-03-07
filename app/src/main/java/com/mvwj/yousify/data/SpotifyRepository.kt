package com.mvwj.yousify.data.repository

import android.content.Context
import com.mvwj.yousify.data.api.RetrofitClient
import com.mvwj.yousify.data.api.SpotifyApiService
import com.mvwj.yousify.data.model.Playlist
import com.mvwj.yousify.data.model.SpotifyPlaylistTracksResponse
import com.mvwj.yousify.data.model.SpotifyUserPlaylistsResponse
import com.mvwj.yousify.data.model.TrackItem
import com.mvwj.yousify.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.Response

class SpotifyRepository(private val applicationContextExt: Context) {
    private val spotifyService: SpotifyApiService = RetrofitClient.getSpotifyService(applicationContextExt)

    private suspend fun <T> executeApiCall(
        errorMessage: String,
        apiCall: suspend () -> Response<T>
    ): Response<T>? {
        repeat(4) { attempt ->
            try {
                val response = apiCall()
                if (response.isSuccessful) {
                    return response
                }

                if (response.code() == 429 && attempt < 3) {
                    val retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()?.coerceAtLeast(1L)
                        ?: (attempt + 1).toLong()
                    Logger.w("$errorMessage. Spotify rate limit hit, retrying in ${retryAfterSeconds}s.")
                    delay(retryAfterSeconds * 1000)
                    return@repeat
                }

                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Logger.e("$errorMessage. Code: ${response.code()}, Message: ${response.message()}, Body: $errorBody")
                return response
            } catch (e: Exception) {
                Logger.e("$errorMessage. Exception: ${e.message}", e)
                return null
            }
        }

        return null
    }

    private suspend fun <T> safeApiCall(
        errorMessage: String,
        apiCall: suspend () -> Response<T>
    ): T? {
        val response = executeApiCall(errorMessage, apiCall) ?: return null
        return if (response.isSuccessful) response.body() else null
    }

    suspend fun getCurrentUserPlaylists(): List<Playlist>? = withContext(Dispatchers.IO) {
        val allPlaylists = mutableListOf<Playlist>()
        var offset = 0
        var hasMore = true

        if (com.mvwj.yousify.auth.SecurePrefs.accessToken(applicationContextExt) == null) {
            Logger.w("SpotifyRepository: No access token available for getCurrentUserPlaylists.")
            return@withContext null
        }

        while (hasMore) {
            val responseWrapper: SpotifyUserPlaylistsResponse? = safeApiCall("Failed to fetch current user playlists") {
                spotifyService.getCurrentUserPlaylists(offset = offset, limit = 50)
            }

            if (responseWrapper == null) {
                return@withContext null
            }

            allPlaylists.addAll(responseWrapper.items)
            offset += responseWrapper.items.size
            hasMore = responseWrapper.next != null && responseWrapper.items.isNotEmpty()
        }

        Logger.i("SpotifyRepository: Fetched ${allPlaylists.size} playlists for current user.")
        return@withContext allPlaylists
    }

    suspend fun getPlaylistTracks(playlistId: String): List<TrackItem>? = withContext(Dispatchers.IO) {
        val allTracks = mutableListOf<TrackItem>()
        var offset = 0
        var hasMore = true
        var useItemsEndpoint = true

        if (com.mvwj.yousify.auth.SecurePrefs.accessToken(applicationContextExt) == null) {
            Logger.w("SpotifyRepository: No access token available for getPlaylistTracks (playlistId: $playlistId).")
            return@withContext null
        }

        while (hasMore) {
            val response = executeApiCall("Failed to fetch tracks for playlist $playlistId") {
                if (useItemsEndpoint) {
                    spotifyService.getPlaylistItems(playlistId = playlistId, offset = offset, limit = 50)
                } else {
                    spotifyService.getPlaylistLegacyTracks(playlistId = playlistId, offset = offset, limit = 100)
                }
            }

            if (response == null) {
                return@withContext null
            }

            if (!response.isSuccessful) {
                if (useItemsEndpoint && (response.code() == 403 || response.code() == 404)) {
                    Logger.w(
                        "SpotifyRepository: Playlist items endpoint unavailable for $playlistId with code ${response.code()}. Falling back to legacy tracks endpoint."
                    )
                    useItemsEndpoint = false
                    offset = 0
                    allTracks.clear()
                    continue
                }
                return@withContext null
            }

            val responseWrapper = response.body()
            if (responseWrapper == null) {
                Logger.e("SpotifyRepository: Empty response body for playlist $playlistId.")
                return@withContext null
            }

            val validTracks = responseWrapper.items.filter { trackItem ->
                val track = trackItem.resolvedTrack
                track != null && track.id != null && (track.type == null || track.type == "track")
            }
            allTracks.addAll(validTracks)

            if (responseWrapper.items.isEmpty() && responseWrapper.next == null) {
                hasMore = false
            } else {
                offset += responseWrapper.items.size
                hasMore = responseWrapper.next != null && responseWrapper.items.isNotEmpty()
            }
        }

        Logger.i("SpotifyRepository: Fetched ${allTracks.size} tracks for playlist $playlistId.")
        return@withContext allTracks
    }
}
