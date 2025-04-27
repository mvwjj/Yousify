package com.veshikov.try1.data

import com.veshikov.try1.data.model.Playlist
import com.veshikov.try1.data.model.TrackItem
import com.veshikov.try1.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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
                try {
                    val url = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100"
                    val jsonString = SpotifyJsonParser.fetchJsonFromUrl(url, token)
                    if (jsonString != null) {
                        val tracks = SpotifyJsonParser.parsePlaylistTracksJson(jsonString)
                        if (tracks.isNotEmpty()) {
                            Logger.i("Получено треков через ручной парсинг JSON: ${tracks.size}")
                            return@withContext tracks
                        }
                    }
                } catch (jsonEx: Exception) {
                    Logger.e("Ошибка при ручном парсинге JSON треков", jsonEx)
                }
            }
            Logger.i("Не удалось получить треки плейлиста")
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