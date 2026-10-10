package com.singalong07.app

import com.singalong07.app.domain.parser.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun testStandardLrcParsing() {
        val lrc = """
            [ti:Bohemian Rhapsody]
            [ar:Queen]
            [00:10.50]Is this the real life?
            [00:15.20]Is this just fantasy?
        """.trimIndent()

        val result = LrcParser.parse(lrc)
        assertEquals("Bohemian Rhapsody", result.title)
        assertEquals("Queen", result.artist)
        assertEquals(2, result.lines.size)

        assertEquals(10500L, result.lines[0].timestampMs)
        assertEquals("Is this the real life?", result.lines[0].originalText)

        assertEquals(15200L, result.lines[1].timestampMs)
        assertEquals("Is this just fantasy?", result.lines[1].originalText)
    }

    @Test
    fun testRepeatedTimestamps() {
        val lrc = "[00:10.00][00:20.00]Repeated Chorus"
        val result = LrcParser.parse(lrc)

        assertEquals(2, result.lines.size)
        assertEquals(10000L, result.lines[0].timestampMs)
        assertEquals(20000L, result.lines[1].timestampMs)
        assertEquals("Repeated Chorus", result.lines[0].originalText)
        assertEquals("Repeated Chorus", result.lines[1].originalText)
    }

    @Test
    fun testEmptyOrInvalidLrc() {
        val result = LrcParser.parse("")
        assertTrue(result.lines.isEmpty())
    }
}
