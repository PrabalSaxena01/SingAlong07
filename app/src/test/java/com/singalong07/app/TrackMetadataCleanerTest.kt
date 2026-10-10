package com.singalong07.app

import com.singalong07.app.domain.cleaner.TrackMetadataCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMetadataCleanerTest {

    @Test
    fun testYouTubeNoiseStripping() {
        val raw = "Never Gonna Give You Up (Official Music Video) [Remastered 4K]"
        val cleaned = TrackMetadataCleaner.clean(raw, "Rick Astley")

        assertEquals("Never Gonna Give You Up", cleaned.title)
        assertEquals("Rick Astley", cleaned.artist)
    }

    @Test
    fun testArtistEmbeddedInTitle() {
        val raw = "Queen - Bohemian Rhapsody (Official Video)"
        val cleaned = TrackMetadataCleaner.clean(raw, null)

        assertEquals("Bohemian Rhapsody", cleaned.title)
        assertEquals("Queen", cleaned.artist)
    }

    @Test
    fun testLeadingTrackNumberAndExtension() {
        val raw = "01. Hotel California.mp3"
        val cleaned = TrackMetadataCleaner.clean(raw, "Eagles")

        assertEquals("Hotel California", cleaned.title)
    }

    @Test
    fun testTokenSortRatioSimilarity() {
        val s1 = "Bohemian Rhapsody Queen"
        val s2 = "Queen Bohemian Rhapsody"

        val score = TrackMetadataCleaner.tokenSortRatio(s1, s2)
        assertTrue("Tokens in different order should yield 1.0 similarity", score > 0.99)
    }

    @Test
    fun testBollywoodNoiseStripping() {
        val raw = "Apna Bana Le (From \"Bhediya\") | T-Series"
        val cleaned = TrackMetadataCleaner.clean(raw, "Arijit Singh")

        assertEquals("Apna Bana Le", cleaned.title)
        assertEquals("Arijit Singh", cleaned.artist)
    }
}
