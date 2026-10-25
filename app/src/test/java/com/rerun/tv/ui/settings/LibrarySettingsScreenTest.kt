package com.rerun.tv.ui.settings

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rerun.core.plex.Section
import com.rerun.tv.RerunApp
import com.rerun.tv.data.LibrarySync
import com.rerun.tv.data.Settings
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.theme.ColorTheme
import com.rerun.tv.ui.theme.ProvideColorTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class LibrarySettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun indicatorsAndLabelsFollowSelectionInEveryTheme() = runBlocking {
        val app = mock(RerunApp::class.java)
        val settings = mock(Settings::class.java)
        val sync = mock(LibrarySync::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.sync).thenReturn(sync)
        `when`(settings.selectedLibraries()).thenReturn(setOf("movies"))
        `when`(sync.libraries()).thenReturn(listOf(
            Section("movies", "movie", "Movies"),
            Section("shows", "show", "Shows"),
        ))
        val theme = mutableStateOf(ColorTheme.STANDARD_BLUE)
        val controller = mock(TvController::class.java)
        `when`(controller.lineupUpdate).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(com.rerun.tv.ui.LineupUpdate()))
        compose.setContent {
            ProvideColorTheme(theme.value) { LibrarySettingsScreen(controller, app) }
        }
        for (choice in ColorTheme.entries) {
            compose.runOnIdle { theme.value = choice }
            compose.onNodeWithText("+ Movies").assertIsDisplayed()
            compose.onNodeWithText("Included - Movies", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("x Shows").assertIsDisplayed()
            compose.onNodeWithText("Not included - TV shows", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("+ Movies").performClick()
            compose.onNodeWithText("x Movies").assertIsDisplayed()
            compose.onNodeWithText("Save and sync").assertIsNotEnabled()
            compose.onNodeWithText("x Shows").performClick()
            compose.onNodeWithText("+ Shows").assertIsDisplayed().performClick()
            compose.onNodeWithText("x Movies").performClick()
        }
        compose.onNodeWithText("Save and sync").performClick()
        verify(controller).saveLibraries(setOf("movies"))
    }
}
