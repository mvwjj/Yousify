package com.mvwj.yousify.data.model

import com.google.gson.annotations.SerializedName

/**
 * ÐœÐ¾Ð´ÐµÐ»ÑŒ Ð¿Ð»ÐµÐ¹Ð»Ð¸ÑÑ‚Ð°
 */
data class Playlist(
    val id: String,
    val name: String,
    val description: String?,
    val uri: String,
    val href: String?,
    val images: List<Image>,
    val owner: Owner?,
    val tracks: Tracks?
)

/**
 * ÐœÐ¾Ð´ÐµÐ»ÑŒ ÑÐ»ÐµÐ¼ÐµÐ½Ñ‚Ð° Ñ‚Ñ€ÐµÐºÐ° Ð² Ð¿Ð»ÐµÐ¹Ð»Ð¸ÑÑ‚Ðµ
 */
data class TrackItem(
    @SerializedName("added_at")
    val addedAt: String?,
    @SerializedName("added_by")
    val addedBy: Owner?,
    @SerializedName("is_local")
    val isLocal: Boolean,
    @SerializedName("track")
    val track: Track? = null,
    @SerializedName("item")
    val item: Track? = null
) {
    val resolvedTrack: Track?
        get() = track ?: item
}

/**
 * ÐœÐ¾Ð´ÐµÐ»ÑŒ Ñ‚Ñ€ÐµÐºÐ°
 */
data class Track(
    val id: String?,
    val name: String?,
    val album: Album?,
    val artists: List<Artist>?,
    @SerializedName("available_markets")
    val availableMarkets: List<String>?,
    @SerializedName("disc_number")
    val discNumber: Int?,
    @SerializedName("duration_ms")
    val durationMs: Long?,
    val explicit: Boolean?,
    @SerializedName("external_ids")
    val externalIds: Map<String, String>?,
    @SerializedName("external_urls")
    val externalUrls: ExternalUrls?,
    val href: String?,
    @SerializedName("is_local")
    val isLocal: Boolean?,
    val popularity: Int?,
    @SerializedName("preview_url")
    val previewUrl: String?,
    @SerializedName("track_number")
    val trackNumber: Int?,
    val type: String?,
    val uri: String?
)

/**
 * ÐœÐ¾Ð´ÐµÐ»ÑŒ Ð°Ð»ÑŒÐ±Ð¾Ð¼Ð°
 */
data class Album(
    val id: String?,
    val name: String?,
    @SerializedName("album_type")
    val albumType: String?,
    val artists: List<Artist>?,
    @SerializedName("available_markets")
    val availableMarkets: List<String>?,
    @SerializedName("external_urls")
    val externalUrls: ExternalUrls?,
    val href: String?,
    val images: List<Image>?,
    @SerializedName("release_date")
    val releaseDate: String?,
    @SerializedName("release_date_precision")
    val releaseDatePrecision: String?,
    @SerializedName("total_tracks")
    val totalTracks: Int?,
    val type: String?,
    val uri: String?
)

/**
 * ÐœÐ¾Ð´ÐµÐ»ÑŒ Ð¸ÑÐ¿Ð¾Ð»Ð½Ð¸Ñ‚ÐµÐ»Ñ
 */
data class Artist(
    val id: String?,
    val name: String?,
    val type: String?,
    val uri: String?,
    val href: String?
)

/**
 * ÐœÐ¾Ð´ÐµÐ»ÑŒ Ð¸Ð·Ð¾Ð±Ñ€Ð°Ð¶ÐµÐ½Ð¸Ñ
 */
data class Image(
    val url: String?,
    val height: Int?,
    val width: Int?
)

/**
 * ÐœÐ¾Ð´ÐµÐ»ÑŒ Ð²Ð»Ð°Ð´ÐµÐ»ÑŒÑ†Ð°
 */
data class Owner(
    val id: String?,
    @SerializedName("display_name")
    val displayName: String?,
    val href: String?,
    val type: String?,
    val uri: String?
)

/**
 * ÐœÐ¾Ð´ÐµÐ»ÑŒ Ð²Ð½ÐµÑˆÐ½Ð¸Ñ… URL
 */
data class ExternalUrls(
    val spotify: String?
)

/**
 * ÐœÐ¾Ð´ÐµÐ»ÑŒ Ð¸Ð½Ñ„Ð¾Ñ€Ð¼Ð°Ñ†Ð¸Ð¸ Ð¾ Ñ‚Ñ€ÐµÐºÐ°Ñ…
 */
data class Tracks(
    val href: String?,
    val total: Int?
)
