package com.rerun.tv.ui.channels

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.rerun.core.catalog.MinimumContentLevel
import com.rerun.core.model.AdBreakLength
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaItem
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
    fun invalidSaveFromBackPromptKeepsDialogAndShowsValidationError() = runBlocking<Unit> {
        val fixture = editorFixture()
        showEditor(fixture)
        compose.onNodeWithText("Test channel").performTextReplacement("")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithText("Save changes").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Unsaved changes").assertIsDisplayed()
        compose.onNodeWithText("Give the channel a name").assertIsDisplayed()
        verify(fixture.schedule, never()).saveChannel(
            org.mockito.ArgumentMatchers.any(ChannelRow::class.java) ?: ChannelRow.of(fixture.def, builtIn = false),
        )
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
    fun findRelatedTicksAShowAndSavesAMarathon() = runBlocking<Unit> {
        fun ep(key: String, show: String, n: Int) = MediaItem(
            key = key, kind = com.rerun.core.model.MediaKind.EPISODE, title = "$show $n", year = 2002,
            durationMs = 23 * 60_000L, genres = setOf("Anime"), network = null, studio = null, contentRating = "TV-PG",
            showKey = "show:$show", showTitle = show, season = 1, episode = n, tmdbId = null, labels = emptySet(),
            collections = emptySet(), partKey = null, container = "mkv", videoCodec = "h264", audioCodec = "aac", thumb = null,
        )
        val fixture = editorFixture(listOf(ep("n1", "Naruto", 1), ep("n2", "Naruto", 2), ep("s1", "Naruto Shippūden", 1), ep("b1", "Bleach", 1)))
        showEditor(fixture)

        scrollTo("Word")
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("related-word")))[0].performTextReplacement("naruto")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Naruto Shippūden").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Bleach").assertCountEquals(0)
        compose.onAllNodesWithText("Naruto")[0].performClick()
        scrollTo("Marathon")
        compose.onNodeWithText("Marathon").performClick()
        scrollTo("Save")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()

        val expected = fixture.def.copy(order = com.rerun.core.model.PlayOrder.MARATHON, rules = fixture.def.rules.copy(titles = setOf("Naruto")))
        verify(fixture.schedule).saveChannel(ChannelRow.of(expected, builtIn = false))
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

    @Test
    fun chipControlsWrapInsideThePanelWithoutHorizontalScrolling() = runBlocking<Unit> {
        val fixture = editorFixture()
        showEditor(fixture, constrained = true)
        scrollTo("48 hours")
        val first = compose.onNodeWithText("No minimum").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithText("48 hours").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("channel-editor-panel").fetchSemanticsNode().boundsInRoot

        val output = java.io.File("build/reports/channel-editor-wrapping.png")
        output.parentFile.mkdirs()
        output.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        org.junit.Assert.assertTrue("first=$first last=$last panel=$panel", last.top > first.top)
        org.junit.Assert.assertTrue(first.left >= panel.left && last.right <= panel.right)
        compose.onAllNodesWithTag("chip-flow").fetchSemanticsNodes().forEach { node ->
            org.junit.Assert.assertFalse(SemanticsActions.ScrollBy in node.config)
            org.junit.Assert.assertFalse(SemanticsActions.ScrollToIndex in node.config)
        }
    }

    @Test
    fun largeFilterSectionsShowTwentyItemsBeforeExpandingAndCollapsing() = runBlocking<Unit> {
        val items = filterItems(25)
        assertEquals(25, com.rerun.core.catalog.Facets.from(items).collections.size)
        assertEquals("Collection 21", com.rerun.core.catalog.Facets.from(items).collections[20])
        val fixture = editorFixture(items)
        showEditor(fixture)
        compose.onNodeWithText("Show all", substring = true).assertDoesNotExist()
        scrollTo("Genre 20")
        assertEquals(2, compose.onAllNodesWithText("Genre 20").fetchSemanticsNodes().size)
        compose.onNodeWithText("Genre 21").assertDoesNotExist()
        compose.onNodeWithText("Show 21").assertDoesNotExist()
        compose.onNodeWithText("Collection 21").assertDoesNotExist()

        val sections = listOf(
            Triple("GENRES", "expand-genres", "Genre 21"),
            Triple("LEAVE OUT GENRES", "expand-exclude-genres", "Genre 21"),
            Triple("SHOWS", "expand-shows", "Show 21"),
            Triple("COLLECTIONS", "expand-collections", "Collection 21"),
        )
        for ((section, tag, hiddenItem) in sections) {
            scrollTo(section)
            scrollTo("Show next")
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Show fewer").assertExists()
            compose.onNodeWithTag("next-page").assertDoesNotExist()
            if (section == "COLLECTIONS") {
                val output = java.io.File("build/reports/collections-expanded.png")
                output.parentFile.mkdirs()
                output.outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap()
                        .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            scrollTo(hiddenItem)
            compose.onNodeWithText(hiddenItem).assertIsDisplayed()
            scrollTo("Show fewer")
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithText(hiddenItem).assertDoesNotExist()
        }
    }

    @Test
    fun expandedFiltersPageThroughLargeLibrariesInChunksOfTwenty() = runBlocking<Unit> {
        val fixture = editorFixture(filterItems(45))
        showEditor(fixture)
        scrollTo("GENRES")
        compose.onNodeWithTag("expand-genres").performScrollTo().performClick()
        compose.onNodeWithText("Genre 40").assertIsDisplayed()
        compose.onNodeWithText("Genre 41").assertDoesNotExist()
        compose.onAllNodesWithTag("chip-flow").fetchSemanticsNodes().forEach { node ->
            org.junit.Assert.assertTrue(node.children.size <= 23)
        }

        compose.onNodeWithTag("next-page").performScrollTo().performClick()
        compose.onNodeWithText("Genre 41").assertIsDisplayed()
        compose.onNodeWithText("Genre 45").assertIsDisplayed()
        compose.onNodeWithText("Genre 40").assertDoesNotExist()
        compose.onNodeWithTag("next-page").assertDoesNotExist()
        compose.onNodeWithTag("previous-page").performScrollTo().performClick()
        compose.onNodeWithText("Genre 21").assertIsDisplayed()
        compose.onNodeWithText("Genre 41").assertDoesNotExist()
        compose.onNodeWithText("Show fewer").performScrollTo().performClick()
        compose.onNodeWithText("Genre 21").assertDoesNotExist()
    }

    private fun filterItems(count: Int): List<MediaItem> = (1..count).map { index ->
        val show = com.rerun.core.model.ShowMeta(
            key = "show-$index", title = "Show %02d".format(index), year = null, studio = null,
            genres = setOf("Genre %02d".format(index)), contentRating = null, tmdbId = null,
            labels = emptySet(), collections = setOf("Collection %02d".format(index)),
        )
        PlexParse.episodes(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"episode-$index","title":"Episode","grandparentRatingKey":"${show.key}","grandparentTitle":"${show.title}","duration":1800000}]}}""",
            mapOf(show.key to show),
        ).single()
    }

    private suspend fun editorFixture(items: List<MediaItem> = emptyList()): EditorFixture {
        val app = mock(RerunApp::class.java)
        val schedule = mock(ScheduleRepo::class.java)
        val controller = mock(TvController::class.java)
        val def = ChannelDef("test", 401, "Test channel", ChannelKind.CUSTOM, Rules(genres = emptySet()))
        `when`(app.schedule).thenReturn(schedule)
        `when`(schedule.allChannelRows()).thenReturn(listOf(ChannelRow.of(def, builtIn = false, dormant = true)))
        `when`(schedule.libraryItems()).thenReturn(items)
        `when`(controller.minimumContent).thenReturn(MutableStateFlow(MinimumContentLevel.MEDIUM))
        return EditorFixture(app, schedule, controller, def)
    }

    private fun showEditor(fixture: EditorFixture, constrained: Boolean = false) {
        compose.setContent {
            if (constrained) Box(Modifier.width(600.dp)) { ChannelEditor(fixture.controller, "test", fixture.app) }
            else ChannelEditor(fixture.controller, "test", fixture.app)
        }
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
