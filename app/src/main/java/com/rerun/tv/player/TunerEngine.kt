package com.rerun.tv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import okhttp3.OkHttpClient
import com.rerun.core.catalog.Lineup
import com.rerun.core.model.MediaItem
import com.rerun.core.plex.PlaybackPlan
import com.rerun.core.plex.TokenRedaction
import com.rerun.core.plex.planPlayback
import com.rerun.core.schedule.EntryKind
import com.rerun.core.schedule.Located
import com.rerun.core.schedule.locate
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Phase { TUNING, PLAYING, UP_NEXT, OFF_AIR, DIFFICULTIES }

data class TunerState(
    val channel: ChannelRow? = null,
    val located: Located? = null,
    val item: MediaItem? = null,
    val nextItem: MediaItem? = null,
    /** When nothing is on yet, the wall-clock time the next entry starts. */
    val holdUntilMs: Long? = null,
    val phase: Phase = Phase.TUNING,
    /** Bumps on every channel change so overlays can replay their entrance. */
    val tuneId: Long = 0,
)

/**
 * "Turn to channel X": find what's on now, start it at the right spot, and roll over to the
 * next entry when this one ends. One ExoPlayer for the app's lifetime.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TunerEngine(
    context: Context,
    private val repo: ScheduleRepo,
    private val settings: Settings,
    httpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val http = ResolvingDataSource.Factory(
        OkHttpDataSource.Factory(httpClient),
        object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec = withoutQueryToken(dataSpec)

            override fun resolveReportedUri(uri: Uri): Uri = withoutQueryToken(uri)
        },
    )
    private val session = UUID.randomUUID().toString()
    private var tuneJob: Job? = null
    private var rollover: Runnable? = null
    private var retry: Runnable? = null
    private var tuneSeq = 0L
    private var subtitlesOn = false
    private var transcoded = false
    private var restartReturn: String? = null

    /** Called when the hidden start-over channel finishes and the TV should go back. */
    var onReturned: ((String) -> Unit)? = null

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state.asStateFlow()

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (_state.value.phase == Phase.TUNING) _state.value = _state.value.copy(phase = Phase.PLAYING)
                }

                override fun onPlayerError(error: PlaybackException) = difficulties()

                override fun onTracksChanged(tracks: Tracks) = applySubtitles(tracks)

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_ENDED || _state.value.phase != Phase.PLAYING) return
                    // The start-over channel is a one-shot. When the episode ends, go back.
                    if (_state.value.channel?.id == Lineup.RESTART_ID) {
                        leaveRestart()
                        return
                    }
                    // File ended early (duration in Plex was longer than the real file): wait on the card.
                    _state.value = _state.value.copy(phase = Phase.UP_NEXT)
                }
            })
        }

    fun player(): ExoPlayer = player

    /** Remember which channel to restore when the start-over episode ends. */
    fun armReturn(channelId: String) {
        restartReturn = channelId
    }

    /** Subtitle switch. Direct play selects a text track; a transcode has to be requested again. */
    fun setSubtitles(on: Boolean) {
        subtitlesOn = on
        applySubtitles(player.currentTracks)
        val id = _state.value.channel?.id ?: return
        if (transcoded && _state.value.phase != Phase.DIFFICULTIES) tune(id)
    }

    fun tune(channelId: String) {
        if (channelId != Lineup.RESTART_ID) restartReturn = null
        tuneJob?.cancel()
        clearTimers()
        transcoded = false
        val seq = ++tuneSeq
        tuneJob = scope.launch {
            val t0 = System.currentTimeMillis()
            val channel = repo.channel(channelId)
            _state.value = TunerState(channel = channel, phase = Phase.TUNING, tuneId = seq)
            if (channel == null) return@launch difficulties()
            if (channelId != Lineup.RESTART_ID) settings.setLastChannel(channelId)
            try {
                val subs = withContext(Dispatchers.IO) {
                    repo.ensure(channelId, t0)
                    settings.isOn(Settings.SUBTITLES)
                }
                subtitlesOn = subs
                val now = System.currentTimeMillis()
                val entries = withContext(Dispatchers.IO) { repo.entries(channelId, now - 1, now + 6 * 3_600_000L) }
                val loc = locate(entries, now)
                if (loc == null) {
                    val upcoming = entries.firstOrNull { it.startMs > now }
                    if (upcoming == null) return@launch difficulties()
                    // Nothing stored for right now. Off Air waits for the next entry.
                    val program = if (upcoming.kind == EntryKind.PROGRAM) upcoming
                        else entries.firstOrNull { it.startMs >= upcoming.endMs && it.kind == EntryKind.PROGRAM }
                    player.stop()
                    _state.value = _state.value.copy(
                        phase = Phase.OFF_AIR,
                        nextItem = program?.let { repo.item(it.itemKey) },
                        holdUntilMs = upcoming.startMs,
                    )
                    scheduleRollover(channelId, upcoming.startMs)
                    return@launch
                }
                val item = repo.item(loc.entry.itemKey)
                val nextItem = loc.next?.let { n ->
                    if (n.kind == EntryKind.PROGRAM) repo.item(n.itemKey)
                    else entries.firstOrNull { it.startMs >= n.endMs && it.kind == EntryKind.PROGRAM }?.let { repo.item(it.itemKey) }
                }
                _state.value = _state.value.copy(located = loc, item = item, nextItem = nextItem)
                scheduleRollover(channelId, loc.entry.endMs)

                if (loc.entry.kind == EntryKind.FILLER) {
                    player.stop()
                    _state.value = _state.value.copy(phase = Phase.UP_NEXT)
                    return@launch
                }
                val base = settings.serverBase()
                val token = settings.serverToken ?: settings.accountToken
                if (base == null || token == null) return@launch difficulties()
                // Whatever time loading took, add it back so we land exactly on schedule.
                // Start-over is pinned at 0:00 so the loading delay is not added in.
                val fromStart = channelId == Lineup.RESTART_ID
                val lag = System.currentTimeMillis() - now
                val offset = if (fromStart) 0L else loc.offsetMs + lag
                when (val plan = planPlayback(item, base, settings.clientId, session, offset, subtitlesOn)) {
                    is PlaybackPlan.Direct -> {
                        transcoded = false
                        applySubtitles(player.currentTracks)
                        player.setMediaSource(ProgressiveMediaSource.Factory(http).createMediaSource(ExoItem.fromUri(plan.url)))
                        player.prepare()
                        player.seekTo(if (fromStart) 0L else plan.startMs + (System.currentTimeMillis() - now - lag))
                    }
                    is PlaybackPlan.Transcoded -> {
                        transcoded = true
                        applySubtitles(player.currentTracks)
                        player.setMediaSource(HlsMediaSource.Factory(http).createMediaSource(ExoItem.fromUri(plan.url)))
                        player.prepare()
                    }
                    is PlaybackPlan.UpNext -> {
                        transcoded = false
                        player.stop()
                        _state.value = _state.value.copy(phase = Phase.UP_NEXT)
                    }
                }
            } catch (e: Exception) {
                difficulties()
            }
        }
    }

    /** Re-tune the same channel the moment the current entry ends. Start-over goes back instead. */
    private fun scheduleRollover(channelId: String, endMs: Long) {
        val r = Runnable { if (channelId == Lineup.RESTART_ID) leaveRestart() else tune(channelId) }
        rollover = r
        handler.postDelayed(r, (endMs - System.currentTimeMillis()).coerceAtLeast(0))
    }

    private fun leaveRestart() {
        val back = restartReturn ?: return
        restartReturn = null
        clearTimers()
        onReturned?.invoke(back)
        tune(back)
    }

    private fun applySubtitles(tracks: Tracks) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (subtitlesOn && !transcoded) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
            if (group != null) builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        } else {
            // Off, or already burned into a transcode.
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        }
        player.trackSelectionParameters = builder.build()
    }

    private fun difficulties() {
        player.stop()
        _state.value = _state.value.copy(phase = Phase.DIFFICULTIES)
        val id = _state.value.channel?.id ?: return
        clearTimers()
        val r = Runnable { tune(id) }
        retry = r
        handler.postDelayed(r, RETRY_MS)
    }

    private fun clearTimers() {
        rollover?.let(handler::removeCallbacks)
        retry?.let(handler::removeCallbacks)
        rollover = null
        retry = null
    }

    fun stop() {
        tuneJob?.cancel()
        clearTimers()
        player.stop()
    }

    fun release() {
        stop()
        scope.cancel()
        player.release()
    }

    private companion object {
        const val RETRY_MS = 15_000L

        fun withoutQueryToken(dataSpec: DataSpec): DataSpec {
            val clean = withoutQueryToken(dataSpec.uri)
            return if (clean == dataSpec.uri) dataSpec else dataSpec.withUri(clean)
        }

        fun withoutQueryToken(uri: Uri): Uri {
            val raw = uri.toString()
            val clean = TokenRedaction.stripQuery(raw)
            return if (clean == raw) uri else Uri.parse(clean)
        }
    }
}
