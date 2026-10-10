package com.singalong07.app

import com.singalong07.app.domain.sync.BiDirectionalSyncEngine
import com.singalong07.app.domain.sync.SyncResolutionTier
import com.singalong07.app.domain.translation.DualLanguageLyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class BiDirectionalSyncEngineTest {

    @Test
    fun testActiveLineBinarySearch() {
        val engine = BiDirectionalSyncEngine()
        val lines = listOf(
            DualLanguageLyricLine(10000L, "Line 1"),
            DualLanguageLyricLine(20000L, "Line 2"),
            DualLanguageLyricLine(30000L, "Line 3")
        )
        engine.loadSyncedLyrics(lines, 60000L)

        // Before first line
        engine.updatePlaybackPosition(5000L, isPlaying = true)
        assertEquals(-1, engine.syncState.value.activeLineIndex)

        // Exactly at first line
        engine.updatePlaybackPosition(10000L, isPlaying = true)
        assertEquals(0, engine.syncState.value.activeLineIndex)

        // Mid-way between line 1 and 2
        engine.updatePlaybackPosition(15000L, isPlaying = true)
        assertEquals(0, engine.syncState.value.activeLineIndex)

        // At line 2
        engine.updatePlaybackPosition(20000L, isPlaying = true)
        assertEquals(1, engine.syncState.value.activeLineIndex)

        // Past line 3
        engine.updatePlaybackPosition(45000L, isPlaying = true)
        assertEquals(2, engine.syncState.value.activeLineIndex)
    }

    @Test
    fun testOffsetAdjustmentAndSeekCalculation() {
        val engine = BiDirectionalSyncEngine()
        val lines = listOf(
            DualLanguageLyricLine(10000L, "Line 1"),
            DualLanguageLyricLine(20000L, "Line 2")
        )
        engine.loadSyncedLyrics(lines, 60000L)

        // Nudge offset by +1000ms
        engine.adjustOffset(1000L)

        // Position 9000ms + offset 1000ms = 10000ms (Line 1 active!)
        engine.updatePlaybackPosition(9000L, isPlaying = true)
        assertEquals(0, engine.syncState.value.activeLineIndex)

        // Calculate seek timestamp for line 1 (10000 - 1000 = 9000)
        val seekMs = engine.calculateSeekTimestampForLine(0)
        assertEquals(9000L, seekMs)
    }

    @Test
    fun testPlainLyricsCadencePacing() {
        val engine = BiDirectionalSyncEngine()
        val plainLyrics = "Line one\nLine two\nLine three"
        engine.loadPlainLyricsWithPacingFallback(plainLyrics, durationMs = 100000L)

        assertEquals(SyncResolutionTier.TIER_3_HEURISTIC_PACING, engine.syncState.value.tier)
        assertEquals(3, engine.syncState.value.lines.size)
    }
}
