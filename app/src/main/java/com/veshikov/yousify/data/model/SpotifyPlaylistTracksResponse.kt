package com.veshikov.yousify.data.model // или com.veshikov.yousify.data.model.spotifyapi

import com.google.gson.annotations.SerializedName
// Убедитесь, что TrackItem импортируется из вашего com.veshikov.yousify.data.model
// import com.veshikov.yousify.data.model.TrackItem

data class SpotifyPlaylistTracksResponse(
    @SerializedName("items") val items: List<TrackItem>,
    @SerializedName("href") val href: String,
    @SerializedName("limit") val limit: Int,
    @SerializedName("next") val next: String?,
    @SerializedName("offset") val offset: Int,
    @SerializedName("previous") val previous: String?,
    @SerializedName("total") val total: Int
)