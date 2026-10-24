package com.rerun.tv.ui.channels

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.Rules
import com.rerun.core.plex.PlexParse
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.ui.TvController
import java.util.concurrent.atomic.AtomicInteger
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
    @get:Rule val compose = createComposeRule()

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
            item.copy(genres = object : Set<String> by item.genres {
                override fun iterator(): Iterator<String> {
                    scans.incrementAndGet()
                    return item.genres.iterator()
                }
            })
        }
        val def = ChannelDef("test", 401, "Test channel", ChannelKind.CUSTOM, Rules(genres = setOf("Drama")))
        `when`(schedule.allChannelRows()).thenReturn(listOf(ChannelRow.of(def, builtIn = false, dormant = true)))
        `when`(schedule.libraryItems()).thenReturn(items)
        compose.setContent { ChannelEditor(mock(TvController::class.java), "test", app) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("3.0 hours of content  (needs 4 to go on the air)")
                .fetchSemanticsNodes().isNotEmpty()
        }
        val before = scans.get()
        compose.onNodeWithText("Test channel").performTextReplacement("Renamed")
        compose.onNodeWithText("401").performTextReplacement("402")
        compose.waitForIdle()
        assertEquals(before, scans.get())
        compose.onAllNodesWithText("Comedy")[0].performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("8.0 hours of content").fetchSemanticsNodes().isNotEmpty()
        }
        verify(schedule, times(1)).libraryItems()
    }
}
