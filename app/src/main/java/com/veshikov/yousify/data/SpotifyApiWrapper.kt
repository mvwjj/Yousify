package com.veshikov.yousify.data

import com.veshikov.yousify.data.model.Playlist
import com.veshikov.yousify.data.model.TrackItem
import com.veshikov.yousify.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SpotifyApiWrapper private constructor() {
    private var accessToken: String? = null

    /**
     * Инициализация клиента после получения токена
     */
    suspend fun initializeApiWithToken(token: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            accessToken = token
            Logger.i("Spotify API initialized (token only)")
        }.isSuccess
    }

    fun getAccessToken(): String? = accessToken

    suspend fun getUserPlaylists(): List<Playlist>? = withContext(Dispatchers.IO) {
        Logger.i("getUserPlaylists: called with token=${accessToken?.take(10)}...")
        try {
            val token = accessToken
            if (token != null) {
                try {
                    val url = "https://api.spotify.com/v1/me/playlists?limit=50"
                    val jsonString = SpotifyJsonParser.fetchJsonFromUrl(url, token)
                    Logger.i("SpotifyApiWrapper: playlists raw json: $jsonString")
                    if (jsonString != null) {
                        val playlists = SpotifyJsonParser.parsePlaylistsJson(jsonString)
                        Logger.i("SpotifyApiWrapper: playlists parsed: $playlists")
                        if (playlists.isNotEmpty()) {
                            Logger.i("Получено плейлистов через ручной парсинг JSON: ${playlists.size}")
                            return@withContext playlists
                        }
                    }
                } catch (jsonEx: Exception) {
                    Logger.e("Ошибка при ручном парсинге JSON плейлистов", jsonEx)
                }
            }
            Logger.i("Не удалось получить плейлисты ни одним из способов")
            return@withContext emptyList()
        } catch (e: Exception) {
            Logger.e("Ошибка при получении плейлистов", e)
            return@withContext null
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<TrackItem>? = withContext(Dispatchers.IO) {
        try {
            Logger.i("Получаем треки для плейлиста: $playlistId")
            val token = accessToken
            if (token != null) {
                val allTracks = mutableListOf<TrackItem>()
                var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100"
                var page = 0
                while (nextUrl != null) {
                    try {
                        Logger.i("SpotifyApiWrapper: fetch page $page, url=$nextUrl")
                        val jsonString = SpotifyJsonParser.fetchJsonFromUrl(nextUrl, token)
                        if (jsonString == null) {
                            Logger.e("fetchJsonFromUrl вернул null для $nextUrl")
                            break
                        }
                        val jsonObject = org.json.JSONObject(jsonString)
                        val tracks = SpotifyJsonParser.parsePlaylistTracksJson(jsonString)
                        Logger.i("SpotifyApiWrapper: page $page, получено треков: ${tracks.size}")
                        allTracks.addAll(tracks)
                        nextUrl = if (jsonObject.isNull("next")) null else jsonObject.optString("next", null)
                        page++
                    } catch (ex: Exception) {
                        Logger.e("Ошибка при загрузке страницы $page плейлиста $playlistId", ex)
                        break
                    }
                }
                Logger.i("SpotifyApiWrapper: всего получено треков: ${allTracks.size}")
                return@withContext allTracks
            }
            Logger.i("Не удалось получить треки плейлиста: нет токена")
            return@withContext emptyList()
        } catch (e: Exception) {
            Logger.e("Ошибка при получении треков плейлиста", e)
            return@withContext null
        }
    }

    fun release() {
        accessToken = null
        Logger.i("Spotify API released")
    }

    companion object {
        @Volatile
        private var INSTANCE: SpotifyApiWrapper? = null
        
        fun getInstance(): SpotifyApiWrapper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpotifyApiWrapper().also { INSTANCE = it }
            }
        }
    }
}