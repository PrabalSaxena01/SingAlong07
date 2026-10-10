package com.singalong07.app.data.sources

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * KuGou Music (酷狗音乐) Lyrics Source.
 * Massive global database with over 50 million synchronized LRC tracks.
 */
class KugouSource : LyricsSource {
    override val id: String = "kugou"
    override val displayName: String = "KuGou (Synced LRC)"
    override val supportsSynced: Boolean = true
    override val supportsTranslations: Boolean = false
    override val requiresApiKey: Boolean = false

    private val searchUrl = "https://lyrics.kugou.com/search"
    private val downloadUrl = "https://lyrics.kugou.com/download"

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val candidate = searchCandidates(title, artist, durationSeconds)
                ?: throw NoSuchElementException("No lyrics candidate found on KuGou for: $artist - $title")

            val lrcText = downloadLyricContent(candidate.first, candidate.second)
                ?: throw IllegalStateException("Failed to download or decode KuGou lyric content")

            if (lrcText.isBlank()) {
                throw NoSuchElementException("Empty KuGou lyric content")
            }

            LyricsResult(
                sourceName = displayName,
                trackTitle = title,
                artistName = artist,
                syncedLrc = lrcText,
                sourceConfidence = 0.93f
            )
        }
    }

    private fun searchCandidates(title: String, artist: String, durationSeconds: Long?): Pair<String, String>? {
        val keyword = URLEncoder.encode("$artist $title".trim(), "UTF-8")
        val durationParam = if (durationSeconds != null && durationSeconds > 0) "&duration=${durationSeconds * 1000}" else ""
        val fullUrl = "$searchUrl?ver=1&man=yes&client=pc&keyword=$keyword$durationParam"

        val response = makeHttpRequest(fullUrl) ?: return null

        return try {
            val json = JSONObject(response)
            if (json.optInt("status") == 200) {
                val candidates = json.optJSONArray("candidates") ?: return null
                if (candidates.length() > 0) {
                    val first = candidates.getJSONObject(0)
                    val id = first.optString("id")
                    val accessKey = first.optString("accesskey")
                    if (id.isNotBlank() && accessKey.isNotBlank()) {
                        Pair(id, accessKey)
                    } else null
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadLyricContent(id: String, accessKey: String): String? {
        val fullUrl = "$downloadUrl?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"
        val response = makeHttpRequest(fullUrl) ?: return null

        return try {
            val json = JSONObject(response)
            if (json.optInt("status") == 200) {
                val base64Content = json.optString("content")
                if (base64Content.isNotBlank()) {
                    val decodedBytes = Base64.decode(base64Content, Base64.DEFAULT)
                    String(decodedBytes, Charsets.UTF_8)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun makeHttpRequest(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SingAlong07")
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
