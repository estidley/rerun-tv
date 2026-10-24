package com.rerun.tv.ui.settings

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class AudioLanguageScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun audioLanguageScreenSavesTheAudioPreferenceNotSubtitles() = runBlocking<Unit> {
        val controller = mock(TvController::class.java)
        `when`(controller.audioLanguage).thenReturn(MutableStateFlow("en"))
        compose.setContent { SubtitleLanguageScreen(controller, audio = true) }
        compose.onNodeWithText("AUDIO LANGUAGE").assertExists()
        compose.onNodeWithText("Automatic").performClick()
        verify(controller).setAudioLanguage("auto")
        verify(controller, never()).setSubtitleLanguage(anyString())
        verify(controller).go(Screen.Settings)
    }
}
