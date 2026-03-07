package com.mvwj.yousify.youtube

import androidx.room.*
import android.content.Context

@Entity(tableName = "youtube_search_cache")
data class SearchCacheEntry(
    @PrimaryKey val isrc: String,
    val youtubeUrl: String,
    val timestamp: Long
)

@Dao
interface SearchCacheDao {
    @Query("SELECT * FROM youtube_search_cache WHERE isrc = :isrc LIMIT 1")
    suspend fun getByIsrc(isrc: String): SearchCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SearchCacheEntry)

    @Query("DELETE FROM youtube_search_cache WHERE timestamp < :expiry")
    suspend fun deleteOlderThan(expiry: Long)
}

@Database(entities = [SearchCacheEntry::class], version = 1, exportSchema = false)
abstract class SearchCacheDatabase : RoomDatabase() {
    abstract fun searchCacheDao(): SearchCacheDao
    companion object {
        @Volatile private var INSTANCE: SearchCacheDatabase? = null
        fun getInstance(context: Context): SearchCacheDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                SearchCacheDatabase::class.java,
                "youtube_search_cache.db"
            ).build().also { INSTANCE = it }
        }
    }
}
