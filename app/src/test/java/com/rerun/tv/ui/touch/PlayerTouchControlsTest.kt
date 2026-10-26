package com.rerun.tv.ui.touch

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import com.rerun.tv.ui.player.TouchControlBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class PlayerTouchControlsTest {
    @get:Rule val compose = createComposeRule()

    private fun record(enabled: Boolean = true): MutableList<PlayerTouchAction> {
        val actions = mutableListOf<PlayerTouchAction>()
        val onAction: (PlayerTouchAction) -> Unit = { actions += it }
        compose.setContent { Box(Modifier.fillMaxSize().testTag("player").playerTouchControls(enabled, onAction)) }
        return actions
    }

    @Test
    fun swipesTapsAndLongPressesMapToRemoteActions() {
        val actions = record()
        val player = compose.onNodeWithTag("player")
        player.performTouchInput { swipeUp() }
        player.performTouchInput { swipeDown() }
        player.performTouchInput { swipeLeft() }
        player.performTouchInput { swipeRight() }
        player.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1_000)
        player.performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(
            listOf(
                PlayerTouchAction.CHANNEL_UP, PlayerTouchAction.CHANNEL_DOWN,
                PlayerTouchAction.LAST_CHANNEL, PlayerTouchAction.SHOW_INFO,
                PlayerTouchAction.TOGGLE_BANNER, PlayerTouchAction.OPEN_GUIDE,
            ),
            actions,
        )
    }

    @Test
    fun disabledOnDevicesWithoutTouch() {
        val actions = record(enabled = false)
        compose.onNodeWithTag("player").performTouchInput { swipeUp() }
        compose.onNodeWithTag("player").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(emptyList<PlayerTouchAction>(), actions)
    }

    @Test
    fun controlBarButtonsRunTheirRemoteActions() {
        val pressed = mutableListOf<String>()
        compose.setContent {
            TouchControlBar(
                onGuide = { pressed += "guide" }, onChannelUp = { pressed += "up" }, onChannelDown = { pressed += "down" },
                onLastChannel = { pressed += "last" }, onStartOver = { pressed += "over" },
                onAudio = { pressed += "audio" }, onCrt = { pressed += "crt" },
            )
        }
        listOf("Guide", "CH ▲", "CH ▼", "Last channel", "Start over", "Audio", "CRT").forEach {
            compose.onNodeWithText(it, substring = true).performClick()
        }
        assertEquals(listOf("guide", "up", "down", "last", "over", "audio", "crt"), pressed)
    }
}
