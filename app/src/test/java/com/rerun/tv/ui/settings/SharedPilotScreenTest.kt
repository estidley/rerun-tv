package com.rerun.tv.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.rerun.tv.data.SharedPilotStatus
import com.rerun.core.schedule.SharedScheduleCode
import com.rerun.tv.ui.LineupUpdate
import com.rerun.tv.ui.TvController
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class SharedPilotScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun codeEntryAndGenerateDisableAreRemoteReachableAndExplainPrototypeLimits() {
        val controller = mock(TvController::class.java)
        `when`(controller.sharedPilot).thenReturn(MutableStateFlow(SharedPilotStatus()))
        `when`(controller.lineupUpdate).thenReturn(MutableStateFlow(LineupUpdate()))
        compose.setContent { SharedPilotScreen(controller) }
        compose.onNodeWithText("Join code").assertIsFocused()
        compose.onNodeWithTag("shared-code-input").assertDoesNotExist()
        compose.onNodeWithText("Join code").performClick()
        compose.onNodeWithTag("shared-code-input").assertIsFocused()
        compose.onNodeWithText("Confirm join").assertIsNotEnabled()
        val grouped = SharedScheduleCode(123, 0).encode().chunked(4).joinToString("-")
        compose.onNodeWithTag("shared-code-input").performTextInput(grouped)
        verify(controller, never()).setSharedPilot(anyString(), anyBoolean())
        compose.onNodeWithText("Confirm join").performScrollTo().performClick()
        verify(controller).setSharedPilot(grouped, false)
        compose.onNodeWithTag("shared-code-input").assertDoesNotExist()
        compose.onNodeWithTag("shared-join-progress").assertExists()
        compose.onNodeWithText(grouped).assertExists()
        compose.onNodeWithText("Generate & join").performScrollTo().performClick()
        verify(controller).setSharedPilot(null, true)
        compose.onNodeWithText("Disable").performScrollTo().performClick()
        verify(controller).setSharedPilot(null, false)
        compose.onNodeWithText("time-block channels are excluded", substring = true).assertExists()
        compose.onNodeWithText("movies may lose up to two minutes", substring = true).assertExists()
        compose.onNodeWithText("Stale libraries refresh at most daily", substring = true).assertExists()
    }

    @Test
    fun failuresAndPendingUtcCutoversAreVisible() {
        val controller = mock(TvController::class.java)
        `when`(controller.sharedPilot).thenReturn(MutableStateFlow(SharedPilotStatus("CODE", 10_800_000L)))
        `when`(controller.lineupUpdate).thenReturn(MutableStateFlow(LineupUpdate(error = "Refresh unavailable")))
        compose.setContent { SharedPilotScreen(controller) }
        compose.onNodeWithText("Refresh unavailable").assertExists()
        compose.onNodeWithText("Requested UTC boundary:", substring = true).assertExists()
    }

    @Test
    fun busyJoinExplainsWorkBeforeSavedCodeIsAvailable() {
        val controller = mock(TvController::class.java)
        `when`(controller.sharedPilot).thenReturn(MutableStateFlow(SharedPilotStatus()))
        `when`(controller.lineupUpdate).thenReturn(MutableStateFlow(
            LineupUpdate(busy = true, message = "Reading Plex libraries")))
        compose.setContent { SharedPilotScreen(controller) }
        compose.onNodeWithTag("shared-join-progress").assertExists()
        compose.onNodeWithText("Reading Plex libraries").assertExists()
        compose.onNodeWithText("Join code").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-mdpi")
    fun stageScrollKeepsJoinAndManyChannelFingerprintsAccessibleOn720p() {
        val controller = mock(TvController::class.java)
        val rows = (100..199).map { "$it stock channel fingerprint $it" }
        `when`(controller.sharedPilot).thenReturn(MutableStateFlow(SharedPilotStatus(channels = rows)))
        `when`(controller.lineupUpdate).thenReturn(MutableStateFlow(LineupUpdate()))
        compose.setContent { SharedPilotScreen(controller) }
        compose.onNodeWithText("Join code").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Join code").performClick()
        compose.onNodeWithTag("shared-code-input").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText(rows.last()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Generate & join").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun invalidCodeStaysInFormAndCancelReturnsFocusWithoutJoining() {
        val controller = mock(TvController::class.java)
        `when`(controller.sharedPilot).thenReturn(MutableStateFlow(SharedPilotStatus()))
        `when`(controller.lineupUpdate).thenReturn(MutableStateFlow(LineupUpdate()))
        compose.setContent { SharedPilotScreen(controller) }
        compose.onNodeWithText("Join code").performClick()
        compose.onNodeWithTag("shared-code-input").performTextInput("BAD")
        compose.onNodeWithText("Confirm join").performClick()
        compose.onNodeWithText("Shared schedule code must contain 24 symbols").assertExists()
        verify(controller, never()).setSharedPilot(anyString(), anyBoolean())
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("shared-code-input").assertDoesNotExist()
        compose.onNodeWithText("Join code").assertIsFocused()
    }
}
