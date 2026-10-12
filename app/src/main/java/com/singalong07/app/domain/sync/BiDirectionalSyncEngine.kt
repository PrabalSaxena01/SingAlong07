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
    val lines: List<DualLanguageLyricLine> = emptyList(),
    val isLoading: Boolean = false,
    val lyricsNotFound: Boolean = false,
    val sourceName: String? = null
)

class BiDirectionalSyncEngine {

    private val _syncState = MutableStateFlow(SyncPlaybackState())
    val syncState: StateFlow<SyncPlaybackState> = _syncState.asStateFlow()

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow.asStateFlow()

    private val _sourceNameFlow = MutableStateFlow<String?>(null)
    val sourceNameFlow: StateFlow<String?> = _sourceNameFlow.asStateFlow()

    private var activeLines: List<DualLanguageLyricLine> = emptyList()
    private var totalDurationMs: Long = 0L
    private var currentUserOffsetMs: Long = 0L

    fun startLoadingLyrics(durationMs: Long = 0L) {
        this.activeLines = emptyList()
        this.totalDurationMs = durationMs
        _sourceNameFlow.value = null
        _syncState.update {
            it.copy(
                lines = emptyList(),
                durationMs = durationMs,
                isLoading = true,
                lyricsNotFound = false,
                sourceName = null,
                activeLineIndex = -1
            )
        }
    }

    fun setLyricsNotFound(durationMs: Long = 0L) {
        this.activeLines = emptyList()
        this.totalDurationMs = durationMs
        _sourceNameFlow.value = null
        _syncState.update {
            it.copy(
                lines = emptyList(),
                durationMs = durationMs,
                isLoading = false,
                lyricsNotFound = true,
                sourceName = null,
                tier = SyncResolutionTier.TIER_5_INSTRUMENTAL,
                activeLineIndex = -1
            )
        }
    }

    fun loadSyncedLyrics(
        lines: List<DualLanguageLyricLine>,
        durationMs: Long,
        isWordByWord: Boolean = false,
        sourceName: String? = null
    ) {
        this.activeLines = lines
        this.totalDurationMs = durationMs
        val tier = if (isWordByWord) SyncResolutionTier.TIER_1_WORD_BY_WORD else SyncResolutionTier.TIER_2_LINE_BY_LINE
        _sourceNameFlow.value = sourceName

        _syncState.update {
            it.copy(
                lines = lines,
                durationMs = durationMs,
                tier = tier,
                isLoading = false,
                lyricsNotFound = lines.isEmpty(),
                sourceName = sourceName,
                activeLineIndex = -1
            )
        }
    }

    /**
     * Intensive Fallback: Generates synthetic line timings based on word counts
     * and track duration when only un-synced plain lyrics are available.
     */
    fun loadPlainLyricsWithPacingFallback(
        plainText: String,
        durationMs: Long,
        sourceName: String? = null
    ) {
        val safeDuration = if (durationMs > 0L) durationMs else 180000L
        this.totalDurationMs = safeDuration
        val rawLines = plainText.lines().map { it.trim() }.filter { it.isNotBlank() }

        if (rawLines.isEmpty()) {
            _sourceNameFlow.value = null
            _syncState.update {
                it.copy(
                    lines = emptyList(),
                    tier = SyncResolutionTier.TIER_5_INSTRUMENTAL,
                    isLoading = false,
                    lyricsNotFound = true,
                    sourceName = null
                )
            }
            return
        }

        val introTimeMs = (safeDuration * 0.08).toLong().coerceAtLeast(3000L)
        val outroTimeMs = (safeDuration * 0.05).toLong().coerceAtLeast(3000L)
        val singingWindowMs = (safeDuration - introTimeMs - outroTimeMs).coerceAtLeast(10000L)

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
        _sourceNameFlow.value = sourceName
        _syncState.update {
            it.copy(
                lines = estimatedLines,
                durationMs = safeDuration,
                tier = SyncResolutionTier.TIER_3_HEURISTIC_PACING,
                isLoading = false,
                lyricsNotFound = false,
                sourceName = sourceName,
                activeLineIndex = -1
            )
        }
    }

    fun updatePlaybackPosition(mediaPositionMs: Long, isPlaying: Boolean) {
        val effectivePosition = mediaPositionMs + currentUserOffsetMs
        val lineIndex = binarySearchActiveLine(effectivePosition)

        if (_isPlayingFlow.value != isPlaying) {
            _isPlayingFlow.value = isPlaying
        }

        val current = _syncState.value
        val lineChanged = current.activeLineIndex != lineIndex
        val playStateChanged = current.isPlaying != isPlaying
        val posDelta = kotlin.math.abs(mediaPositionMs - current.currentPositionMs)
        val posChanged = posDelta >= 200L

        if (lineChanged || playStateChanged || posChanged) {
            _syncState.update {
                it.copy(
                    currentPositionMs = mediaPositionMs,
                    isPlaying = isPlaying,
                    activeLineIndex = lineIndex,
                    userOffsetMs = currentUserOffsetMs
                )
            }
        }
    }

    fun adjustOffset(deltaMs: Long): Long {
        currentUserOffsetMs += deltaMs
        val effectivePosition = _syncState.value.currentPositionMs + currentUserOffsetMs
        val lineIndex = binarySearchActiveLine(effectivePosition)
        _syncState.update {
            it.copy(
                userOffsetMs = currentUserOffsetMs,
                activeLineIndex = lineIndex
            )
        }
        return currentUserOffsetMs
    }

    fun resetOffset(): Long {
        currentUserOffsetMs = 0L
        val effectivePosition = _syncState.value.currentPositionMs
        val lineIndex = binarySearchActiveLine(effectivePosition)
        _syncState.update {
            it.copy(
                userOffsetMs = 0L,
                activeLineIndex = lineIndex
            )
        }
        return 0L
    }

    fun setOffset(offsetMs: Long) {
        currentUserOffsetMs = offsetMs
        val effectivePosition = _syncState.value.currentPositionMs + currentUserOffsetMs
        val lineIndex = binarySearchActiveLine(effectivePosition)
        _syncState.update {
            it.copy(
                userOffsetMs = currentUserOffsetMs,
                activeLineIndex = lineIndex
            )
        }
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
