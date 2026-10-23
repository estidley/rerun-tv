package com.rerun.core.plex

import com.rerun.core.movie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackTest {
    private val base = "https://192-168-1-20.abc.plex.direct:32400"

    @Test
    fun h264AacDirectPlays() {
        val m = movie("1002").copy(container = "mp4", videoCodec = "h264", audioCodec = "aac", partKey = "/library/parts/78/1/file.mp4")
        assertTrue(Playback.canDirectPlay(m))
        assertEquals("$base/library/parts/78/1/file.mp4?X-Plex-Token=tok", Playback.directUrl(base, m, "tok"))
    }

    @Test
    fun dtsForcesTranscode() {
        assertFalse(Playback.canDirectPlay(movie("1").copy(audioCodec = "dca")))
        assertFalse(Playback.canDirectPlay(movie("1").copy(audioCodec = "truehd")))
    }

    @Test
    fun oddContainerOrCodecForcesTranscode() {
        assertFalse(Playback.canDirectPlay(movie("1").copy(container = "avi")))
        assertFalse(Playback.canDirectPlay(movie("1").copy(videoCodec = "mpeg4")))
        assertFalse(Playback.canDirectPlay(movie("1").copy(partKey = null)))
    }

    @Test
    fun transcodeUrlCarriesOffsetSeconds() {
        val url = Playback.transcodeUrl(base, movie("1001"), "tok", "client-1", "sess-9", 2_820_500)
        assertTrue(url.startsWith("$base/video/:/transcode/universal/start.m3u8?path=%2Flibrary%2Fmetadata%2F1001&"), url)
        assertTrue("&offset=2820&" in url, url)
        assertTrue("&session=sess-9&" in url, url)
        assertTrue("X-Plex-Client-Identifier=client-1" in url, url)
        assertTrue(url.endsWith("&X-Plex-Token=tok"), url)
    }

    @Test
    fun planDirectKeepsOffsetForPlayer() {
        val plan = planPlayback(movie("1002"), base, "tok", "c", "s", 47 * 60_000L)
        assertEquals(PlaybackPlan.Direct("$base/library/parts/1002/file.mkv?X-Plex-Token=tok", 47 * 60_000L), plan)
    }

    @Test
    fun planTranscodeForDts() {
        val plan = planPlayback(movie("1001").copy(audioCodec = "dca"), base, "tok", "c", "s", 60_000)
        assertTrue(plan is PlaybackPlan.Transcoded && "offset=60&" in plan.url)
    }

    @Test
    fun missingItemPlansUpNext() =
        assertEquals(PlaybackPlan.UpNext("missing"), planPlayback(null, base, "tok", "c", "s", 0))
}
