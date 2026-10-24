package com.rerun.tv.player

import android.app.Application
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.rerun.core.catalog.Lineup
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaItem
import com.rerun.core.model.Rules
import com.rerun.core.plex.PlexParse
import com.rerun.core.schedule.EntryKind
import com.rerun.core.schedule.ScheduleEntry
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.data.AdRow
import com.rerun.tv.data.PlexApi
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.data.Settings
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.mockito.ArgumentCaptor
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TunerEngineTest {
    private lateinit var engine: TunerEngine
    private lateinit var player: ExoPlayer
    private lateinit var repo: ScheduleRepo
    private lateinit var settings: Settings
    private lateinit var api: PlexApi
    private lateinit var listener: Player.Listener
    private val sources = mutableListOf<MediaSource>()
    private var wallTime: Long? = null

    @Before
    fun setUp() = runBlocking {
        player = mock(ExoPlayer::class.java)
        repo = mock(ScheduleRepo::class.java)
        settings = mock(Settings::class.java)
        api = mock(PlexApi::class.java)
        `when`(player.trackSelectionParameters).thenReturn(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT)
        `when`(player.currentTracks).thenReturn(Tracks.EMPTY)
        `when`(settings.serverBase()).thenReturn("http://127.0.0.1:32400")
        `when`(settings.isOn(Settings.SUBTITLES)).thenReturn(false)
        `when`(settings.subtitleLanguageValue()).thenReturn("auto")
        `when`(settings.serverToken).thenReturn("test-only-placeholder")
        `when`(settings.clientId).thenReturn("test-client")
        doAnswer { listener = it.getArgument(0); null }.`when`(player).addListener(any(Player.Listener::class.java))
        doAnswer { sources += it.getArgument<MediaSource>(0); null }.`when`(player).setMediaSource(any(MediaSource::class.java))
        engine = TunerEngine(
            RuntimeEnvironment.getApplication(), repo, settings, OkHttpClient(), api,
            clock = { wallTime ?: System.currentTimeMillis() },
        ) { player }
    }

    @After
    fun tearDown() = engine.release()

    @Test
    fun audioBecomesPlayingWithoutAVideoFrame() = runBlocking {
        val item = PlexParse.tracks(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"1","title":"Song","duration":240000,"Genre":[{"tag":"Jazz"}],"Media":[{"container":"mp3","Part":[{"key":"/library/parts/1/file.mp3"}]}]}]}}""",
        ).single()
        prepare("music:jazz", item)
        engine.tune("music:jazz")
        await { sources.size == 1 }
        assertEquals(Phase.TUNING, engine.state.value.phase)
        listener.onPlaybackStateChanged(Player.STATE_READY)
        assertEquals(Phase.PLAYING, engine.state.value.phase)
        listener.onPlaybackStateChanged(Player.STATE_ENDED)
        assertEquals(Phase.UP_NEXT, engine.state.value.phase)
    }

    @Test
    fun restartedTranscodeDoesNotReturnToZeroOnRetune() = runBlocking {
        val item = PlexParse.episodes(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"1","title":"Episode","duration":1800000}]}}""",
            emptyMap(),
        ).single()
        prepare(Lineup.RESTART_ID, item)
        engine.armReturn("east")
        engine.tune(Lineup.RESTART_ID)
        await { sources.size == 1 }
        assertTrue(sources[0].mediaItem.localConfiguration!!.uri.toString().contains("&offset=0&"))
        engine.tune(Lineup.RESTART_ID)
        await { sources.size == 2 }
        val offset = sources[1].mediaItem.localConfiguration!!.uri.getQueryParameter("offset")!!.toLong()
        assertTrue(offset >= 60)
    }

    @Test
    fun restartReturnsOnPlaybackEndRatherThanCuttingOffAtMetadataDeadline() = runBlocking {
        val item = PlexParse.episodes(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"1","title":"Episode","duration":1800000}]}}""",
            emptyMap(),
        ).single()
        prepare(Lineup.RESTART_ID, item)
        var returned: String? = null
        engine.onReturned = { returned = it }
        engine.armReturn("east")
        engine.tune(Lineup.RESTART_ID)
        await { sources.size == 1 }
        listener.onRenderedFirstFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(11))
        assertNull(returned)
        listener.onPlaybackStateChanged(Player.STATE_ENDED)
        assertEquals("east", returned)
    }

    @Test
    fun togglingSubtitlesLoadsAndRemovesExternalTextDuringDirectPlay() = runBlocking {
        val metadata = videoWithSubtitle("srt")
        prepare("east", PlexParse.movies(metadata).single())
        `when`(api.get("http://127.0.0.1:32400", "/library/metadata/1", "test-only-placeholder")).thenReturn(metadata)
        engine.tune("east")
        await { sources.size == 1 }
        assertTrue(sources[0].mediaItem.localConfiguration!!.subtitleConfigurations.isEmpty())

        `when`(settings.isOn(Settings.SUBTITLES)).thenReturn(true)
        engine.setSubtitles(true)
        await { sources.size == 2 }
        val subtitle = sources[1].mediaItem.localConfiguration!!.subtitleConfigurations.single()
        assertEquals("http://127.0.0.1:32400/library/streams/7", subtitle.uri.toString())
        assertEquals("application/x-subrip", subtitle.mimeType)

        `when`(settings.isOn(Settings.SUBTITLES)).thenReturn(false)
        engine.setSubtitles(false)
        await { sources.size == 3 }
        assertTrue(sources[2].mediaItem.localConfiguration!!.subtitleConfigurations.isEmpty())
    }

    @Test
    fun imageSubtitlesForceBurnInWithTheSelectedStream() = runBlocking {
        val metadata = videoWithSubtitle("pgs")
        prepare("east", PlexParse.movies(metadata).single())
        `when`(api.get("http://127.0.0.1:32400", "/library/metadata/1", "test-only-placeholder")).thenReturn(metadata)
        `when`(settings.isOn(Settings.SUBTITLES)).thenReturn(true)
        engine.tune("east")
        await { sources.size == 1 }
        val uri = sources.single().mediaItem.localConfiguration!!.uri
        assertEquals("burn", uri.getQueryParameter("subtitles"))
        assertEquals("7", uri.getQueryParameter("subtitleStreamID"))
    }

    @Test
    fun preferredLanguageSelectsExternalTrackInsteadOfPlexDefault() = runBlocking {
        val metadata = videoWithSubtitle("srt").replace(
            """"selected":true}""",
            """"languageCode":"eng","selected":true},{"id":8,"streamType":3,"codec":"srt","key":"/library/streams/8","languageCode":"spa"}""",
        )
        prepare("east", PlexParse.movies(metadata).single())
        `when`(settings.isOn(Settings.SUBTITLES)).thenReturn(true)
        `when`(settings.subtitleLanguageValue()).thenReturn("es")
        `when`(api.get("http://127.0.0.1:32400", "/library/metadata/1", "test-only-placeholder")).thenReturn(metadata)
        engine.tune("east")
        await { sources.size == 1 }
        val subtitle = sources.single().mediaItem.localConfiguration!!.subtitleConfigurations.single()
        assertEquals("http://127.0.0.1:32400/library/streams/8", subtitle.uri.toString())
        assertEquals("spa", subtitle.language)
        val englishTracks = Tracks(listOf(Tracks.Group(
            TrackGroup(Format.Builder().setSampleMimeType("application/x-subrip").setLanguage("en").build()),
            false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false),
        )))
        listener.onTracksChanged(englishTracks)
        val parameters = ArgumentCaptor.forClass(TrackSelectionParameters::class.java)
        verify(player, atLeastOnce()).trackSelectionParameters = parameters.capture()
        assertTrue(C.TRACK_TYPE_TEXT in parameters.value.disabledTrackTypes)

        val spanishTracks = Tracks(listOf(Tracks.Group(
            TrackGroup(Format.Builder().setSampleMimeType("application/x-subrip").setLanguage("spa").build()),
            false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false),
        )))
        listener.onTracksChanged(spanishTracks)
        verify(player, atLeastOnce()).trackSelectionParameters = parameters.capture()
        assertFalse(C.TRACK_TYPE_TEXT in parameters.value.disabledTrackTypes)
        assertTrue(parameters.value.overrides.isNotEmpty())
    }

    @Test
    fun unavailablePreferredLanguageDoesNotBurnAnotherLanguage() = runBlocking {
        val metadata = videoWithSubtitle("pgs")
        prepare("east", PlexParse.movies(metadata).single().copy(audioCodec = "dca"))
        `when`(settings.isOn(Settings.SUBTITLES)).thenReturn(true)
        `when`(settings.subtitleLanguageValue()).thenReturn("ja")
        `when`(api.get("http://127.0.0.1:32400", "/library/metadata/1", "test-only-placeholder")).thenReturn(metadata)
        engine.tune("east")
        await { sources.size == 1 }
        assertEquals("none", sources.single().mediaItem.localConfiguration!!.uri.getQueryParameter("subtitles"))
    }

    @Test
    fun clearingPlaybackRemovesAnExcludedChannelFromState() = runBlocking {
        prepare("east", PlexParse.movies(videoWithSubtitle("srt")).single())
        engine.tune("east")
        await { sources.size == 1 }
        engine.clear()
        assertNull(engine.state.value.channel)
        assertNull(engine.state.value.item)
        assertEquals(Phase.OFF_AIR, engine.state.value.phase)
    }

    @Test
    fun adsSeekIntoTheBreakAndRollOverWithoutUnmutingMenus() = runBlocking {
        val ad = PlexParse.movies(videoWithSubtitle("srt")).single().copy(key = "ad", durationMs = 30_000)
        val now = System.currentTimeMillis()
        wallTime = now
        prepare("east", ad)
        `when`(repo.adCatalog()).thenReturn(listOf(AdRow.of(ad)))
        `when`(repo.entries(anyString(), anyLong(), anyLong())).thenReturn(listOf(
            ScheduleEntry("east", now - 10_000, now + 55_000, EntryKind.FILLER, null),
            ScheduleEntry("east", now + 55_000, now + 600_000, EntryKind.PROGRAM, ad.key),
        ))
        engine.setVisible(false)
        engine.tune("east")
        await { sources.size == 1 }
        assertEquals(EntryKind.FILLER, engine.state.value.located!!.entry.kind)
        assertEquals("ad", engine.state.value.item!!.key)
        val seeks = ArgumentCaptor.forClass(Long::class.javaObjectType)
        verify(player).seekTo(seeks.capture())
        assertTrue(seeks.value in 10_000L..15_000L)
        wallTime = now + 21_000
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21))
        await { sources.size == 2 }
        wallTime = now + 51_000
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        await { engine.state.value.phase == Phase.UP_NEXT }
        wallTime = now + 56_000
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        await { sources.size == 3 }
        assertEquals(EntryKind.PROGRAM, engine.state.value.located!!.entry.kind)
        verify(player, never()).volume = 1f
        engine.setVisible(true)
        verify(player).volume = 1f
    }

    @Test
    fun emptyAdCatalogKeepsUpNextAndUnsupportedCodecsUseTranscoding() = runBlocking {
        val ad = PlexParse.movies(videoWithSubtitle("srt")).single().copy(key = "ad", durationMs = 30_000, audioCodec = "dca")
        prepare("east", ad)
        val now = System.currentTimeMillis()
        `when`(repo.entries(anyString(), anyLong(), anyLong())).thenReturn(listOf(
            ScheduleEntry("east", now - 10_000, now + 50_000, EntryKind.FILLER, null),
        ))
        `when`(repo.adCatalog()).thenReturn(emptyList())
        engine.tune("east")
        await { engine.state.value.phase == Phase.UP_NEXT }
        assertTrue(sources.isEmpty())
        `when`(repo.adCatalog()).thenReturn(listOf(AdRow.of(ad)))
        engine.tune("east")
        await { sources.size == 1 }
        assertTrue(sources.single().mediaItem.localConfiguration!!.uri.path!!.endsWith("start.m3u8"))
    }

    @Test
    fun failedAdDoesNotCancelTheNextProgramsDeadline() = runBlocking {
        val ad = PlexParse.movies(videoWithSubtitle("srt")).single().copy(key = "ad", durationMs = 10_000)
        val now = System.currentTimeMillis()
        wallTime = now
        prepare("east", ad)
        `when`(repo.adCatalog()).thenReturn(listOf(AdRow.of(ad)))
        `when`(repo.entries(anyString(), anyLong(), anyLong())).thenReturn(listOf(
            ScheduleEntry("east", now, now + 10_000, EntryKind.FILLER, null),
            ScheduleEntry("east", now + 10_000, now + 600_000, EntryKind.PROGRAM, ad.key),
        ))
        engine.tune("east")
        await { sources.size == 1 }
        listener.onPlayerError(PlaybackException("Test failure", null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        assertEquals(Phase.DIFFICULTIES, engine.state.value.phase)
        wallTime = now + 11_000
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        await { sources.size == 2 }
        assertEquals(EntryKind.PROGRAM, engine.state.value.located!!.entry.kind)
    }

    private fun videoWithSubtitle(codec: String): String =
        """{"MediaContainer":{"Metadata":[{"ratingKey":"1","title":"Video","duration":1800000,
          "Media":[{"container":"mp4","videoCodec":"h264","audioCodec":"aac","Part":[
            {"key":"/library/parts/1/file.mp4","Stream":[
              {"id":7,"streamType":3,"codec":"$codec","key":"/library/streams/7","selected":true}
            ]}
          ]}]}]}}"""

    private suspend fun prepare(id: String, item: MediaItem) {
        val row = ChannelRow.of(ChannelDef(id, 1, "Test", ChannelKind.CUSTOM, Rules()), false, false)
        val now = System.currentTimeMillis()
        `when`(repo.channel(id)).thenReturn(row)
        `when`(repo.item(item.key)).thenReturn(item)
        `when`(repo.entries(anyString(), anyLong(), anyLong())).thenReturn(
            listOf(ScheduleEntry(id, now - 60_000, now + 600_000, EntryKind.PROGRAM, item.key)),
        )
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Tuner did not finish preparing", condition())
    }
}
