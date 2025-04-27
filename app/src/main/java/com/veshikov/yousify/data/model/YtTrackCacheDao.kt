package com.veshikov.yousify.data.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object для работы с кэшем соответствий трек-видео
 */
@Dao
interface YtTrackCacheDao {
    /**
     * Получить запись по ID трека Spotify - блокирующий метод
     */
    @Query("SELECT * FROM yt_track_cache WHERE spotifyId = :spotifyId")
    suspend fun getBySpotifyId(spotifyId: String): YtTrackCacheEntity?
    
    /**
     * Получить только ID видео по ID трека Spotify - реактивный метод
     */
    @Query("SELECT videoId FROM yt_track_cache WHERE spotifyId = :spotifyId")
    fun getVideoIdFlowBySpotifyId(spotifyId: String): Flow<String?>
    
    /**
     * Вставить или обновить запись в кэше
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: YtTrackCacheEntity)
    
    /**
     * Очистить записи старше указанного времени в миллисекундах
     */
    @Query("DELETE FROM yt_track_cache WHERE timestamp < :olderThanTimestamp")
    suspend fun deleteOlderThan(olderThanTimestamp: Long): Int
    
    /**
     * Полностью очистить кэш
     */
    @Query("DELETE FROM yt_track_cache")
    suspend fun clearAll()
}
