package com.singalong07.app

import com.singalong07.app.data.sources.GeniusSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeniusSourceTest {
    @Test
    fun testGeniusLyricsFetch() = runBlocking {
        val source = GeniusSource()
        val result = source.fetchLyrics("Shape of You", "Ed Sheeran", null, null)
        assertTrue("Expected Genius fetch to succeed", result.isSuccess)
        val lyrics = result.getOrThrow()
        assertNotNull(lyrics.plainLyrics)
        assertTrue("Expected non-empty lyrics", lyrics.plainLyrics!!.length > 200)
        // Ensure translation and contributor headers are stripped
        assertFalse(lyrics.plainLyrics!!.contains("TranslationsTürkçe", ignoreCase = true))
        assertFalse(lyrics.plainLyrics!!.startsWith("Translations", ignoreCase = true))
    }
}
