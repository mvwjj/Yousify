package com.mvwj.yousify.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "tracks",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playlistId"), Index("id")]
)
data class TrackEntity(
    val playlistId: String,
    val position: Int,
    val id: String,
    val title: String,
    val artist: String,
    val imageUrl: String? = null,
    val isrc: String?,
    val durationMs: Long
)
