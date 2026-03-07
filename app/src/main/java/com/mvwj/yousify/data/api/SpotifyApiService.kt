package com.mvwj.yousify.data.api

// Ð˜Ð¼Ð¿Ð¾Ñ€Ñ‚Ñ‹ Ð´Ð»Ñ ÐºÐ»Ð°ÑÑÐ¾Ð² Ð¾Ñ‚Ð²ÐµÑ‚Ð¾Ð² API
// Ð•ÑÐ»Ð¸ Ð²Ñ‹ ÑÐ¾Ð·Ð´Ð°Ð»Ð¸ Ð¸Ñ… Ð² com.mvwj.yousify.data.model:
import com.mvwj.yousify.data.model.SpotifyPlaylistTracksResponse
import com.mvwj.yousify.data.model.SpotifyUserPlaylistsResponse
// Ð•ÑÐ»Ð¸ Ð²Ñ‹ ÑÐ¾Ð·Ð´Ð°Ð»Ð¸ Ð¸Ñ… Ð² com.mvwj.yousify.data.model.spotifyapi:
// import com.mvwj.yousify.data.model.spotifyapi.SpotifyPlaylistTracksResponse
// import com.mvwj.yousify.data.model.spotifyapi.SpotifyUserPlaylistsResponse

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SpotifyApiService {

    @GET("v1/me/playlists")
    suspend fun getCurrentUserPlaylists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "items(id,name,description,images,owner(display_name),tracks(total),uri,href),next,total,limit,offset"
    ): Response<SpotifyUserPlaylistsResponse>

    @GET("v1/users/{user_id}/playlists")
    suspend fun getUserPlaylists(
        @Path("user_id") userId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "items(id,name,description,images,owner(display_name),tracks(total),uri,href),next,total,limit,offset"
    ): Response<SpotifyUserPlaylistsResponse>

    @GET("v1/playlists/{playlist_id}/items")
    suspend fun getPlaylistItems(
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "items(added_at,added_by.id,is_local,item(id,name,album(id,name,images,album_type),artists(id,name),duration_ms,uri,type)),next,total,limit,offset"
    ): Response<SpotifyPlaylistTracksResponse>

    @GET("v1/playlists/{playlist_id}/tracks")
    suspend fun getPlaylistLegacyTracks(
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "items(added_at,added_by.id,is_local,track(id,name,album(id,name,images,album_type),artists(id,name),duration_ms,external_ids,uri)),next,total,limit,offset"
    ): Response<SpotifyPlaylistTracksResponse>
}
