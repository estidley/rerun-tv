package com.rerun.tv.ui.onboarding

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.rerun.core.plex.PlexServer
import com.rerun.core.plex.Section
import com.rerun.tv.RerunApp
import com.rerun.tv.data.LibrarySync
import com.rerun.tv.data.PlexApi
import com.rerun.tv.data.Settings
import com.rerun.tv.ui.TvController
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import com.rerun.tv.data.SyncProgress
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: RerunApp
    private lateinit var settings: Settings
    private lateinit var sync: LibrarySync

    @Before
    fun setUp() = runBlocking<Unit> {
        app = mock(RerunApp::class.java)
        settings = mock(Settings::class.java)
        sync = mock(LibrarySync::class.java)
        val api = mock(PlexApi::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.sync).thenReturn(sync)
        `when`(app.plex).thenReturn(api)
        `when`(settings.accountToken).thenReturn("test-placeholder")
        val server = PlexServer("Server", "server", null, emptyList())
        `when`(api.servers("test-placeholder")).thenReturn(listOf(server))
        `when`(api.pickConnection(server)).thenReturn("http://127.0.0.1:32400")
        `when`(sync.adLibraries()).thenReturn(listOf(Section("ads", "movie", "Retro Ads")))
        `when`(sync.libraries()).thenReturn(listOf(Section("tv", "show", "TV Shows")))
        `when`(sync.syncAds("ads")).thenReturn(flowOf(SyncProgress("Ads synced", 1, 1, true)))
        `when`(sync.syncAds(null)).thenReturn(flowOf(SyncProgress("Ads disabled", 0, 0, true)))
    }

    @Test
    fun setupSelectsAdLibraryBeforeRegularLibraries() = runBlocking<Unit> {
        val controller = mock(TvController::class.java)
        compose.setContent { OnboardingScreen(controller, app) }
        compose.onNodeWithText("Choose an ad library, or disable ads").assertExists()
        compose.onNodeWithText("Retro Ads").performScrollTo().performClick()
        compose.onNodeWithText("■ Retro Ads").assertExists()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Which libraries should Rerun include?").assertExists()
        compose.onNodeWithText("+ TV Shows").assertExists()
        compose.onNodeWithText("Retro Ads").assertDoesNotExist()
        verify(sync).syncAds("ads")
        verify(sync).libraries()
    }

    @Test
    fun setupCanDisableAdsEvenWithNoAdLibraries() = runBlocking<Unit> {
        `when`(sync.adLibraries()).thenReturn(emptyList())
        val controller = mock(TvController::class.java)
        compose.setContent { OnboardingScreen(controller, app) }
        compose.onNodeWithText("■ Disable ads").assertExists()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Which libraries should Rerun include?").assertExists()
        verify(sync).syncAds(null)
    }
}
