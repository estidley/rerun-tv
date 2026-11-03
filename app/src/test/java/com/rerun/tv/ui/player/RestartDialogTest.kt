package com.rerun.tv.ui.player

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rerun.core.model.MediaKind
import com.rerun.core.plex.PlexParse
import com.rerun.tv.ui.RestartPrompt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class RestartDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun queueAndLeaveChoicesWorkWithTouchAndShowErrorsWithoutDismissing() {
        val item = PlexParse.movies(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"1","title":"Episode","duration":1800000}]}}""",
        ).single().copy(kind = MediaKind.EPISODE)
        val prompt = mutableStateOf<RestartPrompt>(RestartPrompt.Start(item, "source", 1, replacing = true))
        var action = ""
        compose.setContent {
            RestartChoices(prompt.value, false, "Save failed", { action = "cancel" },
                { action = "queue:$it" }, { action = "remove:$it" })
        }
        compose.onNodeWithText("Starting this program replaces your saved From the Top station.").assertExists()
        compose.onNodeWithText("Queue next episodes").performClick()
        assertEquals("queue:true", action)
        compose.onNodeWithText("Just this episode").performClick()
        assertEquals("queue:false", action)
        compose.runOnIdle { prompt.value = RestartPrompt.Leave("other", false) }
        compose.onNodeWithText("Pause until later").performClick()
        assertEquals("remove:false", action)
        compose.onNodeWithText("Remove station").performClick()
        assertEquals("remove:true", action)
        compose.onNodeWithText("Save failed").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("cancel", action)
    }
}
