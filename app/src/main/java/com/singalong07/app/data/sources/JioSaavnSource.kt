package com.singalong07.app.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * JioSaavn & Bollywood/Indian Regional Lyrics Source.
 * Provides unmatched coverage for Bollywood (Hindi), Punjabi, Tamil, Telugu,
 * and indie Indian music.
 */
class JioSaavnSource : LyricsSource {
    override val id: String = "jiosaavn"
    override val displayName: String = "JioSaavn (Bollywood & Hindi)"
    override val supportsSynced: Boolean = false
    override val supportsTranslations: Boolean = true
    override val requiresApiKey: Boolean = false

    private val searchApi = "https://saavn.dev/api/search/songs"
    private val lyricsApi = "https://saavn.dev/api/songs"

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Search for song on JioSaavn
            val songId = searchSongId(title, artist)
                ?: throw NoSuchElementException("No Bollywood match found on JioSaavn for: $title")

            // 2. Fetch lyrics for this song ID
            val lyricsPayload = fetchLyricsForId(songId)
                ?: throw NoSuchElementException("Song found on JioSaavn, but no lyrics available")

            val cleanLyrics = formatLyricsText(lyricsPayload)
            if (cleanLyrics.isBlank()) {
                throw NoSuchElementException("Empty lyrics content from JioSaavn")
            }

            LyricsResult(
                sourceName = displayName,
                trackTitle = title,
                artistName = artist,
                syncedLrc = null, // Saavn delivers unsynced text; feeds into Cadence Pacing Engine
                plainLyrics = cleanLyrics,
                sourceConfidence = 0.92f
            )
        }
    }

    private fun searchSongId(title: String, artist: String): String? {
        val cleanQuery = "$title ${artist.take(20)}".trim()
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
        val fullUrl = "$searchApi?query=$encodedQuery&page=1&limit=5"

        val response = makeHttpRequest(fullUrl) ?: return fallbackDirectSaavnSearch(title)

        return try {
            val json = JSONObject(response)
            if (json.optBoolean("success", false)) {
                val data = json.optJSONObject("data") ?: return null
                val results = data.optJSONArray("results") ?: return null
                if (results.length() > 0) {
                    val firstSong = results.getJSONObject(0)
                    firstSong.optString("id").takeIf { it.isNotBlank() }
                } else null
            } else null
        } catch (_: Exception) {
            fallbackDirectSaavnSearch(title)
        }
    }

    private fun fallbackDirectSaavnSearch(title: String): String? {
        // Fallback to official JioSaavn autocomplete endpoint
        val encodedQuery = URLEncoder.encode(title, "UTF-8")
        val fallbackUrl = "https://www.jiosaavn.com/api.php?__call=autocomplete.get&_format=json&_marker=0&cc=in&includeMetaTags=1&query=$encodedQuery"
        val response = makeHttpRequest(fallbackUrl) ?: return null

        return try {
            val json = JSONObject(response)
            val songsObj = json.optJSONObject("songs") ?: return null
            val dataArray = songsObj.optJSONArray("data") ?: return null
            if (dataArray.length() > 0) {
                dataArray.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchLyricsForId(songId: String): String? {
        val fullUrl = "$lyricsApi/$songId/lyrics"
        val response = makeHttpRequest(fullUrl) ?: return null

        return try {
            val json = JSONObject(response)
            if (json.optBoolean("success", false)) {
                val data = json.optJSONObject("data")
                data?.optString("lyrics")
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatLyricsText(rawHtml: String): String {
        return rawHtml
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""&amp;"""), "&")
            .replace(Regex("""&quot;"""), "\"")
            .replace(Regex("""&#039;"""), "'")
            .replace(Regex("""&lt;"""), "<")
            .replace(Regex("""&gt;"""), ">")
            .trim()
    }

    private fun makeHttpRequest(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SingAlong07/1.0")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
