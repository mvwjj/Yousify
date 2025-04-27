package com.veshikov.try1.data.repository

import com.veshikov.try1.data.api.RetrofitClient
import com.veshikov.try1.data.api.SpotifyService
import com.veshikov.try1.data.model.Album
import com.veshikov.try1.data.model.Artist
import com.veshikov.try1.data.model.Image
import com.veshikov.try1.data.model.Owner
import com.veshikov.try1.data.model.Playlist
import com.veshikov.try1.data.model.Track
import com.veshikov.try1.data.model.TrackItem
import com.veshikov.try1.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Репозиторий для работы с API Spotify
 */
class SpotifyRepository {
    private val spotifyService = RetrofitClient.getSpotifyService()
    
    /**
     * Получает плейлисты текущего пользователя
     * @param token Токен доступа в формате "Bearer YOUR_TOKEN"
     * @return Список плейлистов или null в случае ошибки
     */
    suspend fun getCurrentUserPlaylists(token: String): List<Playlist>? = withContext(Dispatchers.IO) {
        try {
            val playlists = mutableListOf<Playlist>()
            var offset = 0
            var hasMore = true
            
            // Получаем все плейлисты с пагинацией
            while (hasMore) {
                val response = spotifyService.getCurrentUserPlaylists(token, offset = offset)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        playlists.addAll(body)
                        
                        // Проверяем, есть ли еще плейлисты
                        offset += body.size
                        hasMore = offset < body.size
                    } else {
                        Logger.e("Получен пустой ответ при запросе плейлистов")
                        hasMore = false
                    }
                } else {
                    Logger.e("Ошибка при запросе плейлистов: ${response.code()} - ${response.message()}")
                    hasMore = false
                }
            }
            
            Logger.i("Получено ${playlists.size} плейлистов")
            return@withContext playlists
        } catch (e: Exception) {
            Logger.e("Ошибка при получении плейлистов", e)
            return@withContext null
        }
    }
    
    /**
     * Получает плейлисты конкретного пользователя
     * @param userId ID пользователя
     * @param token Токен доступа в формате "Bearer YOUR_TOKEN"
     * @return Список плейлистов или null в случае ошибки
     */
    suspend fun getUserPlaylists(userId: String, token: String): List<Playlist>? = withContext(Dispatchers.IO) {
        try {
            val playlists = mutableListOf<Playlist>()
            var offset = 0
            var hasMore = true
            
            // Получаем все плейлисты с пагинацией
            while (hasMore) {
                val response = spotifyService.getUserPlaylists(userId, token, offset = offset)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        playlists.addAll(body)
                        
                        // Проверяем, есть ли еще плейлисты
                        offset += body.size
                        hasMore = offset < body.size
                    } else {
                        Logger.e("Получен пустой ответ при запросе плейлистов пользователя")
                        hasMore = false
                    }
                } else {
                    Logger.e("Ошибка при запросе плейлистов пользователя: ${response.code()} - ${response.message()}")
                    hasMore = false
                }
            }
            
            Logger.i("Получено ${playlists.size} плейлистов пользователя $userId")
            return@withContext playlists
        } catch (e: Exception) {
            Logger.e("Ошибка при получении плейлистов пользователя", e)
            return@withContext null
        }
    }
    
    /**
     * Получает рекомендуемые плейлисты
     * @param token Токен доступа в формате "Bearer YOUR_TOKEN"
     * @return Список плейлистов или null в случае ошибки
     */
    suspend fun getFeaturedPlaylists(token: String): List<Playlist>? = withContext(Dispatchers.IO) {
        try {
            val response = spotifyService.getFeaturedPlaylists(token)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Logger.i("Получено ${body.size} рекомендуемых плейлистов")
                    return@withContext body
                } else {
                    Logger.e("Получен пустой ответ при запросе рекомендуемых плейлистов")
                }
            } else {
                Logger.e("Ошибка при запросе рекомендуемых плейлистов: ${response.code()} - ${response.message()}")
            }
            
            return@withContext null
        } catch (e: Exception) {
            Logger.e("Ошибка при получении рекомендуемых плейлистов", e)
            return@withContext null
        }
    }
    
    /**
     * Получает треки плейлиста через Retrofit
     * @param playlistId ID плейлиста
     * @param token Токен доступа в формате "Bearer YOUR_TOKEN"
     * @return Список треков или null в случае ошибки
     */
    suspend fun getPlaylistTracks(playlistId: String, token: String): List<TrackItem>? = withContext(Dispatchers.IO) {
        try {
            val tracks = mutableListOf<TrackItem>()
            var offset = 0
            var hasMore = true
            
            // Получаем все треки с пагинацией
            while (hasMore) {
                val response = spotifyService.getPlaylistTracks(playlistId, token, offset = offset)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        tracks.addAll(body)
                        
                        // Проверяем, есть ли еще треки
                        offset += body.size
                        hasMore = offset < body.size
                    } else {
                        Logger.e("Получен пустой ответ при запросе треков")
                        hasMore = false
                    }
                } else {
                    Logger.e("Ошибка при запросе треков: ${response.code()} - ${response.message()}")
                    hasMore = false
                }
            }
            
            Logger.i("Получено ${tracks.size} треков для плейлиста $playlistId")
            return@withContext tracks
        } catch (e: Exception) {
            Logger.e("Ошибка при получении треков плейлиста", e)
            return@withContext null
        }
    }
}