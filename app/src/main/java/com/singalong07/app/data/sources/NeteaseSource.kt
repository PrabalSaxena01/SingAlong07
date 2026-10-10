package com.singalong07.app.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class NeteaseSource : LyricsSource {
    override val id: String = "netease"
    override val displayName: String = "Netease Cloud (Synced & Translations)"
    override val supportsSynced: Boolean = true
    override val supportsTranslations: Boolean = true
    override val requiresApiKey: Boolean = false

    private val searchUrl = "https://music.163.com/api/search/get/web"
    private val lyricUrl = "https://music.163.com/api/song/lyric"

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val songId = searchTrackId(title, artist)
                ?: throw NoSuchElementException("Track not found on Netease: $artist - $title")

            val lyricJsonStr = fetchLyricPayload(songId)
                ?: throw IllegalStateException("Failed to load lyrics payload for id $songId")

            val json = JSONObject(lyricJsonStr)
            val lrcObj = json.optJSONObject("lrc")
            val tlyricObj = json.optJSONObject("tlyric")
            val romalrcObj = json.optJSONObject("romalrc")
            val klyricObj = json.optJSONObject("klyric")

            val originalLrc = lrcObj?.optString("lyric")?.takeIf { it.isNotBlank() }
            val translatedLrc = tlyricObj?.optString("lyric")?.takeIf { it.isNotBlank() }
            val romanizedLrc = romalrcObj?.optString("lyric")?.takeIf { it.isNotBlank() }
            val isWordByWord = klyricObj?.optString("lyric")?.isNotBlank() == true

            if (originalLrc.isNullOrBlank()) {
                throw NoSuchElementException("Netease song found, but has no lyrics text")
            }

            LyricsResult(
                sourceName = displayName,
                trackTitle = title,
                artistName = artist,
                syncedLrc = originalLrc,
                translatedLrc = translatedLrc,
                romanizedLrc = romanizedLrc,
                isWordByWord = isWordByWord,
                sourceConfidence = 0.90f
            )
        }
    }

    private fun searchTrackId(title: String, artist: String): Long? {
        val query = URLEncoder.encode("$artist $title", "UTF-8")
        val fullUrl = "$searchUrl?s=$query&type=1&offset=0&limit=5"
        val response = makeHttpRequest(fullUrl) ?: return null

        val obj = JSONObject(response)
        val result = obj.optJSONObject("result") ?: return null
        val songs = result.optJSONArray("songs") ?: return null

        if (songs.length() == 0) return null
        return songs.getJSONObject(0).optLong("id")
    }

    private fun fetchLyricPayload(songId: Long): String? {
        val fullUrl = "$lyricUrl?id=$songId&lv=-1&kv=-1&tv=-1&rv=-1"
        return makeHttpRequest(fullUrl)
    }

    private fun makeHttpRequest(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            setRequestProperty("Referer", "https://music.163.com/")
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
