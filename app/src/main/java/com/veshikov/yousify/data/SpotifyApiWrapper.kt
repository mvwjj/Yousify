package com.veshikov.yousify.data

import android.content.Context
import com.veshikov.yousify.auth.SecurePrefs
import com.veshikov.yousify.data.model.Playlist
import com.veshikov.yousify.data.model.TrackItem
import com.veshikov.yousify.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SpotifyApiWrapper private constructor(private val applicationContext: Context) {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    private val tokenRefreshMutex = Mutex()
    private var isRefreshingToken = false


    suspend fun initializeApiWithToken(token: String, providedRefreshToken: String? = null): Boolean = withContext(Dispatchers.IO) {
        accessToken = token
        refreshToken = providedRefreshToken ?: SecurePrefs.refreshToken(applicationContext)
        Logger.i("Spotify API initialized. Access token ends with ${accessToken?.takeLast(5)}, Refresh token: ${if (refreshToken != null) "ends with " + refreshToken?.takeLast(5) else "not available"}")
        return@withContext true
    }

    fun getAccessToken(): String? = accessToken

    private suspend fun refreshAccessToken(): Boolean = tokenRefreshMutex.withLock {
        if (isRefreshingToken) {
            Logger.d("SpotifyApiWrapper: Token refresh already in progress, waiting...")
            return false
        }
        isRefreshingToken = true
        Logger.i("SpotifyApiWrapper: Attempting to refresh access token.")
        val currentRefreshToken = refreshToken ?: SecurePrefs.refreshToken(applicationContext)

        if (currentRefreshToken == null) {
            Logger.w("SpotifyApiWrapper: No refresh token available to refresh access token.")
            isRefreshingToken = false
            return false
        }

        return try {
            val url = URL("https://accounts.spotify.com/api/token")
            val postData = "grant_type=refresh_token" +
                    "&refresh_token=" + URLEncoder.encode(currentRefreshToken, "UTF-8") +
                    "&client_id=" + URLEncoder.encode(com.veshikov.yousify.auth.SpotifyAuthManager.CLIENT_ID, "UTF-8")

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            conn.outputStream.use { it.write(postData.toByteArray()) }
            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error stream"
            }

            Logger.i("SpotifyApiWrapper: Refresh token response code: $responseCode, body: $responseBody")

            if (responseCode == 200) {
                val jsonResponse = JSONObject(responseBody)
                val newAccessToken = jsonResponse.getString("access_token")
                val newRefreshToken = jsonResponse.optString("refresh_token", currentRefreshToken)
                val expiresIn = jsonResponse.getLong("expires_in")

                accessToken = newAccessToken
                refreshToken = newRefreshToken
                SecurePrefs.save(newAccessToken, newRefreshToken, expiresIn, applicationContext)
                Logger.i("SpotifyApiWrapper: Access token refreshed successfully. New access token ends with ${newAccessToken.takeLast(5)}")
                true
            } else {
                Logger.e("SpotifyApiWrapper: Failed to refresh access token. Code: $responseCode, Body: $responseBody")
                if (responseCode == 400 || responseCode == 401) {
                    clearTokensAndNotify()
                }
                false
            }
        } catch (e: Exception) {
            Logger.e("SpotifyApiWrapper: Exception during token refresh", e)
            false
        } finally {
            isRefreshingToken = false
        }
    }

    private fun clearTokensAndNotify() {
        accessToken = null
        refreshToken = null
        SecurePrefs.clear(applicationContext)
        Logger.w("SpotifyApiWrapper: All tokens cleared due to refresh failure. User needs to re-authenticate.")
    }

    private suspend fun executeRequest(
        requestFn: suspend (String) -> Pair<String?, Int>
    ): String? {
        val currentToken = accessToken
        if (currentToken == null) {
            Logger.w("SpotifyApiWrapper: No access token available for request.")
            val savedToken = SecurePrefs.accessToken(applicationContext)
            if (savedToken != null) {
                val savedRefreshToken = SecurePrefs.refreshToken(applicationContext)
                initializeApiWithToken(savedToken, savedRefreshToken)
                return executeRequest(requestFn)
            }
            Logger.w("SpotifyApiWrapper: No saved token found in SecurePrefs either.")
            return null
        }

        var (jsonString, responseCode) = requestFn(currentToken)

        if (responseCode == 401) {
            Logger.w("SpotifyApiWrapper: Access token expired (401). Attempting refresh.")
            val refreshed = refreshAccessToken()
            if (refreshed && accessToken != null) {
                Logger.i("SpotifyApiWrapper: Token refreshed. Retrying original request.")
                val (newJsonString, newResponseCode) = requestFn(accessToken!!)
                if (newResponseCode == 401) {
                    Logger.e("SpotifyApiWrapper: Still 401 after token refresh. Clearing tokens.")
                    clearTokensAndNotify()
                    return null
                }
                jsonString = newJsonString
            } else {
                Logger.e("SpotifyApiWrapper: Failed to refresh token or new token is null. Request failed.")
                // Не вызываем clearTokensAndNotify() здесь повторно, т.к. refreshAccessToken мог это сделать
                return null
            }
        }
        return jsonString
    }


    suspend fun getUserPlaylists(): List<Playlist>? = withContext(Dispatchers.IO) {
        Logger.i("getUserPlaylists: called with token=${accessToken?.take(10)}...")
        try {
            val jsonString = executeRequest { token ->
                val url = "https://api.spotify.com/v1/me/playlists?limit=50" // TODO: Handle pagination for playlists
                SpotifyJsonParser.fetchJsonAndCodeFromUrl(url, token)
            }

            Logger.d("SpotifyApiWrapper: playlists raw json: ${jsonString?.take(300)}")
            if (jsonString != null) {
                val playlists = SpotifyJsonParser.parsePlaylistsJson(jsonString)
                Logger.i("SpotifyApiWrapper: playlists parsed count: ${playlists.size}")
                return@withContext playlists
            }
            Logger.w("SpotifyApiWrapper: Failed to get playlists, jsonString is null.")
            return@withContext null
        } catch (e: Exception) {
            Logger.e("SpotifyApiWrapper: Error in getUserPlaylists", e)
            return@withContext null
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<TrackItem>? = withContext(Dispatchers.IO) {
        try {
            Logger.i("SpotifyApiWrapper: Getting tracks for playlist: $playlistId")
            val allTracks = mutableListOf<TrackItem>()
            var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100"
            var page = 0

            while (nextUrl != null) {
                val currentRequestUrl = nextUrl // Захватываем для лямбды
                // ИСПРАВЛЕНО: Проверка currentRequestUrl перед вызовом
                if (currentRequestUrl == null) { // Эта проверка здесь избыточна из-за while (nextUrl != null)
                    Logger.w("SpotifyApiWrapper: nextUrl became null unexpectedly before request for page $page of playlist $playlistId")
                    break
                }

                val jsonString = executeRequest { token ->
                    SpotifyJsonParser.fetchJsonAndCodeFromUrl(currentRequestUrl, token)
                }

                if (jsonString == null) {
                    Logger.e("SpotifyApiWrapper: fetchJsonFromUrl (via executeRequest) returned null for $currentRequestUrl")
                    break // Прерываем цикл, если не удалось получить данные для текущей страницы
                }
                if (jsonString.isBlank() || jsonString.equals("null", ignoreCase = true)) {
                    Logger.w("SpotifyApiWrapper: Received blank or 'null' string JSON for $currentRequestUrl")
                    break
                }

                val jsonObject = org.json.JSONObject(jsonString)
                val tracks = SpotifyJsonParser.parsePlaylistTracksJson(jsonString)
                Logger.i("SpotifyApiWrapper: page $page, got ${tracks.size} tracks for $playlistId")
                allTracks.addAll(tracks)
                // ИСПРАВЛЕНО: nextUrl может быть пустой строкой, что вызовет MalformedURLException.
                // ifBlank { null } обработает этот случай.
                nextUrl = jsonObject.optString("next", null)?.ifBlank { null }
                page++
            }
            Logger.i("SpotifyApiWrapper: total tracks received for $playlistId: ${allTracks.size}")
            return@withContext allTracks

        } catch (e: Exception) {
            Logger.e("SpotifyApiWrapper: Error in getPlaylistTracks for playlist $playlistId", e)
            return@withContext null
        }
    }

    fun release() {
        accessToken = null
        refreshToken = null
        Logger.i("Spotify API released (tokens cleared from memory)")
    }

    companion object {
        @Volatile
        private var INSTANCE: SpotifyApiWrapper? = null

        fun getInstance(context: Context): SpotifyApiWrapper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpotifyApiWrapper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}