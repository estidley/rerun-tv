package com.rerun.tv.ui.channels

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.rerun.core.catalog.MinimumContentLevel
import com.rerun.core.model.AdBreakLength
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.Rules
import com.rerun.core.plex.PlexParse
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class ChannelEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun namesAndNumbersDoNotRescanTheLibraryButRulesUpdatePoolHours() = runBlocking<Unit> {
        val app = mock(RerunApp::class.java)
        val schedule = mock(ScheduleRepo::class.java)
        `when`(app.schedule).thenReturn(schedule)
        val scans = AtomicInteger()
        val items = PlexParse.movies(
            """{"MediaContainer":{"Metadata":[
                {"ratingKey":"drama","title":"Drama movie","duration":10800000,"Genre":[{"tag":"Drama"}]},
                {"ratingKey":"comedy","title":"Comedy movie","duration":18000000,"Genre":[{"tag":"Comedy"}]}
            ]}}""",
        ).map { item ->
            item.copy(network = if (item.key == "drama") "TBS" else item.network, genres = object : Set<String> by item.genres {
                override fun iterator(): Iterator<String> {
                    scans.incrementAndGet()
                    return item.genres.iterator()
                }
            })
        }
        val def = ChannelDef("test", 401, "Test channel", ChannelKind.CUSTOM, Rules(genres = setOf("Drama")))
        `when`(schedule.allChannelRows()).thenReturn(listOf(ChannelRow.of(def, builtIn = false, dormant = true)))
        `when`(schedule.libraryItems()).thenReturn(items)
        val controller = mock(TvController::class.java)
        `when`(controller.minimumContent).thenReturn(MutableStateFlow(MinimumContentLevel.MEDIUM))
        compose.setContent { ChannelEditor(controller, "test", app) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("3.0 hours of content  (needs 12 to go on the air)")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Genres: Drama · movies and episodes.").performScrollTo().assertIsDisplayed()
        scrollTo("Ted's TV")
        compose.onNodeWithText("Ted's TV").assertIsDisplayed()
        compose.onNodeWithText("TBS").assertIsDisplayed()
        scrollToTop()
        val before = scans.get()
        compose.onNodeWithText("Test channel").performTextReplacement("Renamed")
        compose.onNodeWithText("401").performTextReplacement("402")
        compose.waitForIdle()
        assertEquals(before, scans.get())
        scrollTo("No minimum")
        compose.onNodeWithText("No minimum").performClick()
        scrollToTop()
        compose.onNodeWithText("3.0 hours of content").assertExists()
        assertEquals(before, scans.get())
        scrollTo("Channel default")
        compose.onNodeWithText("Channel default").performClick()
        scrollTo("Comedy")
        compose.onAllNodesWithText("Comedy")[0].performClick()
        scrollToTop()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("8.0 hours of content  (needs 12 to go on the air)")
                .fetchSemanticsNodes().isNotEmpty()
        }
        verify(schedule, times(1)).libraryItems()
    }

    @Test
    fun cancelWithoutChangesLeavesImmediately() = runBlocking<Unit> {
        val fixture = editorFixture()
        showEditor(fixture)

        cancelEditor()
        compose.waitForIdle()

        verify(fixture.controller).go(Screen.Channels)
        compose.onNodeWithText("Unsaved changes").assertDoesNotExist()
    }

    @Test
    fun backWithChangesPromptsAndKeepEditingPreservesDraft() = runBlocking<Unit> {
        val fixture = editorFixture()
        showEditor(fixture)
        compose.onNodeWithText("Test channel").performTextReplacement("Renamed")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithText("Unsaved changes").assertIsDisplayed()
        verify(fixture.controller, never()).go(Screen.Channels)
        compose.onNodeWithText("Keep editing").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Unsaved changes").assertDoesNotExist()
        compose.onNodeWithText("Renamed").performScrollTo().assertIsDisplayed()
        verify(fixture.controller, never()).go(Screen.Channels)
    }

    @Test
    fun discardChangesLeavesWithoutSaving() = runBlocking<Unit> {
        val fixture = editorFixture()
        showEditor(fixture)
        compose.onNodeWithText("Test channel").performTextReplacement("Renamed")
        cancelEditor()

        compose.onNodeWithText("Discard changes").performClick()
        compose.waitForIdle()

        verify(fixture.schedule, never()).saveChannel(
            org.mockito.ArgumentMatchers.any(ChannelRow::class.java) ?: ChannelRow.of(fixture.def, builtIn = false),
        )
        verify(fixture.controller).go(Screen.Channels)
    }

    @Test
    fun saveChangesPersistsDraftBeforeLeaving() = runBlocking<Unit> {
        val fixture = editorFixture()
        showEditor(fixture)
        compose.onNodeWithText("Test channel").performTextReplacement("Renamed")
        cancelEditor()

        compose.onNodeWithText("Save changes").performClick()
        compose.waitForIdle()

        verify(fixture.schedule).saveChannel(ChannelRow.of(fixture.def.copy(name = "Renamed"), builtIn = false))
        verify(fixture.schedule).resetFuture(eq("test") ?: "test", anyLong())
        verify(fixture.controller).reconcileChannels()
        verify(fixture.controller).go(Screen.Channels)
    }

    @Test
    fun nonClockAndAdBreakChoicesPersist() = runBlocking<Unit> {
        val fixture = editorFixture()
        showEditor(fixture)

        scrollTo("Non-clock aligned")
        compose.onNodeWithText("Non-clock aligned").performClick()
        scrollTo("Short · about 2 min")
        compose.onNodeWithText("Short · about 2 min").performClick()
        scrollTo("No ads")
        compose.onNodeWithText("No ads").performClick()
        compose.onNodeWithText("Short · about 2 min").assertDoesNotExist()
        scrollTo("Show ads")
        compose.onNodeWithText("Show ads").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Short · about 2 min", substring = true).assertExists()
        cancelEditor()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitForIdle()

        verify(fixture.schedule).saveChannel(ChannelRow.of(
            fixture.def.copy(nonClockAligned = true, showAds = true, adBreakLength = AdBreakLength.SHORT),
            builtIn = false,
        ))
    }

    private suspend fun editorFixture(): EditorFixture {
        val app = mock(RerunApp::class.java)
        val schedule = mock(ScheduleRepo::class.java)
        val controller = mock(TvController::class.java)
        val def = ChannelDef("test", 401, "Test channel", ChannelKind.CUSTOM, Rules(genres = emptySet()))
        `when`(app.schedule).thenReturn(schedule)
        `when`(schedule.allChannelRows()).thenReturn(listOf(ChannelRow.of(def, builtIn = false, dormant = true)))
        `when`(schedule.libraryItems()).thenReturn(emptyList())
        `when`(controller.minimumContent).thenReturn(MutableStateFlow(MinimumContentLevel.MEDIUM))
        return EditorFixture(app, schedule, controller, def)
    }

    private fun showEditor(fixture: EditorFixture) {
        compose.setContent { ChannelEditor(fixture.controller, "test", fixture.app) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Test channel").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun scrollToTop() {
        compose.onAllNodes(hasScrollAction())[0].performScrollToIndex(0)
    }

    private fun scrollTo(text: String) {
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text))
    }

    private fun cancelEditor() {
        scrollTo("Cancel")
        compose.onNodeWithText("Cancel").performClick()
    }

    private data class EditorFixture(
        val app: RerunApp,
        val schedule: ScheduleRepo,
        val controller: TvController,
        val def: ChannelDef,
    )
}
