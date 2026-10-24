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
import kotlinx.coroutines.flow.flowOf
import com.rerun.tv.data.SyncProgress
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
        `when`(sync.adCount()).thenReturn(0)
        `when`(sync.syncAds("ads")).thenReturn(flowOf(SyncProgress("Ads synced", 2, 2, true)))
        `when`(sync.syncAds(null)).thenReturn(flowOf(SyncProgress("Ads disabled", 0, 0, true)))
        `when`(sync.adLibraries()).thenReturn(listOf(Section("ads", "movie", "Retro Ads")))
        val controller = mock(TvController::class.java)
        compose.setContent { AdLibraryScreen(controller, app) }
        compose.onNodeWithText("■ Off").assertExists()
        compose.onNodeWithText("Retro Ads").performClick()
        compose.onNodeWithText("■ Retro Ads").assertExists()
        verify(sync).syncAds("ads")
        compose.onNodeWithText("Force ad sync").performScrollTo().performClick()
        verify(sync, times(2)).syncAds("ads")
        compose.onNodeWithText("Off").performClick()
        compose.onNodeWithText("■ Off").assertExists()
        verify(sync).syncAds(null)
        compose.onNodeWithText("Force ad sync").assertIsNotEnabled()
    }

    @Test
    fun failedLoadingCanBeRetried() = runBlocking<Unit> {
        val app = mock(RerunApp::class.java)
        val settings = mock(Settings::class.java)
        val sync = mock(LibrarySync::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.sync).thenReturn(sync)
        `when`(sync.adCount()).thenReturn(0)
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
        `when`(sync.adCount()).thenReturn(0)
        `when`(sync.syncAds(null)).thenReturn(flowOf(SyncProgress("Ads disabled", 0, 0, true)))
        `when`(sync.adLibraries()).thenReturn(listOf(Section("ads", "movie", "Retro Ads")))
        `when`(sync.syncAds("ads")).thenAnswer { throw IllegalStateException("Save failed") }
        val controller = mock(TvController::class.java)
        compose.setContent { AdLibraryScreen(controller, app) }
        compose.onNodeWithText("Retro Ads").performScrollTo().performClick()
        compose.onNodeWithText("Ad sync failed: Save failed").assertExists()
        compose.onNodeWithText("■ Off").performScrollTo().performClick()
        compose.onNodeWithText("Ad sync failed: Save failed").assertDoesNotExist()
        verify(sync).syncAds(null)
    }
}
