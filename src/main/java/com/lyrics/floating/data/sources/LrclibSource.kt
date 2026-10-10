package com.lyrics.floating.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * LRCLIB Source implementation.
 * LRCLIB (https://lrclib.net) is an open-source, community-driven, free database
 * dedicated specifically to synchronized LRC lyrics.
 */
class LrclibSource : LyricsSource {
    override val id: String = "lrclib"
    override val displayName: String = "LRCLIB (Community Synced)"
    override val supportsSynced: Boolean = true
    override val supportsTranslations: Boolean = false
    override val requiresApiKey: Boolean = false

    private val baseUrl = "https://lrclib.net/api"

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Attempt exact match via /api/get
            val exactResult = queryExactMatch(title, artist, album, durationSeconds)
            if (exactResult != null) return@runCatching exactResult

            // 2. Fallback to /api/search if exact query missed
            val searchResult = querySearchFallback(title, artist)
            if (searchResult != null) return@runCatching searchResult

            throw NoSuchElementException("No lyrics found on LRCLIB for $artist - $title")
        }
    }

    private fun queryExactMatch(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): LyricsResult? {
        val queryBuilder = StringBuilder("$baseUrl/get?")
            .append("track_name=").append(URLEncoder.encode(title, "UTF-8"))
            .append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"))

        if (!album.isNullOrBlank()) {
            queryBuilder.append("&album_name=").append(URLEncoder.encode(album, "UTF-8"))
        }
        if (durationSeconds != null && durationSeconds > 0) {
            queryBuilder.append("&duration=").append(durationSeconds)
        }

        val json = makeHttpRequest(queryBuilder.toString()) ?: return null
        return parseLrclibJson(json, title, artist)
    }

    private fun querySearchFallback(title: String, artist: String): LyricsResult? {
        val searchQuery = URLEncoder.encode("$artist $title", "UTF-8")
        val searchUrl = "$baseUrl/search?q=$searchQuery"
        val response = makeHttpRequest(searchUrl) ?: return null

        val jsonArray = JSONArray(response)
        if (jsonArray.length() == 0) return null

        // Pick top result with synced lyrics if available
        var bestItem: JSONObject? = null
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            if (item.has("syncedLyrics") && !item.isNull("syncedLyrics")) {
                bestItem = item
                break
            }
        }
        if (bestItem == null) bestItem = jsonArray.getJSONObject(0)

        return parseLrclibJson(bestItem.toString(), title, artist)
    }

    private fun parseLrclibJson(jsonStr: String, title: String, artist: String): LyricsResult {
        val obj = JSONObject(jsonStr)
        val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() }
        val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() }

        return LyricsResult(
            sourceName = displayName,
            trackTitle = obj.optString("trackName", title),
            artistName = obj.optString("artistName", artist),
            syncedLrc = synced,
            plainLyrics = plain,
            sourceConfidence = if (synced != null) 0.95f else 0.70f
        )
    }

    private fun makeHttpRequest(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "FloatingLyricsApp/1.0 (Android)")
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
