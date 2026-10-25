package com.rerun.tv.ui.player

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rerun.core.plex.AudioStream
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
class AudioTrackScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var controller: TvController
    private lateinit var engine: TunerEngine

    @Before
    fun setUp() {
        controller = mock(TvController::class.java)
        engine = mock(TunerEngine::class.java)
        `when`(controller.engine).thenReturn(engine)
        `when`(engine.state).thenReturn(MutableStateFlow(TunerState(
            tuneId = 9,
            selectedAudioId = 1,
            audioTracks = listOf(
                AudioStream(1, "eng", "English", "aac", 2, true),
                AudioStream(2, "spa", "Spanish", "ac3", 6, false),
            ),
        )))
        compose.setContent { AudioTrackScreen(controller) }
    }

    @Test
    fun choosesAnExplicitTrackWithoutSavingTheGlobalPreference() {
        compose.onNodeWithText("Spanish").performClick()
        verify(engine).selectAudioTrack(2, 9)
        verify(controller).go(Screen.Player)
    }

    @Test
    fun defaultButtonClearsThePerProgramOverride() {
        compose.onNodeWithText("Use preferred language").performClick()
        verify(engine).selectAudioTrack(null, 9)
        verify(controller).go(Screen.Player)
    }

    @Test
    fun failedSelectionKeepsThePickerOpenAndShowsTheError() {
        doThrow(IllegalStateException("Program changed")).`when`(engine).selectAudioTrack(2, 9)
        compose.onNodeWithText("Spanish").performClick()
        compose.onNodeWithText("Couldn't change audio: Program changed").assertExists()
        verify(controller, never()).go(Screen.Player)
    }
}
