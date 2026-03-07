package com.mvwj.yousify.data.model // Ð¸Ð»Ð¸ com.mvwj.yousify.data.model.spotifyapi, ÐµÑÐ»Ð¸ Ð²Ñ‹ Ð²Ñ‹Ð±Ñ€Ð°Ð»Ð¸ ÐµÐ³Ð¾

import com.google.gson.annotations.SerializedName
// Ð£Ð±ÐµÐ´Ð¸Ñ‚ÐµÑÑŒ, Ñ‡Ñ‚Ð¾ Playlist Ð¸Ð¼Ð¿Ð¾Ñ€Ñ‚Ð¸Ñ€ÑƒÐµÑ‚ÑÑ Ð¸Ð· Ð²Ð°ÑˆÐµÐ³Ð¾ com.mvwj.yousify.data.model
// import com.mvwj.yousify.data.model.Playlist

data class SpotifyUserPlaylistsResponse(
    @SerializedName("items") val items: List<Playlist>,
    @SerializedName("href") val href: String,
    @SerializedName("limit") val limit: Int,
    @SerializedName("next") val next: String?,
    @SerializedName("offset") val offset: Int,
    @SerializedName("previous") val previous: String?,
    @SerializedName("total") val total: Int
)