package com.singalong07.app.domain.parser

import com.singalong07.app.domain.translation.DualLanguageLyricLine

data class ParsedLrcResult(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val globalOffsetMs: Long = 0L,
    val lines: List<DualLanguageLyricLine> = emptyList(),
    val isWordByWord: Boolean = false
)

object LrcParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{2,3}))?\]""")
    private val TAG_REGEX = Regex("""\[(ti|ar|al|offset|by):([^]]+)\]""", RegexOption.IGNORE_CASE)

    /**
     * Parses raw LRC text into a structured, chronologically sorted list of lines.
     */
    fun parse(rawLrc: String): ParsedLrcResult {
        if (rawLrc.isBlank()) return ParsedLrcResult()

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var globalOffsetMs = 0L
        var hasWordByWord = false

        val parsedLines = mutableListOf<DualLanguageLyricLine>()

        rawLrc.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            // Check metadata tags e.g. [ar:Queen]
            val tagMatch = TAG_REGEX.matchEntire(trimmed)
            if (tagMatch != null) {
                val key = tagMatch.groupValues[1].lowercase()
                val value = tagMatch.groupValues[2].trim()
                when (key) {
                    "ti" -> title = value
                    "ar" -> artist = value
                    "al" -> album = value
                    "offset" -> globalOffsetMs = value.toLongOrNull() ?: 0L
                }
                return@forEach
            }

            // Extract all timestamps on this line (supports repeated timestamps like [01:10.00][01:25.00] Chorus)
            val matches = TIMESTAMP_REGEX.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val lyricText = trimmed.substring(matches.last().range.last + 1).trim()
                if (lyricText.contains("<") && lyricText.contains(">")) {
                    hasWordByWord = true
                }

                matches.forEach { match ->
                    val min = match.groupValues[1].toLong()
                    val sec = match.groupValues[2].toLong()
                    val milliStr = match.groupValues[3]
                    val millis = when (milliStr.length) {
                        2 -> milliStr.toLong() * 10
                        3 -> milliStr.toLong()
                        else -> 0L
                    }

                    val timestampMs = (min * 60 * 1000) + (sec * 1000) + millis + globalOffsetMs

                    parsedLines.add(
                        DualLanguageLyricLine(
                            timestampMs = timestampMs.coerceAtLeast(0L),
                            originalText = lyricText
                        )
                    )
                }
            }
        }

        // Sort chronologically
        parsedLines.sortBy { it.timestampMs }

        return ParsedLrcResult(
            title = title,
            artist = artist,
            album = album,
            globalOffsetMs = globalOffsetMs,
            lines = parsedLines,
            isWordByWord = hasWordByWord
        )
    }
}
