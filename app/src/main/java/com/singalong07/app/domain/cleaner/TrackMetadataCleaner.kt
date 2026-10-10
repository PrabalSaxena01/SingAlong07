package com.singalong07.app.domain.cleaner

data class CleanedMetadata(
    val title: String,
    val artist: String,
    val rawOriginal: String
)

object TrackMetadataCleaner {

    private val NOISE_REGEXES = listOf(
        Regex("""(?i)\[[^\]]*(remaster|4k|hd|video|audio|official|visualizer|lyric|from|t-series|zee)[^\]]*\]"""),
        Regex("""(?i)\([^)]*(remaster|4k|hd|video|audio|official|visualizer|lyric|from|t-series|zee)[^)]*\)"""),
        Regex("""(?i)\b(feat\.|ft\.)\s+[^()\\[\\]]+"""),
        Regex("""(?i)\b(lyrics?|lyric video|audio|full video|video song)\b"""),
        Regex("""(?i)\|\s*(t-series|zee music|sony music|yrf|tips).*$"""),
        Regex("""(?i)^\d+[\s._-]+"""),                     // e.g. "01. Song Title"
        Regex("""(?i)\.(mp3|flac|m4a|wav|aac|ogg)$""")    // file extensions
    )

    /**
     * Cleans noisy track metadata into canonical search tokens.
     */
    fun clean(rawTitle: String, rawArtist: String?): CleanedMetadata {
        var cleanTitle = rawTitle
        var cleanArtist = rawArtist?.trim().orEmpty()

        // 1. Strip all noise patterns from title
        NOISE_REGEXES.forEach { regex ->
            cleanTitle = cleanTitle.replace(regex, "")
        }

        // 2. Handle embedded "Artist - Title" patterns if artist is missing or generic
        if (cleanArtist.isBlank() || cleanArtist.equals("Unknown", ignoreCase = true) || cleanArtist.contains("YouTube", ignoreCase = true)) {
            val dashSplit = cleanTitle.split(Regex("""\s+-\s+"""))
            if (dashSplit.size == 2) {
                cleanArtist = dashSplit[0].trim()
                cleanTitle = dashSplit[1].trim()
            }
        }

        // 3. Normalize multiple whitespaces and trim special boundary characters
        cleanTitle = cleanTitle.replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '_', '|', ':', '.')
        cleanArtist = cleanArtist.replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '_', '|', ':', '.')

        return CleanedMetadata(
            title = cleanTitle.ifBlank { rawTitle.trim() },
            artist = cleanArtist,
            rawOriginal = rawTitle
        )
    }

    /**
     * Computes fuzzy Token Sort Ratio similarity (0.0 to 1.0) between two strings.
     * Tokens are lowercased and sorted alphabetically to eliminate ordering bias.
     */
    fun tokenSortRatio(s1: String, s2: String): Double {
        val t1 = s1.lowercase().split(Regex("""\s+""")).sorted().joinToString(" ")
        val t2 = s2.lowercase().split(Regex("""\s+""")).sorted().joinToString(" ")
        return levenshteinSimilarity(t1, t2)
    }

    /**
     * Normalized Levenshtein similarity metric (0.0 to 1.0).
     */
    fun levenshteinSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // deletion
                    dp[i][j - 1] + 1,       // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        val distance = dp[s1.length][s2.length]
        val maxLen = maxOf(s1.length, s2.length)
        return 1.0 - (distance.toDouble() / maxLen)
    }
}
