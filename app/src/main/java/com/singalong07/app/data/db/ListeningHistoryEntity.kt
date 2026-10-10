package com.singalong07.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackTitle: String,
    val artistName: String,
    val albumName: String? = null,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val sourcePackage: String,        // e.g. "com.spotify.music"
    val lyricSource: String? = null,  // e.g. "LRCLIB", "Netease"
    val lyricSyncType: String,       // "SYNCED_LRC", "PACED_PLAIN", "INSTRUMENTAL"
    val userOffsetMs: Long = 0L,     // Saved user timing calibration
    val matchedViaFingerprint: Boolean = false
)

@Entity(tableName = "user_offsets")
data class UserOffsetEntity(
    @PrimaryKey val trackHash: String, // "artist__title"
    val offsetMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)
