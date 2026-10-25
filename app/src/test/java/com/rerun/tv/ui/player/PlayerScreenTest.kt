package com.rerun.tv.ui.player

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import com.rerun.tv.ui.LineupUpdate
import com.rerun.tv.ui.StartupState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class PlayerScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun startupMessagesDistinguishLoadingUpdatingEmptyAndFailedStates() {
        val startup = mutableStateOf(StartupState(loading = true))
        val update = mutableStateOf(LineupUpdate())
        compose.setContent { StartupMessage(startup.value, update.value, channelsEmpty = true) }
        compose.onNodeWithText("Loading channels and updating guide - please wait.").assertExists()
        compose.onNodeWithContentDescription("RERUN").assertIsDisplayed()
        compose.onNodeWithTag("startup-spinner").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
        )
        compose.onNodeWithText("No channels yet.", substring = true).assertDoesNotExist()
        compose.runOnIdle { update.value = LineupUpdate(busy = true, message = "Reading libraries") }
        compose.onNodeWithText("Updating guide - please wait.", substring = true).assertExists()
        compose.onNodeWithTag("startup-spinner").assertIsDisplayed()
        compose.runOnIdle {
            update.value = LineupUpdate()
            startup.value = StartupState()
        }
        compose.onNodeWithText("No channels yet.", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("startup-spinner").assertDoesNotExist()
        compose.runOnIdle { startup.value = StartupState(error = "Couldn't load channels") }
        compose.onNodeWithText("Couldn't load channels").assertExists()
        compose.onNodeWithContentDescription("RERUN").assertIsDisplayed()
        compose.onNodeWithTag("startup-spinner").assertDoesNotExist()
        compose.onNodeWithText("No channels yet.", substring = true).assertDoesNotExist()
        compose.runOnIdle { startup.value = StartupState(empty = true) }
        compose.onNodeWithText("No channels yet.", substring = true).assertExists()
        compose.onNodeWithTag("startup-spinner").assertDoesNotExist()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun loadingSpinnerRotatesWhileTheLogoRemainsVisible() {
        compose.mainClock.autoAdvance = false
        compose.setContent { StartupMessage(StartupState(loading = true), LineupUpdate(), channelsEmpty = true) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("RERUN").assertIsDisplayed()
        val before = compose.onNodeWithTag("startup-spinner").captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(250)
        val after = compose.onNodeWithTag("startup-spinner").captureToImage().toPixelMap()
        assertTrue("The spinner must visibly rotate", (0 until before.height).any { y ->
            (0 until before.width).any { x -> before[x, y] != after[x, y] }
        })
        compose.onNodeWithContentDescription("RERUN").assertIsDisplayed()
    }
}
