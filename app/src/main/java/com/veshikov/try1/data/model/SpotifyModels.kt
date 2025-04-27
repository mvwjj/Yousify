package com.veshikov.try1.data.model

import com.google.gson.annotations.SerializedName

/**
 * Модель плейлиста
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
 * Модель элемента трека в плейлисте
 */
data class TrackItem(
    @SerializedName("added_at")
    val addedAt: String?,
    @SerializedName("added_by")
    val addedBy: Owner?,
    @SerializedName("is_local")
    val isLocal: Boolean,
    val track: Track?
)

/**
 * Модель трека
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
 * Модель альбома
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
 * Модель исполнителя
 */
data class Artist(
    val id: String?,
    val name: String?,
    val type: String?,
    val uri: String?,
    val href: String?
)

/**
 * Модель изображения
 */
data class Image(
    val url: String?,
    val height: Int?,
    val width: Int?
)

/**
 * Модель владельца
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
 * Модель внешних URL
 */
data class ExternalUrls(
    val spotify: String?
)

/**
 * Модель информации о треках
 */
data class Tracks(
    val href: String?,
    val total: Int?
)