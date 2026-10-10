package com.singalong07.app

import com.singalong07.app.data.db.ListeningHistoryEntity
import com.singalong07.app.data.export.HistoryCsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCsvExporterTest {

    @Test
    fun testEscapeCsvField() {
        val exporter = HistoryCsvExporter(null)

        // Simple string
        assertEquals("Queen", exporter.escapeCsvField("Queen"))

        // String with comma
        assertEquals("\"Queen, The Band\"", exporter.escapeCsvField("Queen, The Band"))

        // String with quote
        assertEquals("\"Song \"\"Special\"\"\"", exporter.escapeCsvField("Song \"Special\""))

        // String with formula injection triggers
        assertEquals("'=cmd|' /C calc'!A0", exporter.escapeCsvField("=cmd|' /C calc'!A0"))
        assertEquals("'+12345", exporter.escapeCsvField("+12345"))
    }

    @Test
    fun testCsvStringGeneration() {
        val exporter = HistoryCsvExporter(null)
        val historyList = listOf(
            ListeningHistoryEntity(
                id = 1,
                trackTitle = "Bohemian Rhapsody",
                artistName = "Queen",
                albumName = "A Night at the Opera",
                durationMs = 354000L,
                timestamp = 1700000000000L,
                sourcePackage = "com.spotify.music",
                lyricSource = "LRCLIB",
                lyricSyncType = "SYNCED_LRC",
                userOffsetMs = 500L,
                matchedViaFingerprint = false
            )
        )

        val csv = exporter.generateCsvString(historyList)
        assertTrue(csv.startsWith("ID,Timestamp,Track,Artist,Album"))
        assertTrue(csv.contains("Bohemian Rhapsody"))
        assertTrue(csv.contains("Queen"))
        assertTrue(csv.contains("com.spotify.music"))
    }
}
