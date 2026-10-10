package com.singalong07.app.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * YouTube Music (YTM Innertube API) Lyrics Source.
 * Fetches official lyrics catalog directly from YouTube Music Innertube endpoints.
 */
class YouTubeMusicSource : LyricsSource {
    override val id: String = "ytmusic"
    override val displayName: String = "YouTube Music"
    override val supportsSynced: Boolean = false // Paced plain text with cadence fallback
    override val supportsTranslations: Boolean = false
    override val requiresApiKey: Boolean = false

    private val searchEndpoint = "https://music.youtube.com/youtubei/v1/search"
    private val nextEndpoint = "https://music.youtube.com/youtubei/v1/next"
    private val browseEndpoint = "https://music.youtube.com/youtubei/v1/browse"

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val videoId = searchSongVideoId(title, artist)
                ?: throw NoSuchElementException("No YouTube Music track match for: $artist - $title")

            val lyricsBrowseId = fetchLyricsBrowseId(videoId)
                ?: throw NoSuchElementException("No lyrics browse ID found on YouTube Music for videoId: $videoId")

            val lyricsText = fetchLyricsFromBrowse(lyricsBrowseId)
                ?: throw NoSuchElementException("No lyrics content found for YouTube Music browseId: $lyricsBrowseId")

            if (lyricsText.isBlank() || lyricsText.contains("Lyrics not available", ignoreCase = true)) {
                throw NoSuchElementException("Empty or unavailable lyrics on YouTube Music")
            }

            LyricsResult(
                sourceName = displayName,
                trackTitle = title,
                artistName = artist,
                plainLyrics = lyricsText,
                sourceConfidence = 0.90f
            )
        }
    }

    private fun searchSongVideoId(title: String, artist: String): String? {
        val query = "$artist $title".trim()
        val payload = JSONObject().apply {
            put("context", makeClientContext())
            put("query", query)
            put("params", "EgWKAQIIAWoKEAkQChAFEAMQBA%3D%3D") // Songs filter
        }

        val responseStr = postJson(searchEndpoint, payload.toString()) ?: return null
        
        // Match first videoId from response
        val pattern = Pattern.compile("\"videoId\":\\s*\"([a-zA-Z0-9_-]{11})\"")
        val matcher = pattern.matcher(responseStr)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    private fun fetchLyricsBrowseId(videoId: String): String? {
        val payload = JSONObject().apply {
            put("context", makeClientContext())
            put("videoId", videoId)
        }

        val responseStr = postJson(nextEndpoint, payload.toString()) ?: return null

        // Look for browseId beginning with MPLYt (Music Playlist Lyrics Track)
        val pattern = Pattern.compile("\"browseId\":\\s*\"(MPLYt[^\"]+)\"")
        val matcher = pattern.matcher(responseStr)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    private fun fetchLyricsFromBrowse(browseId: String): String? {
        val payload = JSONObject().apply {
            put("context", makeClientContext())
            put("browseId", browseId)
        }

        val responseStr = postJson(browseEndpoint, payload.toString()) ?: return null

        return try {
            val root = JSONObject(responseStr)
            val contents = root.optJSONObject("contents") ?: return null
            val sectionList = contents.optJSONObject("sectionListRenderer") ?: return null
            val contentsArray = sectionList.optJSONArray("contents") ?: return null
            
            for (i in 0 until contentsArray.length()) {
                val item = contentsArray.optJSONObject(i) ?: continue
                val shelf = item.optJSONObject("musicDescriptionShelfRenderer") ?: continue
                val desc = shelf.optJSONObject("description") ?: continue
                val runs = desc.optJSONArray("runs") ?: continue
                
                val builder = StringBuilder()
                for (j in 0 until runs.length()) {
                    val runObj = runs.optJSONObject(j) ?: continue
                    builder.append(runObj.optString("text", ""))
                }
                val lyrics = builder.toString().trim()
                if (lyrics.isNotEmpty()) {
                    return lyrics
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun makeClientContext(): JSONObject {
        val client = JSONObject().apply {
            put("clientName", "WEB_REMIX")
            put("clientVersion", "1.20240101.01.00")
            put("hl", "en")
            put("gl", "US")
        }
        return JSONObject().apply {
            put("client", client)
        }
    }

    private fun postJson(urlString: String, jsonBody: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 7000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Origin", "https://music.youtube.com")
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
