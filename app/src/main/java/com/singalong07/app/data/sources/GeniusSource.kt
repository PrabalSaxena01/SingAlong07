package com.singalong07.app.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Genius Lyrics Source.
 * Resolves song URL via intelligent slug generation and search fallback,
 * then extracts clean lyrics from Genius lyrics containers.
 */
class GeniusSource : LyricsSource {
    override val id: String = "genius"
    override val displayName: String = "Genius"
    override val supportsSynced: Boolean = false // Plain lyrics paced via cadence engine
    override val supportsTranslations: Boolean = false
    override val requiresApiKey: Boolean = false

    private val userAgent = "Dalvik/2.1.0 (Linux; U; Android 14; Mobile)"

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
        // Strategy 1: Direct Deterministic Slug
        val primarySlug = generateSlug(artist, title)
        val primaryUrl = "https://genius.com/$primarySlug"
        val primaryHtml = getHttp(primaryUrl)
        if (primaryHtml != null && primaryHtml.contains("data-lyrics-container")) {
            return primaryUrl
        }

        // Strategy 2: Simplified Artist Slug (strip secondary featured artists)
        val primaryArtist = artist.split(Regex("[,&]|(?i)\\s+feat\\.?\\s+|(?i)\\s+ft\\.?\\s+")).first().trim()
        if (!primaryArtist.equals(artist, ignoreCase = true)) {
            val secondarySlug = generateSlug(primaryArtist, title)
            val secondaryUrl = "https://genius.com/$secondarySlug"
            val secondaryHtml = getHttp(secondaryUrl)
            if (secondaryHtml != null && secondaryHtml.contains("data-lyrics-container")) {
                return secondaryUrl
            }
        }

        // Strategy 3: DuckDuckGo HTML Search for Genius URL
        return searchViaDuckDuckGo(title, artist)
    }

    private fun searchViaDuckDuckGo(title: String, artist: String): String? {
        val query = URLEncoder.encode("site:genius.com $artist $title lyrics", "UTF-8")
        val searchUrl = "https://html.duckduckgo.com/html/?q=$query"
        val html = getHttp(searchUrl) ?: return null

        // Search for genius.com/...-lyrics in hrefs or text
        val linkPattern = Pattern.compile("""(?:https?://)?(?:www\.)?genius\.com/([a-zA-Z0-9-]+-lyrics)""")
        val matcher = linkPattern.matcher(html)
        if (matcher.find()) {
            val slug = matcher.group(1)
            return "https://genius.com/$slug"
        }
        return null
    }

    private fun generateSlug(artist: String, title: String): String {
        val cleanArtist = cleanForSlug(artist)
        val cleanTitle = cleanForSlug(title)
        return "$cleanArtist-$cleanTitle-lyrics"
    }

    private fun cleanForSlug(input: String): String {
        // Remove text in parentheses/brackets e.g. (feat. ...), [Remix]
        var s = input.replace(Regex("""\([^)]*\)"""), "").replace(Regex("""\[[^\]]*\]"""), "")
        // Remove accents / diacritics
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "")
        // Remove symbols except alphanumeric, hyphens, and spaces
        s = s.replace(Regex("""[^a-zA-Z0-9\s-]"""), "")
        // Collapse spaces and dashes into single dash
        s = s.trim().replace(Regex("""\s+"""), "-").replace(Regex("""-+"""), "-")
        return s.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun scrapeLyricsFromUrl(songUrl: String): String? {
        val html = getHttp(songUrl) ?: return null

        val containerPattern = Pattern.compile("data-lyrics-container=\"true\"[^>]*>(.*?)</div>", Pattern.DOTALL)
        val matcher = containerPattern.matcher(html)

        val fullLyricsBuilder = StringBuilder()
        var foundAny = false

        while (matcher.find()) {
            val containerHtml = matcher.group(1) ?: continue
            foundAny = true

            // Replace <br> and <br/> with newlines
            var cleanText = containerHtml.replace(Regex("(?i)<br\\s*/?>"), "\n")
            // Remove all HTML tags
            cleanText = cleanText.replace(Regex("<[^>]+>"), "")
            // Decode common HTML entities
            cleanText = decodeHtmlEntities(cleanText)

            // Strip out Genius metadata headers embedded in the first container
            cleanText = cleanText.replace(Regex("""(?i)^Translations[^\n]*\n*"""), "")
            cleanText = cleanText.replace(Regex("""(?i)^\d+\s+Contributors?[^\n]*\n*"""), "")
            cleanText = cleanText.replace(Regex("""(?i)^[^\n]*Lyrics\n*"""), "")

            val trimmed = cleanText.trim()
            if (trimmed.isNotEmpty()) {
                fullLyricsBuilder.append(trimmed).append("\n\n")
            }
        }

        if (!foundAny) return null

        val result = fullLyricsBuilder.toString().trim()
        return if (result.isNotEmpty()) result else null
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }

    private fun getHttp(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                instanceFollowRedirects = true
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
