package com.rerun.tv.ui.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.rerun.tv.player.Phase
import com.rerun.tv.player.TunerState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaybackTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sameStationUsesBlackFadeAndOnlyStationChangesUseStatic() {
        val state = mutableStateOf(TunerState(phase = Phase.PLAYING))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                PlaybackTransition(state.value, static = true, roll = false)
            }
        }
        compose.runOnIdle {
            state.value = state.value.copy(sameChannelTransition = true, fadingOut = true)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("channel-static").assertDoesNotExist()
        val dark = compose.onNodeWithTag("clip-fade").captureToImage().toPixelMap()
        assertEquals(Color.Black, dark[dark.width / 2, dark.height / 2])
        compose.runOnIdle { state.value = state.value.copy(fadingOut = false, phase = Phase.TUNING) }
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("channel-static").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(phase = Phase.PLAYING) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(250)
        val light = compose.onNodeWithTag("clip-fade").captureToImage().toPixelMap()
        assertEquals(Color.White, light[light.width / 2, light.height / 2])
        compose.runOnIdle { state.value = TunerState(phase = Phase.TUNING) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("clip-fade").assertDoesNotExist()
        compose.onNodeWithTag("channel-static").assertExists()
    }
}
