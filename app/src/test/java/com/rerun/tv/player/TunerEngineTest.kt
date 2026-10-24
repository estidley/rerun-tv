package com.rerun.tv.player

import android.app.Application
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
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
        `when`(settings.serverToken).thenReturn("test-only-placeholder")
        `when`(settings.clientId).thenReturn("test-client")
        doAnswer { listener = it.getArgument(0); null }.`when`(player).addListener(any(Player.Listener::class.java))
        doAnswer { sources += it.getArgument<MediaSource>(0); null }.`when`(player).setMediaSource(any(MediaSource::class.java))
        engine = TunerEngine(RuntimeEnvironment.getApplication(), repo, settings, OkHttpClient(), api) { player }
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
    fun applySubtitlesPrefersRealTextTrackOverCea608() = runBlocking {
        val ceaGroup = androidx.media3.common.TrackGroup(
            "cea",
            androidx.media3.common.Format.Builder().setSampleMimeType("application/cea-608").build(),
        )
        val srtGroup = androidx.media3.common.TrackGroup(
            "srt",
            androidx.media3.common.Format.Builder()
                .setSampleMimeType("application/x-subrip")
                .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                .build(),
        )
        val tracks = Tracks(
            listOf(
                Tracks.Group(ceaGroup, false, intArrayOf(androidx.media3.common.C.FORMAT_HANDLED), booleanArrayOf(false)),
                Tracks.Group(srtGroup, false, intArrayOf(androidx.media3.common.C.FORMAT_HANDLED), booleanArrayOf(false)),
            ),
        )
        `when`(player.currentTracks).thenReturn(tracks)
        var applied: TrackSelectionParameters? = null
        doAnswer { applied = it.getArgument(0); null }.`when`(player).trackSelectionParameters = any(TrackSelectionParameters::class.java)
        engine.setSubtitles(true)
        assertNotNull(applied)
        assertTrue(applied!!.overrides.containsKey(srtGroup))
        assertFalse(applied!!.overrides.containsKey(ceaGroup))
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
