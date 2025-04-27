package com.veshikov.yousify.data.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.veshikov.yousify.worker.YtCacheCleanupWorker

/**
 * Главная база данных Yousify, содержащая:
 * - Плейлисты
 * - Треки
 * - Кэш соответствий трек-видео
 */
@Database(
    entities = [
        PlaylistEntity::class,
        TrackEntity::class,
        YtTrackCacheEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class YousifyDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun trackDao(): TrackDao
    abstract fun ytTrackCacheDao(): YtTrackCacheDao

    companion object {
        @Volatile private var INSTANCE: YousifyDatabase? = null
        
        fun getInstance(context: Context): YousifyDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildDatabase(context).also { db ->
                INSTANCE = db
                
                // Планируем периодическую очистку кэша
                YtCacheCleanupWorker.schedulePeriodicCleanup(context)
            }
        }
        
        private fun buildDatabase(context: Context): YousifyDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                YousifyDatabase::class.java,
                "yousify.db"
            )
            .addMigrations(
                YousifyDatabaseMigrations.MIGRATION_1_2,
                YousifyDatabaseMigrations.MIGRATION_2_3,
                YousifyDatabaseMigrations.MIGRATION_3_4
            )
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}
