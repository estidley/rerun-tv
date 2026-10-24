package com.rerun.tv.ui.guide

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.Rules
import com.rerun.core.schedule.ScheduleEntry
import com.rerun.core.schedule.EntryKind
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.data.Settings
import com.rerun.tv.player.TunerEngine
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.theme.ColorTheme
import com.rerun.tv.ui.theme.ProvideColorTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var controller: TvController
    private lateinit var schedule: ScheduleRepo
    private val channels = MutableStateFlow<List<ChannelRow>>(emptyList())

    @Before
    fun setUp() = runBlocking {
        val app = mock(RerunApp::class.java)
        val settings = mock(Settings::class.java)
        `when`(settings.crtLevel()).thenReturn(flowOf("OFF"))
        `when`(settings.colorTheme()).thenReturn(flowOf("STANDARD_BLUE"))
        `when`(settings.guideDensity()).thenReturn(flowOf("COMFORTABLE"))
        `when`(settings.subtitleLanguage()).thenReturn(flowOf("auto"))
        `when`(settings.flag(anyString(), anyBoolean())).thenReturn(flowOf(false))
        schedule = mock(ScheduleRepo::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.schedule).thenReturn(schedule)
        channels.value = (0 until 50).map {
            ChannelRow.of(
                ChannelDef("test:$it", it + 1, "A long channel name $it", ChannelKind.CUSTOM, Rules()),
                builtIn = false, dormant = false,
            )
        }
        `when`(schedule.activeChannels()).thenReturn(channels)
        `when`(schedule.allEntries(anyLong(), anyLong())).thenReturn(emptyMap())
        controller = TvController(app, mock(TunerEngine::class.java))
    }

    @After
    fun tearDown() {
        controller.dispose()
    }

    @Test
    fun rowsFillTheGridAlternateColorsAndUpdateWithTheme() = assertGuideRendering()

    @Test
    @Config(qualifiers = "w1280dp-h720dp-mdpi")
    fun guideScalesDownAt720p() = assertGuideRendering()

    @Test
    @Config(qualifiers = "w3840dp-h2160dp-mdpi")
    fun guideUsesAdditionalRowsAt4k() = assertGuideRendering()

    @Test
    fun emptyGuideCanOpenSettingsAndChannelsWithTheRemote() {
        compose.runOnIdle { channels.value = emptyList() }
        compose.setContent { GuideScreen(controller, schedule) }
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionRight)
            pressKey(Key.Enter)
        }
        compose.runOnIdle { assertEquals(Screen.Settings, controller.screen.value) }
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionLeft)
            pressKey(Key.Enter)
        }
        compose.runOnIdle { assertEquals(Screen.Channels, controller.screen.value) }
    }

    @Test
    fun removingAllChannelsLeavesSettingsReachable() {
        compose.setContent { GuideScreen(controller, schedule) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { channels.value = emptyList() }
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionRight)
            pressKey(Key.Enter)
        }
        compose.runOnIdle { assertEquals(Screen.Settings, controller.screen.value) }
    }

    @Test
    fun musicGuideDisplaysStationHoursInsteadOfIndividualSongs() = runBlocking<Unit> {
        val start = Math.floorDiv(System.currentTimeMillis(), 3_600_000L) * 3_600_000L
        val entries = (0 until 80).map {
            ScheduleEntry("music:blues", start + it * 180_000L, start + (it + 1) * 180_000L, EntryKind.PROGRAM, "song:$it")
        }
        `when`(schedule.allEntries(anyLong(), anyLong())).thenReturn(mapOf("music:blues" to entries))
        compose.runOnIdle {
            channels.value = listOf(ChannelRow.of(
                ChannelDef("music:blues", 2000, "Blues", ChannelKind.MUSIC, Rules()),
                builtIn = true, dormant = false,
            ))
        }
        compose.setContent { GuideScreen(controller, schedule) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Blues Music").fetchSemanticsNodes().size >= 3
        }
        verify(schedule, never()).item(anyString())
    }

    private fun assertGuideRendering() {
        val theme = mutableStateOf(ColorTheme.STANDARD_BLUE)
        val metrics = RuntimeEnvironment.getApplication().resources.displayMetrics
        val density = minOf(metrics.widthPixels / 1920f, metrics.heightPixels / 1080f).coerceAtMost(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                ProvideColorTheme(theme.value) { GuideScreen(controller, schedule) }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("guide-row:test:0"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val grid = compose.onNodeWithTag("guide-grid").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("guide-row:test:0").fetchSemanticsNode().boundsInRoot
        val expected = guideRows(grid.height / density - 36f, 50, 0)
        val last = compose.onNodeWithTag("guide-row:test:${expected.visibleCount - 1}").fetchSemanticsNode().boundsInRoot
        assertEquals(grid.bottom, last.bottom, 1f)
        assertTrue(first.height >= 72f * density - 1f)
        val textLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("A long channel name 0", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
        assertEquals(2, textLayouts.single().lineCount)
        for (choice in ColorTheme.entries) {
            compose.runOnIdle { theme.value = choice }
            compose.waitForIdle()
            val a = compose.onNodeWithTag("guide-row:test:0").captureToImage().toPixelMap()
            val b = compose.onNodeWithTag("guide-row:test:1").captureToImage().toPixelMap()
            // Sample inside the channel cell, away from borders, text, and the now-line.
            assertEquals(choice.palette.deep, a[5, 5])
            assertEquals(choice.palette.light, b[5, 5])
            val channel = compose.onNodeWithTag("guide-channel:test:0").captureToImage().toPixelMap()
            assertEquals(choice.palette.secondary, channel[0, channel.height / 2])
        }
    }
}
