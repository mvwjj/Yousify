package com.mvwj.yousify.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Ð¡ÑƒÑ‰Ð½Ð¾ÑÑ‚ÑŒ Ð´Ð»Ñ ÐºÑÑˆÐ¸Ñ€Ð¾Ð²Ð°Ð½Ð¸Ñ ÑÐ¾Ð¾Ñ‚Ð²ÐµÑ‚ÑÑ‚Ð²Ð¸Ñ Ð¼ÐµÐ¶Ð´Ñƒ Ñ‚Ñ€ÐµÐºÐ°Ð¼Ð¸ Spotify Ð¸ Ð²Ð¸Ð´ÐµÐ¾ YouTube.
 * Ð¡Ð¾Ð´ÐµÑ€Ð¶Ð¸Ñ‚:
 * - spotifyId (Ð¿ÐµÑ€Ð²Ð¸Ñ‡Ð½Ñ‹Ð¹ ÐºÐ»ÑŽÑ‡)
 * - videoId (Ð¸Ð½Ð´ÐµÐºÑÐ¸Ñ€Ð¾Ð²Ð°Ð½ Ð´Ð»Ñ Ð±Ñ‹ÑÑ‚Ñ€Ð¾Ð³Ð¾ Ð¿Ð¾Ð¸ÑÐºÐ°)
 * - score (Ð¾Ñ†ÐµÐ½ÐºÐ° ÑÐ¾Ð¾Ñ‚Ð²ÐµÑ‚ÑÑ‚Ð²Ð¸Ñ)
 * - isrc (Ð¼ÐµÐ¶Ð´ÑƒÐ½Ð°Ñ€Ð¾Ð´Ð½Ñ‹Ð¹ ÐºÐ¾Ð´ Ð·Ð°Ð¿Ð¸ÑÐ¸)
 * - timestamp (Ð²Ñ€ÐµÐ¼Ñ ÑÐ¾Ð·Ð´Ð°Ð½Ð¸Ñ Ð·Ð°Ð¿Ð¸ÑÐ¸ Ð´Ð»Ñ Ð°Ð²Ñ‚Ð¾Ð¼Ð°Ñ‚Ð¸Ñ‡ÐµÑÐºÐ¾Ð¹ Ð¾Ñ‡Ð¸ÑÑ‚ÐºÐ¸)
 * - audioUrl (Ð¾Ð¿Ñ†Ð¸Ð¾Ð½Ð°Ð»ÑŒÐ½Ð¾ - Ð¿Ñ€ÑÐ¼Ð°Ñ ÑÑÑ‹Ð»ÐºÐ° Ð½Ð° Ð°ÑƒÐ´Ð¸Ð¾, ÐµÑÐ»Ð¸ Ñ€Ð°Ð·Ñ€ÐµÑˆÐµÐ½Ð°)
 */
@Entity(
    tableName = "yt_track_cache", 
    indices = [
        Index("videoId"),
        Index("timestamp"),
        Index("isrc")
    ]
)
data class YtTrackCacheEntity(
    @PrimaryKey val spotifyId: String,
    val videoId: String,
    val score: Float,
    val isrc: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val audioUrl: String? = null // direct audio url if resolved
)
