package com.singalong07.app.domain.sync

import com.singalong07.app.domain.translation.DualLanguageLyricLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SyncResolutionTier {
    TIER_1_WORD_BY_WORD,     // Syllable / karaoke precision
    TIER_2_LINE_BY_LINE,     // Standard millisecond LRC
    TIER_3_HEURISTIC_PACING, // Plain lyrics paced via speech cadence estimation
    TIER_4_FINGERPRINT_MATCH,// Resolved via audio capture
    TIER_5_INSTRUMENTAL      // Instrumental / no lyrics
}

data class SyncPlaybackState(
    val activeLineIndex: Int = -1,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val tier: SyncResolutionTier = SyncResolutionTier.TIER_2_LINE_BY_LINE,
    val userOffsetMs: Long = 0L,
    val lines: List<DualLanguageLyricLine> = emptyList()
)

class BiDirectionalSyncEngine {

    private val _syncState = MutableStateFlow(SyncPlaybackState())
    val syncState: StateFlow<SyncPlaybackState> = _syncState.asStateFlow()

    private var activeLines: List<DualLanguageLyricLine> = emptyList()
    private var totalDurationMs: Long = 0L
    private var currentUserOffsetMs: Long = 0L

    fun loadSyncedLyrics(lines: List<DualLanguageLyricLine>, durationMs: Long, isWordByWord: Boolean = false) {
        this.activeLines = lines
        this.totalDurationMs = durationMs
        val tier = if (isWordByWord) SyncResolutionTier.TIER_1_WORD_BY_WORD else SyncResolutionTier.TIER_2_LINE_BY_LINE

        _syncState.update {
            it.copy(
                lines = lines,
                durationMs = durationMs,
                tier = tier,
                activeLineIndex = -1
            )
        }
    }

    /**
     * Intensive Fallback: Generates synthetic line timings based on word counts
     * and track duration when only un-synced plain lyrics are available.
     */
    fun loadPlainLyricsWithPacingFallback(plainText: String, durationMs: Long) {
        this.totalDurationMs = durationMs
        val rawLines = plainText.lines().map { it.trim() }.filter { it.isNotBlank() }

        if (rawLines.isEmpty() || durationMs <= 0L) {
            _syncState.update {
                it.copy(
                    lines = emptyList(),
                    tier = SyncResolutionTier.TIER_5_INSTRUMENTAL
                )
            }
            return
        }

        val introTimeMs = (durationMs * 0.08).toLong().coerceAtLeast(3000L)
        val outroTimeMs = (durationMs * 0.05).toLong().coerceAtLeast(3000L)
        val singingWindowMs = (durationMs - introTimeMs - outroTimeMs).coerceAtLeast(10000L)

        val totalWordCount = rawLines.sumOf { it.split(Regex("""\s+""")).size }.coerceAtLeast(1)
        var cumulativeTimeMs = introTimeMs

        val estimatedLines = rawLines.map { line ->
            val wordsInLine = line.split(Regex("""\s+""")).size.coerceAtLeast(1)
            val lineDurationMs = (singingWindowMs * (wordsInLine.toDouble() / totalWordCount)).toLong()

            val timestamp = cumulativeTimeMs
            cumulativeTimeMs += lineDurationMs

            DualLanguageLyricLine(
                timestampMs = timestamp,
                originalText = line,
                translatedText = null
            )
        }

        this.activeLines = estimatedLines
        _syncState.update {
            it.copy(
                lines = estimatedLines,
                durationMs = durationMs,
                tier = SyncResolutionTier.TIER_3_HEURISTIC_PACING,
                activeLineIndex = -1
            )
        }
    }

    fun updatePlaybackPosition(mediaPositionMs: Long, isPlaying: Boolean) {
        val effectivePosition = mediaPositionMs + currentUserOffsetMs
        val lineIndex = binarySearchActiveLine(effectivePosition)

        _syncState.update {
            it.copy(
                currentPositionMs = mediaPositionMs,
                isPlaying = isPlaying,
                activeLineIndex = lineIndex,
                userOffsetMs = currentUserOffsetMs
            )
        }
    }

    fun adjustOffset(deltaMs: Long): Long {
        currentUserOffsetMs += deltaMs
        _syncState.update { it.copy(userOffsetMs = currentUserOffsetMs) }
        return currentUserOffsetMs
    }

    fun setOffset(offsetMs: Long) {
        currentUserOffsetMs = offsetMs
        _syncState.update { it.copy(userOffsetMs = currentUserOffsetMs) }
    }

    /**
     * Bi-Directional Control: Calculates target player seek timestamp
     * when a user taps a lyric line in the floating overlay.
     */
    fun calculateSeekTimestampForLine(lineIndex: Int): Long? {
        if (lineIndex !in activeLines.indices) return null
        val targetLine = activeLines[lineIndex]
        return (targetLine.timestampMs - currentUserOffsetMs).coerceAtLeast(0L)
    }

    private fun binarySearchActiveLine(effectiveTimeMs: Long): Int {
        if (activeLines.isEmpty()) return -1
        if (effectiveTimeMs < activeLines.first().timestampMs) return -1

        var low = 0
        var high = activeLines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (activeLines[mid].timestampMs <= effectiveTimeMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
