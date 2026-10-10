package com.lyrics.floating.domain.sync

import com.lyrics.floating.domain.translation.DualLanguageLyricLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Quality levels for synchronized lyrics fallback.
 */
enum class SyncResolutionTier {
    TIER_1_WORD_BY_WORD,     // Millisecond syllable / karaoke accuracy
    TIER_2_LINE_BY_LINE,     // Standard synced LRC
    TIER_3_HEURISTIC_PACING, // Plain lyrics paced via speech cadence estimation
    TIER_4_FINGERPRINT_MATCH,// Resolved via acoustic audio capture
    TIER_5_INSTRUMENTAL      // Pure instrumental or unfound track
}

/**
 * Real-time state of the lyrics synchronization engine.
 */
data class SyncPlaybackState(
    val activeLineIndex: Int = -1,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val tier: SyncResolutionTier = SyncResolutionTier.TIER_2_LINE_BY_LINE,
    val userOffsetMs: Long = 0L,
    val lines: List<DualLanguageLyricLine> = emptyList()
)

/**
 * High-performance, low-garbage collector lyrics synchronization engine
 * supporting multi-tier fallbacks, heuristic pacing for unsynced lyrics, and
 * bi-directional interactive seeking.
 */
class BiDirectionalSyncEngine {

    private val _syncState = MutableStateFlow(SyncPlaybackState())
    val syncState: StateFlow<SyncPlaybackState> = _syncState.asStateFlow()

    private var activeLines: List<DualLanguageLyricLine> = emptyList()
    private var totalDurationMs: Long = 0L
    private var currentUserOffsetMs: Long = 0L

    /**
     * Loads synchronized lines (Tier 1 or Tier 2).
     */
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
     * Intensive Fallback: Takes plain, unsynced text lyrics and synthesizes
     * estimated timestamps based on word counts and track duration (Tier 3).
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

        // Reserve 8% of duration for instrumental intro and 5% for outro
        val introTimeMs = (durationMs * 0.08).toLong().coerceAtLeast(3000L)
        val outroTimeMs = (durationMs * 0.05).toLong().coerceAtLeast(3000L)
        val singingWindowMs = (durationMs - introTimeMs - outroTimeMs).coerceAtLeast(10000L)

        // Weight lines by word length for realistic speech cadence pacing
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

    /**
     * Updates playback position and evaluates the currently active line via binary search.
     * Called by the high-precision ticker coroutine.
     */
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

    /**
     * Nudges calibration offset for this song.
     */
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
     * Bi-Directional Control: Computes target seek timestamp when a user taps
     * on any specific line in the overlay.
     */
    fun calculateSeekTimestampForLine(lineIndex: Int): Long? {
        if (lineIndex !in activeLines.indices) return null
        val targetLine = activeLines[lineIndex]
        // Subtract user offset to map accurately back to media track position
        return (targetLine.timestampMs - currentUserOffsetMs).coerceAtLeast(0L)
    }

    /**
     * High-speed O(log N) lookup for active line.
     */
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
