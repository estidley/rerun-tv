package com.rerun.tv.ui.player

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rerun.core.plex.SubtitleStream
import com.rerun.tv.player.TunerEngine
import com.rerun.tv.player.TunerState
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class SubtitleTrackScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var controller: TvController
    private lateinit var engine: TunerEngine
    private val state = MutableStateFlow(TunerState())

    @Before
    fun setUp() {
        controller = mock(TvController::class.java)
        engine = mock(TunerEngine::class.java)
        `when`(controller.engine).thenReturn(engine)
        `when`(controller.subtitleLanguage).thenReturn(MutableStateFlow("auto"))
        state.value = TunerState(
            tuneId = 9,
            subtitlesEnabled = true,
            subtitleAutomatic = false,
            selectedSubtitleId = 8,
            subtitleTracks = listOf(
                SubtitleStream(7, "srt", null, "eng", true, title = "English"),
                SubtitleStream(8, "ass", "/library/streams/8", "spa", false, title = "Español (SDH)", hearingImpaired = true),
            ),
        )
        `when`(engine.state).thenReturn(state)
        compose.setContent { SubtitleTrackScreen(controller) }
    }

    @Test
    fun showsTrackLabelsAndSelectsAnExplicitTrack() {
        compose.onNodeWithText("ASS - External - SDH / hearing impaired").assertExists()
        compose.onNodeWithText("Español (SDH)", substring = true).performClick()
        verify(engine).selectSubtitleTrack(8, 9)
        verify(controller).go(Screen.Player)
    }

    @Test
    fun offAutomaticAndRefreshUseTheirOwnActions() {
        compose.onNodeWithText("Off").performClick()
        verify(engine).selectSubtitleTrack(null, 9)
        compose.onNodeWithText("Automatic").performClick()
        verify(engine).useAutomaticSubtitles(9)
        compose.onNodeWithText("Refresh from Plex").performClick()
        verify(engine).refreshSubtitles(9)
    }

    @Test
    fun preferredLanguageOpensTheGlobalSetting() {
        compose.onNodeWithText("Preferred language: Automatic").performClick()
        verify(controller).go(Screen.SubtitleLanguage)
    }

    @Test
    fun failedSelectionKeepsThePickerOpenAndShowsTheError() {
        doThrow(IllegalStateException("Program changed")).`when`(engine).selectSubtitleTrack(8, 9)
        compose.onNodeWithText("Español (SDH)", substring = true).performClick()
        compose.onNodeWithText("Couldn't change subtitles: Program changed").assertExists()
        verify(controller, never()).go(Screen.Player)
    }

    @Test
    fun emptyMetadataExplainsThatPlexHasNoSubtitles() {
        state.value = TunerState(tuneId = 9)
        compose.waitForIdle()
        compose.onNodeWithText("No subtitles found for this program.").assertExists()
    }
}
