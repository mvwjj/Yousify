package com.veshikov.yousify.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracks",
    foreignKeys = [ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playlistId")]
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val title: String,
    val artist: String,
    val isrc: String?,
    val durationMs: Long
)
