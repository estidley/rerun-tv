package com.rerun.tv.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rerun.tv.RerunApp
import com.rerun.tv.player.TunerEngine
import com.rerun.tv.ui.channels.ChannelEditor
import com.rerun.tv.ui.channels.ChannelManager
import com.rerun.tv.ui.crt.CrtLevel
import com.rerun.tv.ui.crt.CrtOverlay
import com.rerun.tv.ui.crt.PowerTransition
import com.rerun.tv.ui.guide.GuideScreen
import com.rerun.tv.ui.onboarding.OnboardingScreen
import com.rerun.tv.ui.player.PlayerScreen
import com.rerun.tv.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    private lateinit var controller: TvController
    private var okDownAt = 0L
    private var okLongFired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val app = application as RerunApp
        controller = TvController(app, TunerEngine(this, app.schedule, app.settings))
        setContent { RerunRoot(controller, onPoweredOff = { finish() }) }
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
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (controller.screen.value != Screen.Player) return super.dispatchKeyEvent(event)
        val code = event.keyCode
        val isOk = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
        if (event.action == KeyEvent.ACTION_DOWN) {
            when {
                isOk -> {
                    if (event.repeatCount == 0) { okDownAt = event.eventTime; okLongFired = false }
                    else if (!okLongFired && event.eventTime - okDownAt >= 2_000) { okLongFired = true; controller.cycleCrt() }
                    return true
                }
                event.repeatCount > 0 -> return true
                code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_CHANNEL_UP -> controller.channelUp()
                code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_CHANNEL_DOWN -> controller.channelDown()
                code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_LAST_CHANNEL -> controller.lastChannel()
                code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_INFO -> controller.showBanner()
                code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_GUIDE -> controller.go(Screen.Guide)
                code == KeyEvent.KEYCODE_BACK -> controller.backFromPlayer()
                else -> return super.dispatchKeyEvent(event)
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_UP) {
            if (isOk) { if (!okLongFired) controller.showBanner(); return true }
            if (code in HANDLED) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private companion object {
        val HANDLED = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_BACK,
        )
    }
}

@Composable
fun RerunRoot(controller: TvController, onPoweredOff: () -> Unit) {
    val screen by controller.screen.collectAsState()
    val crt by controller.crtLevel.collectAsState()
    val exiting by controller.exiting.collectAsState()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = screen) {
            Screen.Onboarding -> OnboardingScreen(controller)
            Screen.Player -> PlayerScreen(controller)
            Screen.Guide -> GuideScreen(controller)
            Screen.Channels -> ChannelManager(controller)
            is Screen.Editor -> ChannelEditor(controller, s.channelId)
            Screen.Settings -> SettingsScreen(controller)
        }
        CrtOverlay(CrtLevel.from(crt))
        PowerTransition(on = !exiting, onOffDone = onPoweredOff)
    }
}
