package com.veshikov.yousify.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Сущность для кэширования соответствия между треками Spotify и видео YouTube.
 * Содержит:
 * - spotifyId (первичный ключ)
 * - videoId (индексирован для быстрого поиска)
 * - score (оценка соответствия)
 * - isrc (международный код записи)
 * - timestamp (время создания записи для автоматической очистки)
 * - audioUrl (опционально - прямая ссылка на аудио, если разрешена)
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
