package com.veshikov.yousify.data.model

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции для базы данных Yousify
 * Обеспечивают автоматическое обновление схемы базы данных без потери данных
 */
object YousifyDatabaseMigrations {
    
    /**
     * Миграция с версии 1 на версию 2
     * - Добавляет поле timestamp в таблицу yt_track_cache
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Создаем временную таблицу с новой схемой
            database.execSQL(
                """
                CREATE TABLE yt_track_cache_temp (
                    spotifyId TEXT NOT NULL PRIMARY KEY,
                    videoId TEXT NOT NULL,
                    score REAL NOT NULL,
                    audioUrl TEXT,
                    timestamp INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}
                )
                """
            )
            
            // Копируем данные из старой таблицы во временную
            database.execSQL(
                """
                INSERT INTO yt_track_cache_temp (spotifyId, videoId, score, audioUrl)
                SELECT spotifyId, videoId, score, audioUrl FROM yt_track_cache
                """
            )
            
            // Удаляем старую таблицу
            database.execSQL("DROP TABLE yt_track_cache")
            
            // Переименовываем временную таблицу
            database.execSQL("ALTER TABLE yt_track_cache_temp RENAME TO yt_track_cache")
            
            // Создаем индекс для videoId
            database.execSQL("CREATE INDEX index_yt_track_cache_videoId ON yt_track_cache (videoId)")
            
            // Создаем индекс для timestamp
            database.execSQL("CREATE INDEX index_yt_track_cache_timestamp ON yt_track_cache (timestamp)")
        }
    }
    
    /**
     * Миграция с версии 2 на версию 3
     * - Добавляет поле isrc в таблицу yt_track_cache
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Добавляем столбец isrc в существующую таблицу
            database.execSQL("ALTER TABLE yt_track_cache ADD COLUMN isrc TEXT")
            
            // Создаем индекс для isrc
            database.execSQL("CREATE INDEX index_yt_track_cache_isrc ON yt_track_cache (isrc)")
        }
    }
    
    /**
     * Миграция с версии 3 на версию 4
     * - Другие изменения, произведенные в приложении
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Предположительно, здесь могут быть другие изменения
            // Если в версии 4 не меняли таблицу yt_track_cache, 
            // можно оставить пустую имплементацию
        }
    }
}
