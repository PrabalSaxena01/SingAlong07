package com.singalong07.app.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lyrics.ovh Source.
 * Free, public API with massive English, Western, and global lyrics coverage.
 * Feeds un-synced lyrics into SingAlong07's Tier 3 Heuristic Cadence Pacing Engine.
 */
class LyricsOvhSource : LyricsSource {
    override val id: String = "lyricsovh"
    override val displayName: String = "Lyrics.ovh (Global Plain)"
    override val supportsSynced: Boolean = false
    override val supportsTranslations: Boolean = false
    override val requiresApiKey: Boolean = false

    private val baseUrl = "https://api.lyrics.ovh/v1"

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (artist.isBlank() || title.isBlank()) {
                throw IllegalArgumentException("Both artist and title required for Lyrics.ovh")
            }

            val encodedArtist = URLEncoder.encode(artist.trim(), "UTF-8")
            val encodedTitle = URLEncoder.encode(title.trim(), "UTF-8")
            val fullUrl = "$baseUrl/$encodedArtist/$encodedTitle"

            val response = makeHttpRequest(fullUrl)
                ?: throw NoSuchElementException("No lyrics found on Lyrics.ovh for: $artist - $title")

            val json = JSONObject(response)
            val lyricsText = json.optString("lyrics").trim()

            if (lyricsText.isBlank()) {
                throw NoSuchElementException("Empty lyrics returned from Lyrics.ovh")
            }

            LyricsResult(
                sourceName = displayName,
                trackTitle = title,
                artistName = artist,
                syncedLrc = null,
                plainLyrics = lyricsText,
                sourceConfidence = 0.85f
            )
        }
    }

    private fun makeHttpRequest(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "SingAlong07/1.0 (Android)")
        }

        return try {
            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
        } finally {
            connection.disconnect()
        }
    }
}
