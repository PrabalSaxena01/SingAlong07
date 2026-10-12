package com.singalong07.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent Room cache entity for fetched lyrics and synchronization values.
 * Stores cleaned metadata, source attribution, timing offset calibrations, and lyric content.
 */
@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val trackHash: String,            // "${cleanedArtist}__${cleanedTitle}"
    val cleanedTitle: String,                      // Cleaned song name
    val cleanedArtist: String,                     // Cleaned artist name
    val sourceName: String,                        // Source it pulled from (e.g. "LRCLIB", "Genius", "JioSaavn")
    val syncType: String,                          // "SYNCED_LRC", "PACED_PLAIN", "WORD_BY_WORD"
    val userOffsetMs: Long = 0L,                   // User synchronization calibration value
    val durationMs: Long = 0L,                     // Track duration in milliseconds
    val isWordByWord: Boolean = false,             // High-resolution word sync flag
    val syncedLrc: String? = null,                 // Full LRC synchronized timestamped text
    val plainLyrics: String? = null,               // Plain text lyrics
    val cachedAt: Long = System.currentTimeMillis() // Timestamp when cached
)
