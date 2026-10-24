package com.rerun.tv.ui

import android.os.Bundle
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
import com.rerun.tv.ui.settings.LibrarySettingsScreen
import com.rerun.tv.ui.settings.SubtitleLanguageScreen
import com.rerun.tv.ui.theme.ColorTheme
import com.rerun.tv.ui.theme.ProvideColorTheme

class MainActivity : ComponentActivity() {
    private lateinit var controller: TvController
    private var okDownAt = 0L
    private var okLongFired = false
    private var leftDownAt = 0L
    private var leftLongFired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val app = application as RerunApp
        controller = TvController(app, TunerEngine(this, app.schedule, app.settings, app.http))
        setContent { RerunRoot(controller, onPoweredOff = { finish() }, onPlayerKeyEvent = ::handlePlayerKeyEvent) }
    }

    override fun onStart() {
        super.onStart()
        if (controller.screen.value != Screen.Onboarding) controller.resume()
    }

    override fun onStop() {
        super.onStop()
        controller.engine.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
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
    val colorTheme by controller.colorTheme.collectAsState()
    val playerFocus = remember { FocusRequester() }
    BackHandler(enabled = screen == Screen.Player) { controller.backFromPlayer() }
    LaunchedEffect(screen) {
        if (screen == Screen.Player) playerFocus.requestFocus()
    }
    val level = CrtLevel.from(crt)
    val metrics = androidx.compose.ui.platform.LocalView.current.resources.displayMetrics
    // Menus keep their TV-scale canvas; the guide can use extra pixels on larger displays.
    val scale = minOf(metrics.widthPixels / 1920f, metrics.heightPixels / 1080f)
    val canvas = androidx.compose.ui.unit.Density(
        density = if (screen == Screen.Guide) scale.coerceAtMost(1f) else scale,
        fontScale = 1f,
    )
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides canvas) {
    ProvideColorTheme(ColorTheme.from(colorTheme)) { ProvideCrtLevel(level) { Box(
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
            Screen.Libraries -> LibrarySettingsScreen(controller)
            Screen.AdLibrary -> com.rerun.tv.ui.settings.AdLibraryScreen(controller)
            Screen.SubtitleLanguage -> SubtitleLanguageScreen(controller)
        }
        CrtOverlay(level)
        if (fx.power) PowerTransition(on = !exiting, onOffDone = onPoweredOff)
        else if (exiting) androidx.compose.runtime.LaunchedEffect(Unit) { onPoweredOff() }
    } } } }
}
