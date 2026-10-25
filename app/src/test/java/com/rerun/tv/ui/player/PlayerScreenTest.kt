package com.rerun.tv.ui.player

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rerun.tv.ui.LineupUpdate
import com.rerun.tv.ui.StartupState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        compose.onNodeWithText("No channels yet.", substring = true).assertDoesNotExist()
        compose.runOnIdle { update.value = LineupUpdate(busy = true, message = "Reading libraries") }
        compose.onNodeWithText("Updating guide - please wait.", substring = true).assertExists()
        compose.runOnIdle {
            update.value = LineupUpdate()
            startup.value = StartupState()
        }
        compose.onNodeWithText("No channels yet.", substring = true).assertDoesNotExist()
        compose.runOnIdle { startup.value = StartupState(error = "Couldn't load channels") }
        compose.onNodeWithText("Couldn't load channels").assertExists()
        compose.onNodeWithText("No channels yet.", substring = true).assertDoesNotExist()
        compose.runOnIdle { startup.value = StartupState(empty = true) }
        compose.onNodeWithText("No channels yet.", substring = true).assertExists()
    }
}
