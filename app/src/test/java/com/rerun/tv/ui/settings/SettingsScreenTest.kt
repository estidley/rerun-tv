package com.rerun.tv.ui.settings

import android.app.Application
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import com.rerun.tv.RerunApp
import com.rerun.tv.data.LibrarySync
import com.rerun.tv.data.Settings
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.data.SyncProgress
import com.rerun.tv.player.TunerEngine
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var settings: Settings
    private lateinit var sync: LibrarySync
    private lateinit var controller: TvController
    private val subtitleSize = MutableStateFlow("MEDIUM")
    private val subtitleColor = MutableStateFlow("WHITE")
    private val showSettings = mutableStateOf(true)

    @Before
    fun setUp() = runBlocking {
        val app = mock(RerunApp::class.java)
        settings = mock(Settings::class.java)
        sync = mock(LibrarySync::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.sync).thenReturn(sync)
        val schedule = mock(ScheduleRepo::class.java)
        `when`(schedule.sharedRefreshRequests).thenReturn(kotlinx.coroutines.flow.MutableSharedFlow())
        `when`(app.schedule).thenReturn(schedule)
        `when`(schedule.activeChannels()).thenReturn(flowOf(emptyList()))
        `when`(schedule.active()).thenReturn(emptyList())
        `when`(settings.lastSyncMs()).thenReturn(0L)
        `when`(settings.serverBase()).thenReturn("http://127.0.0.1:32400")
        `when`(settings.flag(anyString(), anyBoolean())).thenReturn(flowOf(false))
        `when`(settings.crtLevel()).thenReturn(flowOf("OFF"))
        `when`(settings.colorTheme()).thenReturn(flowOf("STANDARD_BLUE"))
        `when`(settings.guideDensity()).thenReturn(flowOf("DEFAULT"))
        `when`(settings.subtitleLanguage()).thenReturn(flowOf("auto"))
        `when`(settings.subtitleSize()).thenReturn(subtitleSize)
        `when`(settings.subtitleColor()).thenReturn(subtitleColor)
        doAnswer { subtitleSize.value = it.getArgument(0); Unit }.`when`(settings).setSubtitleSize(anyString())
        doAnswer { subtitleColor.value = it.getArgument(0); Unit }.`when`(settings).setSubtitleColor(anyString())
        `when`(settings.audioLanguage()).thenReturn(flowOf("auto"))
        `when`(settings.hdrMode()).thenReturn(flowOf("AUTO"))
        `when`(settings.minimumContent()).thenReturn(flowOf(com.rerun.core.catalog.MinimumContentLevel.MEDIUM))
        `when`(settings.minimumContentValue()).thenReturn(com.rerun.core.catalog.MinimumContentLevel.MEDIUM)
        controller = spy(TvController(app, mock(TunerEngine::class.java)))
        compose.setContent {
            if (showSettings.value) SettingsScreen(controller, app) else BasicText("Guide")
        }
    }

    @After
    fun tearDown() = controller.dispose()

    @Test
    fun musicFollowsIncludedLibrariesWithoutASeparateToggle() {
        compose.onNodeWithText("Music").assertDoesNotExist()
        compose.onNodeWithText("Included Plex music libraries sync automatically", substring = true).assertExists()
        compose.onNodeWithText("Manage libraries").assertExists()
    }

    @Test
    fun minimumSelectionPersistsAndRebuildsTheSavedLineup() = runBlocking<Unit> {
        compose.onNodeWithText("High (24 hours)").performScrollTo().performClick()
        verify(settings).setMinimumContent(com.rerun.core.catalog.MinimumContentLevel.HIGH)
        verify(sync).refreshChannels()
        verify(controller).reconcileChannels()
    }

    @Test
    fun subtitleAppearanceCyclesAndSavesWithoutRetuning() = runBlocking<Unit> {
        clearInvocations(controller.engine)
        compose.onNodeWithText("Size: Medium").performScrollTo().performClick()
        compose.onNodeWithText("Size: Large").assertExists()
        compose.onNodeWithText("Color: White").performScrollTo().performClick()
        compose.onNodeWithText("Color: Yellow").assertExists()
        verify(settings).setSubtitleSize("LARGE")
        verify(settings).setSubtitleColor("YELLOW")
        verify(controller.engine, never()).tune(anyString())
    }

    @Test
    fun aboutShowsTheInstalledVersion() {
        compose.onNodeWithText("Rerun ${com.rerun.tv.BuildConfig.VERSION_NAME} · ", substring = true).assertExists()
        compose.onNodeWithText("Rerun 0.", substring = true).assertDoesNotExist()
    }

    @Test
    fun startOverHelpExplainsMovieConfirmationAndEpisodeQueue() {
        compose.onNodeWithText("Hold Left for 2 seconds during a movie or episode to start over.", substring = true)
            .assertExists()
        compose.onNodeWithText("Movies ask for confirmation; episodes can play once or queue the rest of the show.", substring = true)
            .assertExists()
    }

    @Test
    fun regionalFeedOptionIsAbsent() {
        compose.onNodeWithText("East and West").assertDoesNotExist()
    }

    @Test
    fun tabVisitsEveryButtonInVisualOrderAndShiftTabReversesIt() {
        fun assertFocused(label: String) {
            compose.onAllNodesWithText(label).filter(isFocused()).assertCountEquals(1)
        }
        val labels = listOf(
            "All effects on", "All effects off", "Static", "Static sound", "Picture roll", "Power on/off",
            "■ CRT Off", "CRT Subtle", "CRT Full", "■ Default", "Lucy", "Partridge", "Headroom", "Gak",
            "Compact", "■ Default", "Large", "Subtitles", "Current program subtitle tracks", "Size: Medium", "Color: White",
            "Shared scheduling pilot", "Low (4 hours)", "■ Medium (12 hours)", "High (24 hours)",
            "Manage libraries", "Re-sync now", "Channels", "Sign out", "Ad library",
            "Audio language: Automatic", "Current program audio tracks", "Normalize loudness", "■ Auto HDR", "Convert HDR to SDR", "Prefer native HDR",
        )
        for ((index, label) in labels.withIndex()) {
            assertFocused(label)
            if (index < labels.lastIndex) compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        }
        for (label in labels.dropLast(1).asReversed()) {
            compose.onRoot().performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Tab)
                keyUp(Key.ShiftLeft)
            }
            assertFocused(label)
        }
    }

    @Test
    fun dpadFollowsRowsAndKeepsTheColumnWherePossible() {
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("All effects off").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Static sound").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("CRT Subtle").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Static sound").assertIsFocused()
    }

    @Test
    fun dpadMovesAcrossBothThemeRows() {
        repeat(9) { compose.onRoot().performKeyInput { pressKey(Key.Tab) } }
        compose.onAllNodesWithText("■ Default").filter(isFocused()).assertCountEquals(1)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Lucy").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Gak").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Lucy").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Partridge").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Headroom").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Gak").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Headroom").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onAllNodesWithText("■ Default").filter(isFocused()).assertCountEquals(1)
    }

    @Test
    fun dpadUsesVisibleThemeColumnsAtSectionBoundaries() {
        repeat(12) { compose.onRoot().performKeyInput { pressKey(Key.Tab) } }
        compose.onNodeWithText("Headroom").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Compact").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Headroom").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Gak").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onAllNodesWithText("■ Default").filter(isFocused()).assertCountEquals(1)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Gak").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Headroom").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Partridge").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Large").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Partridge").assertIsFocused()
    }

    @Test
    fun normalizationTogglePersistsAndRefreshesPlayback() = runBlocking<Unit> {
        compose.onNodeWithText("Normalize loudness").performScrollTo().performClick()
        verify(settings).setFlag(Settings.NORMALIZE_LOUDNESS, true)
        verify(controller).setNormalizeLoudness(true)
    }

    @Test
    fun subtitlesAndTrackPickerAreAdjacentInTheSameRow() {
        val toggle = compose.onNodeWithText("Subtitles").fetchSemanticsNode().boundsInRoot
        val language = compose.onNodeWithText("Current program subtitle tracks").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(toggle.top, language.top, 1f)
        org.junit.Assert.assertTrue(language.left > toggle.right)
    }

    @Test
    fun leavingSettingsDoesNotCancelLibrarySync() = runBlocking<Unit> {
        val finish = CompletableDeferred<Unit>()
        `when`(sync.sync()).thenReturn(flow {
            emit(SyncProgress("Music", 1, 2))
            finish.await()
            emit(SyncProgress("Done", 2, 2, true))
        })
        controller.resyncLibrary()
        compose.runOnIdle {
            assertTrue(controller.lineupUpdate.value.busy)
            controller.go(Screen.Guide)
            showSettings.value = false
        }
        compose.waitForIdle()
        assertTrue(controller.lineupUpdate.value.busy)
        compose.runOnIdle { finish.complete(Unit) }
        compose.waitUntil(10_000) { !controller.lineupUpdate.value.busy }
        verify(controller).reconcileChannels()
        compose.runOnIdle { showSettings.value = true }
        compose.onNodeWithText("Re-sync now").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun failedLibrarySyncShowsTheError() = runBlocking<Unit> {
        `when`(sync.sync()).thenReturn(flow { throw IllegalStateException("Plex unavailable") })
        controller.resyncLibrary()
        compose.onNodeWithText("Sync failed: Plex unavailable").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun signingOutCancelsLibrarySyncBeforeClearingTheConnection() = runBlocking<Unit> {
        var cancelled = false
        `when`(sync.sync()).thenReturn(flow {
            try {
                emit(SyncProgress("Music", 1, 2))
                kotlinx.coroutines.awaitCancellation()
            } finally {
                cancelled = true
            }
        })
        controller.resyncLibrary()
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.waitUntil(10_000) { !controller.lineupUpdate.value.busy }
        assertTrue(cancelled)
        verify(sync).signOut()
        assertEquals(Screen.Onboarding, controller.screen.value)
    }

    @Test
    fun signOutUsesControllerResetAndDisplaysFailures() = runBlocking<Unit> {
        doAnswer { throw IllegalStateException("Storage unavailable") }.`when`(controller).signOut()
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNodeWithText("Sign out failed: Storage unavailable").performScrollTo().assertIsDisplayed()
        verify(controller).signOut()
    }

    @Test
    fun settingsPanelHasSymmetricMarginsAndLeftAlignedContent() {
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("settings-panel").fetchSemanticsNode().boundsInRoot
        val heading = compose.onNodeWithText("EFFECTS").fetchSemanticsNode().boundsInRoot
        val nextHeading = compose.onNodeWithText("CRT LOOK").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.left > screen.left)
        assertTrue(panel.right < screen.right)
        assertEquals(panel.left - screen.left, screen.right - panel.right, 1f)
        assertTrue(heading.left > panel.left)
        assertEquals(heading.left, nextHeading.left, 1f)
    }
}
