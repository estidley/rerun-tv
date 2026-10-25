package com.rerun.core.plex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TokenRedactionTest {
    @Test
    fun leavesTextWithoutATokenAlone() {
        val text = "http://192.168.1.20:32400/library/parts/1/file.ts?offset=0"
        assertEquals(text, TokenRedaction.stripQuery(text))
    }

    @Test
    fun stripsALoneQueryToken() {
        assertEquals(
            "http://192.168.1.20/seg.ts",
            TokenRedaction.stripQuery("http://192.168.1.20/seg.ts?X-Plex-Token=abc"),
        )
    }

    @Test
    fun keepsOtherParameters() {
        assertEquals(
            "http://h/seg.ts?offset=0",
            TokenRedaction.stripQuery("http://h/seg.ts?X-Plex-Token=abc&offset=0"),
        )
        assertEquals(
            "http://h/seg.ts?offset=0",
            TokenRedaction.stripQuery("http://h/seg.ts?offset=0&X-Plex-Token=abc"),
        )
        assertEquals(
            "http://h/seg.ts?a=1&b=2",
            TokenRedaction.stripQuery("http://h/seg.ts?a=1&X-Plex-Token=abc&b=2"),
        )
    }

    @Test
    fun stripsEveryTokenInAPlaylist() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1
            http://192.168.1.20:32400/video/index.m3u8?X-Plex-Token=SECRET&mediaIndex=0
            #EXTINF:4.0,
            http://192.168.1.20:32400/seg.ts?offset=1&x-plex-token=SECRET
            #EXT-X-KEY:URI="http://192.168.1.20:32400/key?X-Plex-Token=SECRET"
        """.trimIndent()
        val cleaned = TokenRedaction.stripQuery(playlist)
        assertFalse(cleaned.contains("plex-token", ignoreCase = true), cleaned)
        assertFalse(cleaned.contains("SECRET"), cleaned)
        assertEquals(cleaned, TokenRedaction.stripQuery(cleaned))
        assertEquals(
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1
            http://192.168.1.20:32400/video/index.m3u8?mediaIndex=0
            #EXTINF:4.0,
            http://192.168.1.20:32400/seg.ts?offset=1
            #EXT-X-KEY:URI="http://192.168.1.20:32400/key"
            """.trimIndent(),
            cleaned,
        )
    }

    @Test
    fun doesNotTreatAPathSegmentAsAQueryToken() {
        val text = "http://h/library/X-Plex-Token=nope/file.ts"
        assertEquals(text, TokenRedaction.stripQuery(text))
    }
}
