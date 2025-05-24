package com.veshikov.yousify.data.model // или com.veshikov.yousify.data.model.spotifyapi, если вы выбрали его

import com.google.gson.annotations.SerializedName
// Убедитесь, что Playlist импортируется из вашего com.veshikov.yousify.data.model
// import com.veshikov.yousify.data.model.Playlist

data class SpotifyUserPlaylistsResponse(
    @SerializedName("items") val items: List<Playlist>,
    @SerializedName("href") val href: String,
    @SerializedName("limit") val limit: Int,
    @SerializedName("next") val next: String?,
    @SerializedName("offset") val offset: Int,
    @SerializedName("previous") val previous: String?,
    @SerializedName("total") val total: Int
)