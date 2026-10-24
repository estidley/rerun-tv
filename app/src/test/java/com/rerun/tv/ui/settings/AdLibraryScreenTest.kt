package com.rerun.tv.ui.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.rerun.core.plex.Section
import com.rerun.tv.RerunApp
import com.rerun.tv.data.LibrarySync
import com.rerun.tv.data.Settings
import com.rerun.tv.ui.TvController
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class AdLibraryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectingALibraryAndOffUpdatesTheSavedSource() = runBlocking<Unit> {
        val app = mock(RerunApp::class.java)
        val settings = mock(Settings::class.java)
        val sync = mock(LibrarySync::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.sync).thenReturn(sync)
        `when`(sync.adLibraries()).thenReturn(listOf(Section("ads", "movie", "Retro Ads")))
        val controller = mock(TvController::class.java)
        compose.setContent { AdLibraryScreen(controller, app) }
        compose.onNodeWithText("■ Off").assertExists()
        compose.onNodeWithText("Retro Ads").performClick()
        compose.onNodeWithText("■ Retro Ads").assertExists()
        verify(settings).setAdLibrary("ads")
        compose.onNodeWithText("Off").performClick()
        compose.onNodeWithText("■ Off").assertExists()
        verify(settings).setAdLibrary(null)
    }

    @Test
    fun failedLoadingCanBeRetried() = runBlocking<Unit> {
        val app = mock(RerunApp::class.java)
        val settings = mock(Settings::class.java)
        val sync = mock(LibrarySync::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.sync).thenReturn(sync)
        `when`(sync.adLibraries()).thenAnswer { throw IllegalStateException("Offline") }.thenReturn(emptyList())
        val controller = mock(TvController::class.java)
        compose.setContent { AdLibraryScreen(controller, app) }
        compose.onNodeWithText("Couldn't load ad libraries: Offline").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("■ Off").assertExists()
        compose.onNodeWithText("No video libraries found. Create an Other Videos library in Plex, then reopen this page.").assertExists()
    }

    @Test
    fun successfulSaveClearsAnEarlierSaveError() = runBlocking<Unit> {
        val app = mock(RerunApp::class.java)
        val settings = mock(Settings::class.java)
        val sync = mock(LibrarySync::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.sync).thenReturn(sync)
        `when`(sync.adLibraries()).thenReturn(listOf(Section("ads", "movie", "Retro Ads")))
        doAnswer { throw IllegalStateException("Save failed") }.`when`(settings).setAdLibrary("ads")
        val controller = mock(TvController::class.java)
        compose.setContent { AdLibraryScreen(controller, app) }
        compose.onNodeWithText("Retro Ads").performScrollTo().performClick()
        compose.onNodeWithText("Couldn't save ad library: Save failed").assertExists()
        compose.onNodeWithText("■ Off").performScrollTo().performClick()
        compose.onNodeWithText("Couldn't save ad library: Save failed").assertDoesNotExist()
        verify(settings).setAdLibrary(null)
    }
}
