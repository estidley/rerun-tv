package com.rerun.core.plex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
