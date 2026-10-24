package com.rerun.core.plex

import com.rerun.core.movie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDetailsTest {
    private fun metadata(video: String, media: String = "") =
        """{"MediaContainer":{"Metadata":[{"Media":[{"videoCodec":"hevc",$media"Part":[{"Stream":[$video]}]}]}]}}"""

    @Test
    fun parsesHdrTransferAndDolbyVisionWithoutTreatingAllTenBitVideoAsHdr() {
        assertEquals(DynamicRange.HDR10, PlexParse.videoDetails(metadata("""{"streamType":1,"colorTrc":"smpte2084"}""")).range)
        assertEquals(DynamicRange.HLG, PlexParse.videoDetails(metadata("""{"streamType":1,"colorTrc":"arib-std-b67"}""")).range)
        assertEquals(DynamicRange.SDR, PlexParse.videoDetails(metadata("""{"streamType":1,"bitDepth":10}""")).range)
        assertEquals(DynamicRange.HDR10, PlexParse.videoDetails(metadata("", """"videoDynamicRange":"HDR",""")).range)
        val dolby = PlexParse.videoDetails(metadata("""{"streamType":1,"DOVIPresent":true,"DOVIProfile":5}"""))
        assertEquals(DynamicRange.DOLBY_VISION, dolby.range)
        assertEquals(5, dolby.dolbyVisionProfile)
        assertFalse(dolby.hdr10Compatible)
        assertFalse(PlexParse.videoDetails(metadata("""{"streamType":1,"DOVIProfile":8,"DOVIBLCompatID":6}""")).hdr10Compatible)
    }

    @Test
    fun hdrPolicyPreservesSdrAndSupportedHdrButHonorsTheConversionOverride() {
        val hdr = VideoDetails(range = DynamicRange.HDR10)
        assertTrue(requiresSdr(hdr, HdrMode.AUTO, false))
        assertFalse(requiresSdr(hdr, HdrMode.AUTO, true))
        assertTrue(requiresSdr(hdr, HdrMode.CONVERT, true))
        assertFalse(requiresSdr(hdr, HdrMode.NATIVE, false))
        assertFalse(requiresSdr(VideoDetails(), HdrMode.CONVERT, false))
    }

    @Test
    fun audioSelectionUsesLanguageAliasesAndFallsBackToPlexDefault() {
        val audio = PlexParse.videoDetails(metadata(
            """{"streamType":2,"id":1,"languageCode":"eng","codec":"aac","channels":2,"selected":1,"displayTitle":"English"},
            {"streamType":2,"id":2,"languageCode":"fre","codec":"ac3","channels":6,"title":"French commentary"}""",
        )).audio
        assertEquals(1, selectAudio(audio, "auto")!!.id)
        assertEquals(2, selectAudio(audio, "fr")!!.id)
        assertEquals(1, selectAudio(audio, "ja")!!.id)
        assertEquals("French commentary", audio[1].title)
    }

    @Test
    fun sdrConversionCannotRemuxHdrAndAudioSelectionDoesNotNeedFullVideoConversion() {
        val sdr = planPlayback(movie("1"), "http://127.0.0.1:32400", "c", "s", 60_000, forceSdr = true)
        assertTrue(sdr is PlaybackPlan.Transcoded)
        assertTrue("directStream=0&" in sdr.url)
        assertTrue("videoCodec=h264&" in sdr.url)
        assertTrue("videoBitDepth=8&" in sdr.url)
        val audio = planPlayback(movie("1"), "http://127.0.0.1:32400", "c", "s", 60_000, audioStreamId = 9)
        assertTrue(audio is PlaybackPlan.Transcoded)
        assertTrue("audioStreamID=9&" in audio.url)
        assertTrue("directStream=1&" in audio.url)
    }
}
