package com.lyrics.floating.data.sources

/**
 * Result data object returned by any lyrics source.
 */
data class LyricsResult(
    val sourceName: String,
    val trackTitle: String,
    val artistName: String,
    val syncedLrc: String? = null,              // Standard [mm:ss.xx] timestamped lines
    val plainLyrics: String? = null,            // Unsynced plain text
    val translatedLrc: String? = null,          // Synchronized translation lines (e.g. from Netease tlyric)
    val romanizedLrc: String? = null,           // Phonetic transcription (Romaji, Pinyin, etc.)
    val isWordByWord: Boolean = false,          // Syllable / word-by-word timestamps present
    val sourceConfidence: Float = 1.0f          // Score from 0.0 to 1.0 based on match quality
)

/**
 * Standard interface for all lyric service providers.
 */
interface LyricsSource {
    val id: String
    val displayName: String
    val supportsSynced: Boolean
    val supportsTranslations: Boolean
    val requiresApiKey: Boolean

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Long? = null
    ): Result<LyricsResult>
}
