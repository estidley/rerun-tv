package com.rerun.tv.ui.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.Rules
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.player.Phase
import com.rerun.tv.player.TunerState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class PlayerOverlaysTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun hiddenOverlaysDoNotPollAndRetuningRestartsTheOsdDeadline() {
        val channel = ChannelRow.of(ChannelDef("test", 401, "Test channel", ChannelKind.CUSTOM, Rules()), false, false)
        val state = mutableStateOf(TunerState(channel = channel, phase = Phase.PLAYING))
        compose.mainClock.autoAdvance = false
        compose.setContent { Box { PlayerOverlays(state.value, 0) } }
        compose.onNodeWithText("401").assertExists()
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        compose.onNodeWithText("401").assertDoesNotExist()
        val before = Recomposer.runningRecomposers.value.sumOf { it.changeCount }
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertEquals(before, Recomposer.runningRecomposers.value.sumOf { it.changeCount })
        compose.runOnIdle { state.value = state.value.copy(tuneId = 2) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("401").assertExists()
    }

    @Test
    fun bannerExtensionAndEarlyDismissalRescheduleVisibility() {
        val deadline = mutableStateOf(System.currentTimeMillis() + 20_000)
        compose.mainClock.autoAdvance = false
        compose.setContent { Box { PlayerOverlays(TunerState(phase = Phase.PLAYING), deadline.value) } }
        compose.onNodeWithText("Coming up").assertExists()
        compose.mainClock.advanceTimeBy(4_000)
        compose.runOnIdle { deadline.value = System.currentTimeMillis() + 30_000 }
        compose.mainClock.advanceTimeBy(21_000)
        compose.onNodeWithText("Coming up").assertExists()
        compose.runOnIdle { deadline.value = 0 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Coming up").assertDoesNotExist()
    }
}
