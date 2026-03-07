package com.mvwj.yousify.data.model // Ð¸Ð»Ð¸ com.mvwj.yousify.data.model.spotifyapi

import com.google.gson.annotations.SerializedName
// Ð£Ð±ÐµÐ´Ð¸Ñ‚ÐµÑÑŒ, Ñ‡Ñ‚Ð¾ TrackItem Ð¸Ð¼Ð¿Ð¾Ñ€Ñ‚Ð¸Ñ€ÑƒÐµÑ‚ÑÑ Ð¸Ð· Ð²Ð°ÑˆÐµÐ³Ð¾ com.mvwj.yousify.data.model
// import com.mvwj.yousify.data.model.TrackItem

data class SpotifyPlaylistTracksResponse(
    @SerializedName("items") val items: List<TrackItem>,
    @SerializedName("href") val href: String,
    @SerializedName("limit") val limit: Int,
    @SerializedName("next") val next: String?,
    @SerializedName("offset") val offset: Int,
    @SerializedName("previous") val previous: String?,
    @SerializedName("total") val total: Int
)