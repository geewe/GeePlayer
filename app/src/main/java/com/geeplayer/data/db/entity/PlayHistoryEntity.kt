package com.geeplayer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String = "",
    val uri: String,
    val coverUrl: String? = null,
    val playedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L
)

@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val uri: String,
    val coverUrl: String? = null,
    val position: Int = 0
)

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val songKey: String,  // "title-artist" 作为 key
    val lrcText: String,
    val cachedAt: Long = System.currentTimeMillis()
)
