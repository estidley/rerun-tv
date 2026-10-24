package com.rerun.tv.ui.settings

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.pressKey
import com.rerun.tv.RerunApp
import com.rerun.tv.data.LibrarySync
import com.rerun.tv.data.Settings
import com.rerun.tv.ui.TvController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
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
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var settings: Settings
    private lateinit var sync: LibrarySync
    private lateinit var controller: TvController
    private lateinit var refresh: Continuation<Unit>
    private val eastWest = MutableStateFlow(false)

    @Before
    fun setUp() = runBlocking {
        val app = mock(RerunApp::class.java)
        settings = mock(Settings::class.java)
        sync = mock(LibrarySync::class.java)
        controller = mock(TvController::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.sync).thenReturn(sync)
        `when`(settings.lastSyncMs()).thenReturn(0L)
        `when`(settings.serverBase()).thenReturn("http://127.0.0.1:32400")
        `when`(settings.flag(anyString(), anyBoolean())).thenReturn(flowOf(false))
        `when`(settings.flag(Settings.EAST_WEST, false)).thenReturn(eastWest)
        `when`(controller.crtLevel).thenReturn(MutableStateFlow("OFF"))
        `when`(controller.effects).thenReturn(MutableStateFlow(com.rerun.tv.ui.Effects(false, false, false, false)))
        `when`(controller.colorTheme).thenReturn(MutableStateFlow("STANDARD_BLUE"))
        `when`(controller.guideDensity).thenReturn(MutableStateFlow("COMFORTABLE"))
        `when`(controller.subtitleLanguage).thenReturn(MutableStateFlow("auto"))
        doAnswer {
            @Suppress("UNCHECKED_CAST")
            val continuation = it.rawArguments.last() as Continuation<Unit>
            refresh = continuation
            COROUTINE_SUSPENDED
        }.`when`(sync).refreshChannels()
        compose.setContent { SettingsScreen(controller, app) }
    }

    @Test
    fun eastWestChangesBeforeRefreshCompletesAndIgnoresRepeatedClicks() = runBlocking<Unit> {
        compose.onNodeWithText("East and West")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performScrollTo().performClick()
        compose.onNodeWithText("■ East and West").performScrollTo().assertIsDisplayed().performClick()
        verify(settings, times(1)).setFlag(Settings.EAST_WEST, true)
        compose.runOnIdle {
            eastWest.value = true
            refresh.resume(Unit)
        }
        compose.onNodeWithText("■ East and West").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun failedRefreshRestoresTheToggleAndShowsTheError() = runBlocking<Unit> {
        compose.onNodeWithText("East and West")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performScrollTo().performClick()
        compose.onNodeWithText("■ East and West").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { refresh.resumeWithException(IllegalStateException("test failure")) }
        compose.onNodeWithText("East and West").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lineup failed: test failure").performScrollTo().assertIsDisplayed()
        verify(settings).setFlag(Settings.EAST_WEST, false)
    }

    @Test
    fun tabVisitsEveryButtonInVisualOrderAndShiftTabReversesIt() {
        val labels = listOf(
            "All effects on", "All effects off", "Static", "Static sound", "Picture roll", "Power on/off",
            "■ CRT Off", "CRT Subtle", "CRT Full", "■ Standard blue", "70s", "50s",
            "Compact", "■ Comfortable", "Large", "Subtitles", "Subtitle language: Automatic",
            "East and West", "Music", "Manage libraries", "Re-sync now", "Channels", "Sign out", "Ad library",
        )
        for ((index, label) in labels.withIndex()) {
            compose.onNodeWithText(label).assertIsFocused()
            if (index < labels.lastIndex) compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        }
        for (label in labels.dropLast(1).asReversed()) {
            compose.onRoot().performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Tab)
                keyUp(Key.ShiftLeft)
            }
            compose.onNodeWithText(label).assertIsFocused()
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
    fun subtitlesAndLanguageAreAdjacentInTheSameRow() {
        val toggle = compose.onNodeWithText("Subtitles").fetchSemanticsNode().boundsInRoot
        val language = compose.onNodeWithText("Subtitle language: Automatic").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(toggle.top, language.top, 1f)
        org.junit.Assert.assertTrue(language.left > toggle.right)
    }

    @Test
    fun enablingMusicWithoutAnIncludedAudioLibraryExplainsHowToIncludeOne() = runBlocking<Unit> {
        `when`(settings.selectedLibraries()).thenReturn(setOf("movies"))
        `when`(sync.libraries()).thenReturn(listOf(com.rerun.core.plex.Section("audio", "artist", "Music")))
        compose.onNodeWithText("Music").performScrollTo().performClick()
        compose.onNodeWithText("Sync failed: Include a Plex music library in Manage libraries first.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Music").assertExists()
        verify(settings, never()).setFlag(Settings.MUSIC, true)
        verify(sync, never()).sync()
    }

    @Test
    fun enablingMusicSyncsIncludedAudioLibraries() = runBlocking<Unit> {
        `when`(settings.selectedLibraries()).thenReturn(setOf("audio"))
        `when`(sync.libraries()).thenReturn(listOf(com.rerun.core.plex.Section("audio", "artist", "Music")))
        `when`(sync.sync()).thenReturn(flowOf(com.rerun.tv.data.SyncProgress("Done", 1, 1, true)))
        compose.onNodeWithText("Music").performScrollTo().performClick()
        compose.onNodeWithText("■ Music").assertExists()
        verify(settings).setFlag(Settings.MUSIC, true)
        verify(sync).sync()
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
