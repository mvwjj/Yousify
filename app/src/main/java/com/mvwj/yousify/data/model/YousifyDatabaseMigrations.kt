package com.mvwj.yousify.data.model

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object YousifyDatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
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

            database.execSQL(
                """
                INSERT INTO yt_track_cache_temp (spotifyId, videoId, score, audioUrl)
                SELECT spotifyId, videoId, score, audioUrl FROM yt_track_cache
                """
            )

            database.execSQL("DROP TABLE yt_track_cache")
            database.execSQL("ALTER TABLE yt_track_cache_temp RENAME TO yt_track_cache")
            database.execSQL("CREATE INDEX index_yt_track_cache_videoId ON yt_track_cache (videoId)")
            database.execSQL("CREATE INDEX index_yt_track_cache_timestamp ON yt_track_cache (timestamp)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE yt_track_cache ADD COLUMN isrc TEXT")
            database.execSQL("CREATE INDEX index_yt_track_cache_isrc ON yt_track_cache (isrc)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) = Unit
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE playlists ADD COLUMN imageUrl TEXT")

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tracks_new (
                    playlistId TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    imageUrl TEXT,
                    isrc TEXT,
                    durationMs INTEGER NOT NULL,
                    PRIMARY KEY(playlistId, position),
                    FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE
                )
                """
            )

            database.execSQL(
                """
                INSERT INTO tracks_new (playlistId, position, id, title, artist, imageUrl, isrc, durationMs)
                SELECT playlistId, rowid, id, title, artist, imageUrl, isrc, durationMs
                FROM tracks
                """
            )

            database.execSQL("DROP TABLE tracks")
            database.execSQL("ALTER TABLE tracks_new RENAME TO tracks")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_playlistId ON tracks (playlistId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_id ON tracks (id)")
        }
    }
}
