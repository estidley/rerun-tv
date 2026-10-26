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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.Rules
import com.rerun.core.schedule.ScheduleEntry
import com.rerun.core.schedule.EntryKind
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.data.Settings
import com.rerun.tv.data.LibrarySync
import com.rerun.tv.data.SyncProgress
import com.rerun.tv.player.TunerEngine
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.theme.ColorTheme
import com.rerun.tv.ui.theme.ProvideColorTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyCollection
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
    // A fixed instant mid-grid-slot keeps tests from straddling a :00/:30 boundary.
    private val baseNow = Math.floorDiv(System.currentTimeMillis(), 3_600_000L) * 3_600_000L + 10 * 60_000L

    @get:Rule val compose = createComposeRule()
    private lateinit var controller: TvController
    private lateinit var schedule: ScheduleRepo
    private lateinit var sync: LibrarySync
    private val channels = MutableStateFlow<List<ChannelRow>>(emptyList())
    private val revision = MutableStateFlow(0L)

    @Before
    fun setUp() = runBlocking {
        val app = mock(RerunApp::class.java)
        val settings = mock(Settings::class.java)
        `when`(settings.crtLevel()).thenReturn(flowOf("OFF"))
        `when`(settings.colorTheme()).thenReturn(flowOf("STANDARD_BLUE"))
        `when`(settings.guideDensity()).thenReturn(flowOf("DEFAULT"))
        `when`(settings.subtitleLanguage()).thenReturn(flowOf("auto"))
        `when`(settings.subtitleSize()).thenReturn(flowOf("MEDIUM"))
        `when`(settings.subtitleColor()).thenReturn(flowOf("WHITE"))
        `when`(settings.audioLanguage()).thenReturn(flowOf("auto"))
        `when`(settings.hdrMode()).thenReturn(flowOf("AUTO"))
        `when`(settings.flag(anyString(), anyBoolean())).thenReturn(flowOf(false))
        `when`(settings.isOn(anyString(), anyBoolean())).thenReturn(false)
        schedule = mock(ScheduleRepo::class.java)
        `when`(schedule.guideRevision).thenReturn(revision)
        `when`(app.settings).thenReturn(settings)
        `when`(app.schedule).thenReturn(schedule)
        sync = mock(LibrarySync::class.java)
        `when`(app.sync).thenReturn(sync)
        channels.value = (0 until 50).map {
            ChannelRow.of(
                ChannelDef("test:$it", it + 1, "A long channel name $it", ChannelKind.CUSTOM, Rules()),
                builtIn = false, dormant = false,
            )
        }
        `when`(schedule.activeChannels()).thenReturn(channels)
        `when`(schedule.entriesFor(anyCollection(), anyLong(), anyLong())).thenReturn(emptyMap())
        controller = TvController(app, mock(TunerEngine::class.java))
    }

    @After
    fun tearDown() {
        controller.dispose()
    }

    @Test
    fun emptyLineupShowsSyncProgressUntilCompletion() = runBlocking<Unit> {
        channels.value = emptyList()
        val finish = CompletableDeferred<Unit>()
        `when`(sync.sync()).thenReturn(flow {
            emit(SyncProgress("Reading libraries", 1, 2))
            finish.await()
        })
        controller.resyncLibrary()
        compose.setContent { GuideScreen(controller, schedule, clock = { baseNow }) }
        compose.onNodeWithText("Updating guide...").assertExists()
        compose.onNodeWithText("Elapsed: 0s").assertExists()
        compose.onNodeWithText("Reading libraries 1/2").assertExists()
        compose.onNodeWithText("No channels yet. Open Settings and run a library sync.").assertDoesNotExist()
        compose.runOnIdle { finish.complete(Unit) }
        compose.waitUntil(10_000) { !controller.lineupUpdate.value.busy }
        compose.onNodeWithText("No channels yet. Open Settings and run a library sync.").assertExists()
    }

    @Test
    fun timeHeaderRefreshesFormattingWithoutAdvancingTheWindow() {
        val context = RuntimeEnvironment.getApplication()
        val originalContext = com.rerun.tv.ui.player.clockContext
        val originalLocale = java.util.Locale.getDefault()
        val originalZone = java.util.TimeZone.getDefault()
        val originalHour = android.provider.Settings.System.getString(context.contentResolver, android.provider.Settings.System.TIME_12_24)
        val fixedNow = java.time.Instant.parse("2026-10-26T13:10:00Z").toEpochMilli()
        val start = com.rerun.core.schedule.gridFloor(fixedNow)
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            com.rerun.tv.ui.player.clockContext = context
            android.provider.Settings.System.putString(context.contentResolver, android.provider.Settings.System.TIME_12_24, "12")
            compose.setContent { GuideScreen(controller, schedule, clock = { fixedNow }) }
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("guide-grid")).fetchSemanticsNodes().isNotEmpty()
            }
            val initialLabel = com.rerun.tv.ui.player.clockTime(start)
            compose.onNodeWithText(initialLabel).assertExists()
            compose.mainClock.autoAdvance = false
            compose.runOnIdle {
                android.provider.Settings.System.putString(context.contentResolver, android.provider.Settings.System.TIME_12_24, "24")
            }
            compose.mainClock.advanceTimeBy(30_100)
            compose.waitForIdle()
            val hourLabel = com.rerun.tv.ui.player.clockTime(start)
            assertNotEquals(initialLabel, hourLabel)
            compose.onNodeWithText(hourLabel).assertExists()
            compose.onNodeWithText(initialLabel).assertDoesNotExist()
            compose.runOnIdle { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("GMT+02:00")) }
            compose.mainClock.advanceTimeBy(30_100)
            compose.waitForIdle()
            val zoneLabel = com.rerun.tv.ui.player.clockTime(start)
            assertNotEquals(hourLabel, zoneLabel)
            compose.onNodeWithText(zoneLabel).assertExists()
            compose.onNodeWithText(hourLabel).assertDoesNotExist()
        } finally {
            com.rerun.tv.ui.player.clockContext = originalContext
            java.util.Locale.setDefault(originalLocale)
            java.util.TimeZone.setDefault(originalZone)
            android.provider.Settings.System.putString(context.contentResolver, android.provider.Settings.System.TIME_12_24, originalHour)
        }
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
    fun openingAndReopeningSelectsTheCurrentStationAndProgramAfterLoading() = runBlocking<Unit> {
        val now = baseNow
        val showGuide = mutableStateOf(true)
        val release = java.util.concurrent.CountDownLatch(1)
        `when`(schedule.entriesFor(anyCollection(), anyLong(), anyLong())).thenReturn(
            listOf("test:30", "test:40").associateWith { id ->
                listOf(
                    ScheduleEntry(id, now - 60_000, now + 600_000, EntryKind.PROGRAM, null),
                    ScheduleEntry(id, now + 600_000, now + 3_600_000, EntryKind.PROGRAM, null),
                )
            },
        )
        doAnswer {
            check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
            Unit
        }.`when`(schedule).ensure(anyCollection(), anyLong())
        controller.tuneTo("test:30", sound = false)
        try {
            compose.setContent { if (showGuide.value) GuideScreen(controller, schedule, clock = { baseNow }) }
            compose.onNodeWithText("Updating guide...").assertExists()
        } finally {
            release.countDown()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasAnyAncestor(hasTestTag("guide-row:test:30")) and isSelected())
                .fetchSemanticsNodes().size == 1
        }
        val initial = compose.onNode(
            hasAnyAncestor(hasTestTag("guide-row:test:30")) and isSelected(),
        ).fetchSemanticsNode().boundsInRoot
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        val next = compose.onNode(
            hasAnyAncestor(hasTestTag("guide-row:test:30")) and isSelected(),
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(next.left > initial.left)
        compose.runOnIdle { revision.value++ }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("guide-grid")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(next.left, compose.onNode(
            hasAnyAncestor(hasTestTag("guide-row:test:30")) and isSelected(),
        ).fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.runOnIdle { showGuide.value = false }
        compose.waitForIdle()
        compose.runOnIdle {
            controller.tuneTo("test:40", sound = false)
            showGuide.value = true
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasAnyAncestor(hasTestTag("guide-row:test:40")) and isSelected())
                .fetchSemanticsNodes().size == 1
        }
        assertEquals(initial.left, compose.onNode(
            hasAnyAncestor(hasTestTag("guide-row:test:40")) and isSelected(),
        ).fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        assertEquals(Screen.Player, controller.screen.value)
        assertEquals("test:40", controller.currentChannelId())
    }

    @Test
    fun emptyGuideCanOpenSettingsAndChannelsWithTheRemote() {
        compose.runOnIdle { channels.value = emptyList() }
        compose.setContent { GuideScreen(controller, schedule, clock = { baseNow }) }
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
        compose.setContent { GuideScreen(controller, schedule, clock = { baseNow }) }
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
    fun adStationGuideDisplaysHourlyBlocksInsteadOfIndividualAds() = runBlocking<Unit> {
        val start = Math.floorDiv(baseNow, 3_600_000L) * 3_600_000L
        `when`(schedule.entriesFor(anyCollection(), anyLong(), anyLong())).thenReturn(mapOf(
            com.rerun.core.catalog.Lineup.ADS_ID to (0 until 480).map {
                ScheduleEntry(
                    com.rerun.core.catalog.Lineup.ADS_ID, start + it * 30_000L,
                    start + (it + 1) * 30_000L, EntryKind.PROGRAM, "ad:$it",
                )
            },
        ))
        compose.runOnIdle {
            channels.value = listOf(ChannelRow.of(com.rerun.core.catalog.Lineup.adsChannel, true, false))
        }
        compose.setContent { GuideScreen(controller, schedule, clock = { baseNow }) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Buy Me Maybe").fetchSemanticsNodes().size >= 4
        }
        val selected = hasAnyAncestor(hasTestTag("guide-row:ads:only")) and isSelected()
        val current = compose.onNode(selected).fetchSemanticsNode().boundsInRoot
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        assertTrue(compose.onNode(selected).fetchSemanticsNode().boundsInRoot.left > current.left)
        verify(schedule, never()).item(anyString())
        verify(schedule, never()).adCatalog()
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        assertEquals(Screen.Player, controller.screen.value)
        assertEquals(com.rerun.core.catalog.Lineup.ADS_ID, controller.currentChannelId())
    }

    @Test
    fun musicGuideDisplaysStationHoursInsteadOfIndividualSongs() = runBlocking<Unit> {
        val start = Math.floorDiv(baseNow, 3_600_000L) * 3_600_000L
        val entries = (0 until 80).map {
            ScheduleEntry("music:blues", start + it * 180_000L, start + (it + 1) * 180_000L, EntryKind.PROGRAM, "song:$it")
        }
        `when`(schedule.entriesFor(anyCollection(), anyLong(), anyLong())).thenReturn(mapOf("music:blues" to entries))
        compose.runOnIdle {
            channels.value = listOf(ChannelRow.of(
                ChannelDef("music:blues", 2000, "Blues", ChannelKind.MUSIC, Rules()),
                builtIn = true, dormant = false,
            ))
        }
        compose.setContent { GuideScreen(controller, schedule, clock = { baseNow }) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Blues Music").fetchSemanticsNodes().size >= 3
        }
        verify(schedule, never()).item(anyString())
    }

    @Test
    fun scheduleRevisionReloadsEvenWhenChannelsHaveNotChanged() {
        compose.setContent { GuideScreen(controller, schedule, clock = { baseNow }) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("guide-grid")).fetchSemanticsNodes().isNotEmpty()
        }
        clearInvocations(schedule)
        compose.runOnIdle { revision.value++ }
        compose.waitUntil(10_000) {
            mockingDetails(schedule).invocations.any { it.method.name == "entriesFor" }
        }
    }

    @Test
    fun clockTicksDoNotReloadAnUnchangedGuide() = runBlocking<Unit> {
        compose.setContent { GuideScreen(controller, schedule, clock = { baseNow }) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("guide-grid")).fetchSemanticsNodes().isNotEmpty()
        }
        clearInvocations(schedule)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(90_000)
        compose.waitForIdle()
        verify(schedule, never()).ensure(anyCollection(), anyLong())
        verify(schedule, never()).entriesFor(anyCollection(), anyLong(), anyLong())
    }

    @Test
    fun guideWaitsForMusicScanAndShowsProgressBeforeLoadingTheNewLineup() = runBlocking<Unit> {
        val finish = CompletableDeferred<Unit>()
        val start = com.rerun.core.schedule.gridFloor(baseNow)
        `when`(schedule.entriesFor(anyCollection(), anyLong(), anyLong())).thenReturn(mapOf(
            "music:jazz" to listOf(ScheduleEntry("music:jazz", start, start + 4 * 3_600_000L, EntryKind.PROGRAM, "track")),
        ))
        `when`(sync.libraries()).thenReturn(listOf(com.rerun.core.plex.Section("audio", "artist", "Music")))
        `when`(sync.sync()).thenReturn(flow {
            emit(SyncProgress("Music", 1, 2))
            finish.await()
            emit(SyncProgress("Done", 2, 2, true))
        })
        controller.setMusicEnabled(true)
        compose.setContent { GuideScreen(controller, schedule, clock = { baseNow }) }
        compose.onNodeWithText("Updating guide...").assertExists()
        compose.onNodeWithText("Music 1/2").assertExists()
        compose.onNodeWithTag("guide-grid").assertDoesNotExist()
        verify(schedule, never()).ensure(anyCollection(), anyLong())
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.Menu)
            pressKey(Key.DirectionRight)
            pressKey(Key.Enter)
        }
        compose.runOnIdle {
            assertEquals(Screen.Settings, controller.screen.value)
            channels.value = listOf(ChannelRow.of(
                ChannelDef("music:jazz", 2000, "Jazz", ChannelKind.MUSIC, Rules()),
                builtIn = true, dormant = false,
            ))
        }
        compose.waitUntil(10_000) { controller.channels.value.singleOrNull()?.id == "music:jazz" }
        compose.waitForIdle()
        compose.runOnIdle { finish.complete(Unit) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("guide-grid")).fetchSemanticsNodes().isNotEmpty()
        }
        verify(schedule).ensure(anyCollection(), anyLong())
        compose.onAllNodesWithText("Jazz Music")[0].assertExists()
    }

    @Test
    fun slowScheduleBuildHidesGridAndShowsElapsedTime() = runBlocking<Unit> {
        val release = java.util.concurrent.CountDownLatch(1)
        doAnswer {
            check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
            Unit
        }.`when`(schedule).ensure(anyCollection(), anyLong())
        try {
            compose.setContent { GuideScreen(controller, schedule, clock = { baseNow }) }
            compose.onNodeWithText("Updating guide...").assertExists()
            compose.onNodeWithText("Elapsed: 0s").assertExists()
            compose.onNodeWithTag("guide-grid").assertDoesNotExist()
            compose.onRoot().performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.Menu)
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
            verify(schedule, never()).channel(anyString())
        } finally {
            release.countDown()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("guide-grid")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertGuideRendering() {
        val theme = mutableStateOf(ColorTheme.STANDARD_BLUE)
        val metrics = RuntimeEnvironment.getApplication().resources.displayMetrics
        val density = minOf(metrics.widthPixels / 1920f, metrics.heightPixels / 1080f).coerceAtMost(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                ProvideColorTheme(theme.value) { GuideScreen(controller, schedule, clock = { baseNow }) }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("guide-grid"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val grid = compose.onNodeWithTag("guide-grid").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("guide-row:test:0").fetchSemanticsNode().boundsInRoot
        compose.onNode(
            androidx.compose.ui.test.hasContentDescription("A long channel name 0") and
                hasAnyAncestor(hasTestTag("guide-channel:test:0")),
        ).assertExists()
        val expected = guideRows(grid.height / density - 36f, 50, 0)
        val last = compose.onNodeWithTag("guide-row:test:${expected.visibleCount - 1}").fetchSemanticsNode().boundsInRoot
        assertEquals(grid.bottom, last.bottom, 1f)
        assertTrue(first.height >= 72f * density - 1f)
        compose.onNode(
            androidx.compose.ui.test.hasText("1") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("guide-channel:test:0")),
            useUnmergedTree = true,
        ).assertExists()
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
