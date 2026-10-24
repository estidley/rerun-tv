package com.rerun.tv.ui

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.rerun.tv.RerunApp
import com.rerun.tv.player.TunerEngine
import com.rerun.tv.ui.channels.ChannelEditor
import com.rerun.tv.ui.channels.ChannelManager
import com.rerun.tv.ui.crt.CrtLevel
import com.rerun.tv.ui.crt.CrtOverlay
import com.rerun.tv.ui.crt.PowerTransition
import com.rerun.tv.ui.crt.ProvideCrtLevel
import com.rerun.tv.ui.guide.GuideScreen
import com.rerun.tv.ui.onboarding.OnboardingScreen
import com.rerun.tv.ui.player.PlayerScreen
import com.rerun.tv.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    private lateinit var controller: TvController
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stoppedAtMs = 0L
    private var okDownAt = 0L
    private var okLongFired = false
    private var leftDownAt = 0L
    private var leftLongFired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val app = application as RerunApp
        controller = TvController(app, TunerEngine(this, app.schedule, app.settings, app.http))
        mediaSession = runCatching {
            MediaSession(this, "RerunTV").apply {
                setPlaybackState(
                    PlaybackState.Builder()
                        .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                        .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                        .build(),
                )
            }
        }.getOrNull()
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm?.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "rerun:tv-playback",
        )?.apply { setReferenceCounted(false) }
        setContent { RerunRoot(controller, onPoweredOff = { finish() }, onPlayerKeyEvent = ::handlePlayerKeyEvent) }
    }

    override fun onStart() {
        super.onStart()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching {
            mediaSession?.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .build(),
            )
            mediaSession?.isActive = true
        }
        runCatching { wakeLock?.acquire() }
        val sleptLongEnough = stoppedAtMs > 0L && System.currentTimeMillis() - stoppedAtMs >= SLEEP_RANDOMIZE_MS
        stoppedAtMs = 0L
        if (controller.screen.value != Screen.Onboarding) {
            if (sleptLongEnough) controller.startWatching(randomize = true) else controller.resume()
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAtMs = System.currentTimeMillis()
        runCatching { mediaSession?.isActive = false }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        controller.engine.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { mediaSession?.release() }
        mediaSession = null
        controller.engine.release()
        controller.dispose()
    }

    /** Remote-control routing while watching. Other screens use normal D-pad focus. */
    private fun handlePlayerKeyEvent(event: KeyEvent): Boolean {
        if (controller.screen.value != Screen.Player) return false
        val code = event.keyCode
        val isOk = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
        if (event.action == KeyEvent.ACTION_DOWN) {
            when {
                isOk -> {
                    if (event.repeatCount == 0) { okDownAt = event.eventTime; okLongFired = false }
                    else if (!okLongFired && event.eventTime - okDownAt >= 2_000) { okLongFired = true; controller.cycleCrt() }
                    return true
                }
                code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.repeatCount == 0) { leftDownAt = event.eventTime; leftLongFired = false }
                    else if (!leftLongFired && event.eventTime - leftDownAt >= 2_000) {
                        leftLongFired = true
                        controller.startEpisodeOver()
                    }
                    return true
                }
                event.repeatCount > 0 -> return true
                code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_CHANNEL_UP -> controller.channelUp()
                code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_CHANNEL_DOWN -> controller.channelDown()
                code == KeyEvent.KEYCODE_LAST_CHANNEL -> controller.lastChannel()
                code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_INFO -> controller.showBanner()
                code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_GUIDE -> controller.go(Screen.Guide)
                else -> return false
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_UP) {
            if (isOk) { if (!okLongFired) controller.showBanner(); return true }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (!leftLongFired) controller.lastChannel()
                return true
            }
            if (code in HANDLED) return true
        }
        return false
    }

    private companion object {
        const val SLEEP_RANDOMIZE_MS = 15 * 60_000L
        val HANDLED = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE,
        )
    }
}

@Composable
fun RerunRoot(controller: TvController, onPoweredOff: () -> Unit, onPlayerKeyEvent: (KeyEvent) -> Boolean) {
    val screen by controller.screen.collectAsState()
    val crt by controller.crtLevel.collectAsState()
    val exiting by controller.exiting.collectAsState()
    val fx by controller.effects.collectAsState()
    val playerFocus = remember { FocusRequester() }
    BackHandler(enabled = screen == Screen.Player) { controller.backFromPlayer() }
    LaunchedEffect(screen) {
        if (screen == Screen.Player) playerFocus.requestFocus()
    }
    val level = CrtLevel.from(crt)
    // Lay everything out on a fixed 1920×1080 canvas, whatever the TV's density.
    val view = androidx.compose.ui.platform.LocalView.current
    val canvas = androidx.compose.ui.unit.Density(density = view.resources.displayMetrics.widthPixels / 1920f, fontScale = 1f)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides canvas) {
    ProvideCrtLevel(level) { Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { onPlayerKeyEvent(it.nativeKeyEvent) }
            .focusRequester(playerFocus).focusable(enabled = screen == Screen.Player),
    ) {
        when (val s = screen) {
            Screen.Onboarding -> OnboardingScreen(controller)
            Screen.Player -> PlayerScreen(controller)
            Screen.Guide -> GuideScreen(controller)
            Screen.Channels -> ChannelManager(controller)
            is Screen.Editor -> ChannelEditor(controller, s.channelId)
            Screen.Settings -> SettingsScreen(controller)
        }
        CrtOverlay(level)
        if (fx.power) PowerTransition(on = !exiting, onOffDone = onPoweredOff)
        else if (exiting) androidx.compose.runtime.LaunchedEffect(Unit) { onPoweredOff() }
    } } }
}
