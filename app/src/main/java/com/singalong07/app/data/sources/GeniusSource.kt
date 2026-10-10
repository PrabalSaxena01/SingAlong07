package com.singalong07.app.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Genius Lyrics Source.
 * Searches track URL via Genius Search Multi API and extracts clean lyrics.
 */
class GeniusSource : LyricsSource {
    override val id: String = "genius"
    override val displayName: String = "Genius"
    override val supportsSynced: Boolean = false // Plain lyrics paced via cadence engine
    override val supportsTranslations: Boolean = false
    override val requiresApiKey: Boolean = false

    private val searchEndpoint = "https://genius.com/api/search/multi"
    private val googlebotUA = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val songUrl = searchSongUrl(title, artist)
                ?: throw NoSuchElementException("No song found on Genius for: $artist - $title")

            val lyrics = scrapeLyricsFromUrl(songUrl)
                ?: throw NoSuchElementException("Failed to extract lyrics container from Genius page: $songUrl")

            if (lyrics.isBlank()) {
                throw NoSuchElementException("Empty lyrics from Genius")
            }

            LyricsResult(
                sourceName = displayName,
                trackTitle = title,
                artistName = artist,
                plainLyrics = lyrics,
                sourceConfidence = 0.88f
            )
        }
    }

    private fun searchSongUrl(title: String, artist: String): String? {
        val query = URLEncoder.encode("$artist $title".trim(), "UTF-8")
        val url = "$searchEndpoint?q=$query"
        val response = getHttp(url, googlebotUA) ?: return null

        return try {
            val root = JSONObject(response)
            val respObj = root.optJSONObject("response") ?: return null
            val sections = respObj.optJSONArray("sections") ?: return null

            for (i in 0 until sections.length()) {
                val sec = sections.optJSONObject(i) ?: continue
                if (sec.optString("type") == "song") {
                    val hits = sec.optJSONArray("hits") ?: continue
                    if (hits.length() > 0) {
                        val hit = hits.optJSONObject(0) ?: continue
                        val resObj = hit.optJSONObject("result") ?: continue
                        val songUrl = resObj.optString("url", "")
                        if (songUrl.isNotBlank()) {
                            return songUrl
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun scrapeLyricsFromUrl(songUrl: String): String? {
        val html = getHttp(songUrl, googlebotUA) ?: return null

        val containerPattern = Pattern.compile("data-lyrics-container=\"true\"[^>]*>(.*?)</div>", Pattern.DOTALL)
        val matcher = containerPattern.matcher(html)

        val fullLyricsBuilder = StringBuilder()
        var foundAny = false

        while (matcher.find()) {
            val containerHtml = matcher.group(1) ?: continue
            foundAny = true

            // Replace <br> and <br/> with newlines
            var cleanText = containerHtml.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            // Remove all HTML tags
            cleanText = cleanText.replace(Regex("<[^>]+>"), "")
            // Decode common HTML entities
            cleanText = cleanText
                .replace("&#x27;", "'")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")

            fullLyricsBuilder.append(cleanText.trim()).append("\n\n")
        }

        if (!foundAny) return null

        val result = fullLyricsBuilder.toString().trim()
        return if (result.isNotEmpty()) result else null
    }

    private fun getHttp(urlString: String, userAgent: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "text/html,application/json,application/xhtml+xml,*/*")
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
