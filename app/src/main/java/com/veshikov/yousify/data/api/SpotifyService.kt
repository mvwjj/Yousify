package com.veshikov.yousify.data.api

import com.veshikov.yousify.data.model.Playlist
import com.veshikov.yousify.data.model.TrackItem
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Интерфейс для работы с API Spotify через Retrofit
 */
interface SpotifyService {
    
    /**
     * Получение плейлистов текущего пользователя
     * @param token Токен доступа
     * @param limit Максимальное количество плейлистов в ответе (макс. 50)
     * @param offset Смещение для пагинации
     */
    @GET("me/playlists")
    suspend fun getCurrentUserPlaylists(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<Playlist>>
    
    /**
     * Получение плейлистов конкретного пользователя
     * @param userId ID пользователя
     * @param token Токен доступа
     * @param limit Максимальное количество плейлистов в ответе (макс. 50)
     * @param offset Смещение для пагинации
     */
    @GET("users/{user_id}/playlists")
    suspend fun getUserPlaylists(
        @Path("user_id") userId: String,
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<Playlist>>
    
    /**
     * Получение треков плейлиста
     * @param playlistId ID плейлиста
     * @param token Токен доступа
     * @param limit Максимальное количество треков в ответе (макс. 100)
     * @param offset Смещение для пагинации
     */
    @GET("playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("playlist_id") playlistId: String,
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<List<TrackItem>>
    
    /**
     * Получение рекомендуемых плейлистов
     * @param token Токен доступа
     * @param limit Максимальное количество плейлистов в ответе (макс. 50)
     * @param offset Смещение для пагинации
     */
    @GET("browse/featured-playlists")
    suspend fun getFeaturedPlaylists(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<Playlist>>
    
    companion object {
        const val BASE_URL = "https://api.spotify.com/v1/"
    }
}