package com.rerun.tv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem as ExoItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.rerun.core.model.MediaItem
import com.rerun.core.plex.PlaybackPlan
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

enum class Phase { TUNING, PLAYING, UP_NEXT, DIFFICULTIES }

data class TunerState(
    val channel: ChannelRow? = null,
    val located: Located? = null,
    val item: MediaItem? = null,
    val nextItem: MediaItem? = null,
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val http = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(8_000).setReadTimeoutMs(15_000).setAllowCrossProtocolRedirects(true)
    private val session = UUID.randomUUID().toString()
    private var tuneJob: Job? = null
    private var rollover: Runnable? = null
    private var retry: Runnable? = null
    private var tuneSeq = 0L

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state.asStateFlow()

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (_state.value.phase == Phase.TUNING) _state.value = _state.value.copy(phase = Phase.PLAYING)
                }

                override fun onPlayerError(error: PlaybackException) = difficulties()

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // File ended early (duration in Plex was longer than the real file): wait on the card.
                    if (playbackState == Player.STATE_ENDED && _state.value.phase == Phase.PLAYING) {
                        _state.value = _state.value.copy(phase = Phase.UP_NEXT)
                    }
                }
            })
        }

    fun player(): ExoPlayer = player

    fun tune(channelId: String) {
        tuneJob?.cancel()
        clearTimers()
        val seq = ++tuneSeq
        tuneJob = scope.launch {
            val t0 = System.currentTimeMillis()
            val channel = repo.channel(channelId)
            _state.value = TunerState(channel = channel, phase = Phase.TUNING, tuneId = seq)
            if (channel == null) return@launch difficulties()
            settings.setLastChannel(channelId)
            try {
                withContext(Dispatchers.IO) { repo.ensure(channelId, t0) }
                val now = System.currentTimeMillis()
                val entries = withContext(Dispatchers.IO) { repo.entries(channelId, now - 1, now + 6 * 3_600_000L) }
                val loc = locate(entries, now) ?: return@launch difficulties()
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
                val lag = System.currentTimeMillis() - now
                when (val plan = planPlayback(item, base, token, settings.clientId, session, loc.offsetMs + lag)) {
                    is PlaybackPlan.Direct -> {
                        player.setMediaSource(ProgressiveMediaSource.Factory(http).createMediaSource(ExoItem.fromUri(plan.url)))
                        player.prepare()
                        player.seekTo(plan.startMs + (System.currentTimeMillis() - now - lag))
                    }
                    is PlaybackPlan.Transcoded -> {
                        player.setMediaSource(HlsMediaSource.Factory(http).createMediaSource(ExoItem.fromUri(plan.url)))
                        player.prepare()
                    }
                    is PlaybackPlan.UpNext -> {
                        player.stop()
                        _state.value = _state.value.copy(phase = Phase.UP_NEXT)
                    }
                }
            } catch (e: Exception) {
                difficulties()
            }
        }
    }

    /** Re-tune the same channel the moment the current entry ends. */
    private fun scheduleRollover(channelId: String, endMs: Long) {
        val r = Runnable { tune(channelId) }
        rollover = r
        handler.postDelayed(r, (endMs - System.currentTimeMillis()).coerceAtLeast(0))
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
    }
}
