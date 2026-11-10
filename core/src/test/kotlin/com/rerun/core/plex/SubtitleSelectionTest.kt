package com.rerun.core.plex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitleSelectionTest {
    private val english = SubtitleStream(1, "srt", null, "eng", true)
    private val spanish = SubtitleStream(2, "srt", "/library/streams/2", "spa", false)

    @Test
    fun automaticHonorsSelectedStreamAndFallsBackToFirst() {
        assertEquals(english, selectSubtitle(listOf(spanish, english), "auto"))
        assertEquals(spanish, selectSubtitle(listOf(spanish), "auto"))
        assertNull(selectSubtitle(emptyList(), "auto"))
    }

    @Test
    fun explicitLanguageMatchesTwoAndThreeLetterCodes() {
        assertEquals(spanish, selectSubtitle(listOf(english, spanish), "es"))
        assertEquals(spanish, selectSubtitle(listOf(english, spanish), "es-MX"))
        assertEquals(english, selectSubtitle(listOf(english, spanish), "en"))
        assertEquals("fra", subtitleLanguageCode("fre"))
        assertEquals("fra", subtitleLanguageCode("fr"))
        assertEquals("deu", subtitleLanguageCode("ger"))
        assertEquals("deu", subtitleLanguageCode("de"))
    }

    @Test
    fun missingLanguageDoesNotFallBackToWrongTrack() {
        assertNull(selectSubtitle(listOf(english, spanish), "ja"))
        assertNull(selectSubtitle(listOf(english.copy(language = null)), "en"))
    }

    @Test
    fun parsesTrackLabelsForcedFlagsAndLanguageFallbacks() {
        val tracks = PlexParse.subtitles("""{"MediaContainer":{"Metadata":[{"Media":[{"Part":[{"Stream":[
            {"id":7,"streamType":3,"codec":"srt","language":"English","title":"English (SDH)","hearingImpaired":1},
            {"id":8,"streamType":3,"codec":"ass","languageTag":"es-MX","key":"/library/streams/8","forced":true},
            {"id":9,"streamType":3,"codec":"pgs","languageCode":"eng","displayTitle":"Signs"}
        ]}]}]}]}}""")
        assertEquals(listOf(7, 8, 9), tracks.map { it.id })
        assertEquals("eng", tracks[0].language)
        assertEquals("English (SDH)", tracks[0].title)
        assertTrue(tracks[0].hearingImpaired)
        assertEquals("spa", tracks[1].language)
        assertTrue(tracks[1].forced)
        assertEquals("/library/streams/8", tracks[1].key)
        assertEquals("Signs", tracks[2].title)
    }
}
