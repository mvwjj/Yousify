package com.mvwj.yousify.data.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object Ð´Ð»Ñ Ñ€Ð°Ð±Ð¾Ñ‚Ñ‹ Ñ ÐºÑÑˆÐµÐ¼ ÑÐ¾Ð¾Ñ‚Ð²ÐµÑ‚ÑÑ‚Ð²Ð¸Ð¹ Ñ‚Ñ€ÐµÐº-Ð²Ð¸Ð´ÐµÐ¾
 */
@Dao
interface YtTrackCacheDao {
    /**
     * ÐŸÐ¾Ð»ÑƒÑ‡Ð¸Ñ‚ÑŒ Ð·Ð°Ð¿Ð¸ÑÑŒ Ð¿Ð¾ ID Ñ‚Ñ€ÐµÐºÐ° Spotify - Ð±Ð»Ð¾ÐºÐ¸Ñ€ÑƒÑŽÑ‰Ð¸Ð¹ Ð¼ÐµÑ‚Ð¾Ð´
     */
    @Query("SELECT * FROM yt_track_cache WHERE spotifyId = :spotifyId")
    suspend fun getBySpotifyId(spotifyId: String): YtTrackCacheEntity?
    
    /**
     * ÐŸÐ¾Ð»ÑƒÑ‡Ð¸Ñ‚ÑŒ Ñ‚Ð¾Ð»ÑŒÐºÐ¾ ID Ð²Ð¸Ð´ÐµÐ¾ Ð¿Ð¾ ID Ñ‚Ñ€ÐµÐºÐ° Spotify - Ñ€ÐµÐ°ÐºÑ‚Ð¸Ð²Ð½Ñ‹Ð¹ Ð¼ÐµÑ‚Ð¾Ð´
     */
    @Query("SELECT videoId FROM yt_track_cache WHERE spotifyId = :spotifyId")
    fun getVideoIdFlowBySpotifyId(spotifyId: String): Flow<String?>
    
    /**
     * Ð’ÑÑ‚Ð°Ð²Ð¸Ñ‚ÑŒ Ð¸Ð»Ð¸ Ð¾Ð±Ð½Ð¾Ð²Ð¸Ñ‚ÑŒ Ð·Ð°Ð¿Ð¸ÑÑŒ Ð² ÐºÑÑˆÐµ
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: YtTrackCacheEntity)
    
    /**
     * ÐžÑ‡Ð¸ÑÑ‚Ð¸Ñ‚ÑŒ Ð·Ð°Ð¿Ð¸ÑÐ¸ ÑÑ‚Ð°Ñ€ÑˆÐµ ÑƒÐºÐ°Ð·Ð°Ð½Ð½Ð¾Ð³Ð¾ Ð²Ñ€ÐµÐ¼ÐµÐ½Ð¸ Ð² Ð¼Ð¸Ð»Ð»Ð¸ÑÐµÐºÑƒÐ½Ð´Ð°Ñ…
     */
    @Query("DELETE FROM yt_track_cache WHERE timestamp < :olderThanTimestamp")
    suspend fun deleteOlderThan(olderThanTimestamp: Long): Int
    
    /**
     * ÐŸÐ¾Ð»Ð½Ð¾ÑÑ‚ÑŒÑŽ Ð¾Ñ‡Ð¸ÑÑ‚Ð¸Ñ‚ÑŒ ÐºÑÑˆ
     */
    @Query("DELETE FROM yt_track_cache")
    suspend fun clearAll()
}
