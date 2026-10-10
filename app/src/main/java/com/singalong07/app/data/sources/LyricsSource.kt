package com.singalong07.app.data.sources

data class LyricsResult(
    val sourceName: String,
    val trackTitle: String,
    val artistName: String,
    val syncedLrc: String? = null,
    val plainLyrics: String? = null,
    val translatedLrc: String? = null,
    val romanizedLrc: String? = null,
    val isWordByWord: Boolean = false,
    val sourceConfidence: Float = 1.0f
)

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
