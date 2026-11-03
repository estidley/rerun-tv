package com.rerun.tv.ui

import android.app.Application
import android.os.Looper
import com.rerun.core.catalog.Lineup
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.Rules
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.data.Settings
import com.rerun.tv.player.TunerEngine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TvControllerTest {
    private lateinit var controller: TvController
    private lateinit var engine: TunerEngine
    private lateinit var app: RerunApp
    private lateinit var settings: Settings
    private lateinit var repo: ScheduleRepo
    private lateinit var sync: com.rerun.tv.data.LibrarySync
    private val rows = MutableStateFlow<List<ChannelRow>>(emptyList())
    private val sharedRequests = kotlinx.coroutines.flow.MutableSharedFlow<Set<String>>(extraBufferCapacity = 1)

    @Before
    fun setUp() = runBlocking {
        app = mock(RerunApp::class.java)
        repo = mock(ScheduleRepo::class.java)
        settings = mock(Settings::class.java)
        sync = mock(com.rerun.tv.data.LibrarySync::class.java)
        `when`(app.sync).thenReturn(sync)
        engine = mock(TunerEngine::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.schedule).thenReturn(repo)
        `when`(repo.activeChannels()).thenReturn(rows)
        `when`(repo.active()).thenReturn(emptyList())
        `when`(repo.sharedRefreshRequests).thenReturn(sharedRequests)
        `when`(repo.sharedRefreshDue(anyLong())).thenReturn(false)
        `when`(settings.crtLevel()).thenReturn(flowOf("OFF"))
        `when`(settings.colorTheme()).thenReturn(flowOf("STANDARD_BLUE"))
        `when`(settings.guideDensity()).thenReturn(flowOf("DEFAULT"))
        `when`(settings.subtitleLanguage()).thenReturn(flowOf("auto"))
        `when`(settings.subtitleSize()).thenReturn(flowOf("MEDIUM"))
        `when`(settings.subtitleColor()).thenReturn(flowOf("WHITE"))
        `when`(settings.audioLanguage()).thenReturn(flowOf("auto"))
        `when`(settings.hdrMode()).thenReturn(flowOf("AUTO"))
        `when`(settings.flag(anyString(), anyBoolean())).thenReturn(flowOf(false))
        controller = TvController(app, engine)
    }

    @After
    fun tearDown() = controller.dispose()

    @Test
    fun invalidSharedCodeReportsErrorWithoutSyncOrScheduleMutation() = runBlocking<Unit> {
        controller.setSharedPilot("not-a-code")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.lineupUpdate.value.error!!.contains("Shared scheduling failed"))
        verify(sync, never()).sync()
        verify(repo, never()).setSharedPilot(anyString(), anyLong())
    }

    @Test
    fun sharedJoinSurvivesNavigationAndSerializesWithOtherLineupUpdates() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val code = com.rerun.core.schedule.SharedScheduleCode.generate(System.currentTimeMillis()).encode()
        `when`(sync.sync()).thenReturn(flow {
            emit(com.rerun.tv.data.SyncProgress("Reading", 0, 1))
            finish.await()
        })
        `when`(repo.sharedPilotStatus()).thenReturn(com.rerun.tv.data.SharedPilotStatus(code))
        controller.setSharedPilot(code)
        controller.setSharedPilot(code)
        controller.go(Screen.Guide)
        assertTrue(controller.lineupUpdate.value.busy)
        verify(sync, times(1)).sync()
        finish.complete(Unit)
        awaitSharedUpdate()
        verify(repo).setSharedPilot(eq(code), anyLong())
        assertEquals(code, controller.sharedPilot.value.code)
        assertEquals(Screen.Guide, controller.screen.value)
    }

    @Test
    fun failedSharedJoinRefreshDoesNotReplaceRecipe() = runBlocking<Unit> {
        `when`(sync.sync()).thenReturn(flow { throw IllegalStateException("Refresh unavailable") })
        controller.setSharedPilot(com.rerun.core.schedule.SharedScheduleCode.generate(System.currentTimeMillis()).encode())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.lineupUpdate.value.error!!.contains("Refresh unavailable"))
        verify(repo, never()).setSharedPilot(anyString(), anyLong())
    }

    private fun awaitSharedUpdate() {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (controller.lineupUpdate.value.busy && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertFalse("Shared update did not finish", controller.lineupUpdate.value.busy)
    }

    @Test
    fun staleSharedRequestRefreshesOnceWithoutBlockingTuningOrStartingBackgroundPlayback() = runBlocking<Unit> {
        val finish = CompletableDeferred<Unit>()
        `when`(repo.sharedRefreshDue(anyLong())).thenReturn(true)
        `when`(sync.sync()).thenReturn(flow {
            emit(com.rerun.tv.data.SyncProgress("Refreshing", 0, 1))
            finish.await()
        })
        sharedRequests.emit(setOf("tbs"))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.lineupUpdate.value.busy)
        controller.stop()
        clearInvocations(engine)
        finish.complete(Unit)
        awaitSharedUpdate()
        verify(sync, times(1)).sync()
        verify(repo).ensure(eq(setOf("tbs")) ?: setOf("tbs"), anyLong())
        verify(engine, never()).tune(anyString())
        sharedRequests.emit(setOf("tbs"))
        shadowOf(Looper.getMainLooper()).idle()
        verify(sync, times(1)).sync()
    }

    @Test
    fun sharedRefreshFailureIsVisibleAndThrottledWithoutDiscardingTheSavedSchedule() = runBlocking {
        `when`(repo.sharedRefreshDue(anyLong())).thenReturn(true)
        `when`(sync.sync()).thenReturn(flow { throw IllegalStateException("Refresh unavailable") })
        sharedRequests.emit(setOf("tbs"))
        shadowOf(Looper.getMainLooper()).idle()
        awaitSharedUpdate()
        assertTrue(controller.lineupUpdate.value.error!!.contains("using saved library"))
        sharedRequests.emit(setOf("tbs"))
        shadowOf(Looper.getMainLooper()).idle()
        verify(sync, times(1)).sync()
        verify(repo, never()).setSharedPilot(anyString(), anyLong())
    }

    @Test
    fun freshOrDisabledPilotRequestsNeverStartAScan() = runBlocking<Unit> {
        sharedRequests.emit(setOf("tbs"))
        shadowOf(Looper.getMainLooper()).idle()
        verify(sync, never()).sync()
    }

    @Test
    fun sharedJoinReconcilesRemovedCurrentChannelWithoutLeavingOrUnmutingTheMenu() = runBlocking {
        val code = com.rerun.core.schedule.SharedScheduleCode.generate(System.currentTimeMillis()).encode()
        `when`(sync.sync()).thenReturn(flow {
            `when`(repo.active()).thenReturn(listOf(channel("replacement")))
            emit(com.rerun.tv.data.SyncProgress("Done", 1, 1, true))
        })
        `when`(repo.sharedPilotStatus()).thenReturn(com.rerun.tv.data.SharedPilotStatus(code))
        controller.tuneTo("removed", sound = false)
        controller.go(Screen.SharedPilot)
        clearInvocations(engine)
        controller.setSharedPilot(code)
        awaitSharedUpdate()
        assertEquals("replacement", controller.currentChannelId())
        assertEquals(Screen.SharedPilot, controller.screen.value)
        verify(engine).tune("replacement")
        verify(engine, never()).setVisible(true)
    }

    @Test
    fun staleSharedRefreshReconcilesRemovedCurrentButDoesNotStartPlaybackAfterStop() = runBlocking<Unit> {
        val finish = CompletableDeferred<Unit>()
        `when`(repo.sharedRefreshDue(anyLong())).thenReturn(true)
        `when`(sync.sync()).thenReturn(flow {
            finish.await()
            `when`(repo.active()).thenReturn(listOf(channel("replacement")))
            emit(com.rerun.tv.data.SyncProgress("Done", 1, 1, true))
        })
        controller.tuneTo("removed", sound = false)
        controller.go(Screen.Guide)
        sharedRequests.emit(setOf("removed"))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.lineupUpdate.value.busy)
        controller.stop()
        clearInvocations(engine)
        finish.complete(Unit)
        awaitSharedUpdate()
        assertEquals("replacement", controller.currentChannelId())
        assertEquals(Screen.Guide, controller.screen.value)
        verify(engine, never()).tune(anyString())
        verify(engine, never()).setVisible(true)
    }

    @Test
    fun summaryRetriesHttpTimeoutAndCachesTheSuccessfulResponse() = runBlocking<Unit> {
        val api = mock(com.rerun.tv.data.PlexApi::class.java)
        `when`(app.plex).thenReturn(api)
        `when`(repo.guideRevision).thenReturn(MutableStateFlow(0L))
        `when`(settings.serverToken).thenReturn("test-only-placeholder")
        controller.dispose()
        `when`(settings.serverBase()).thenReturn("http://127.0.0.1:32400")
        controller = TvController(app, engine)
        val item = com.rerun.core.plex.PlexParse.movies(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"1","title":"Movie","duration":1800000}]}}""",
        ).single()
        `when`(api.getWithTimeout("http://127.0.0.1:32400", "/library/metadata/1", "test-only-placeholder", 5_000))
            .thenAnswer { throw java.io.InterruptedIOException("timeout") }
            .thenReturn("""{"MediaContainer":{"Metadata":[{"summary":"Description"}]}}""")
        assertEquals("Description", controller.programSummary(item))
        assertEquals("Description", controller.programSummary(item))
        verify(api, times(2)).getWithTimeout("http://127.0.0.1:32400", "/library/metadata/1", "test-only-placeholder", 5_000)
    }

    @Test
    fun startOverRejectsMovieClipsOnTheAdStationWithoutChangingPlayback() = runBlocking<Unit> {
        val item = com.rerun.core.plex.PlexParse.movies(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"ad","title":"Commercial","duration":30000}]}}""",
        ).single()
        val entry = com.rerun.core.schedule.ScheduleEntry(
            Lineup.ADS_ID, 0, 30_000, com.rerun.core.schedule.EntryKind.PROGRAM, item.key,
        )
        `when`(engine.state).thenReturn(MutableStateFlow(com.rerun.tv.player.TunerState(
            channel = ChannelRow.of(Lineup.adsChannel, true, false), item = item, tuneId = 1,
            located = com.rerun.core.schedule.Located(entry, 0, null),
        )))
        controller.tuneTo(Lineup.ADS_ID)
        clearInvocations(engine, repo)
        controller.startEpisodeOver()
        shadowOf(Looper.getMainLooper()).idle()
        verify(engine).notify("Start over works while a show or movie is playing.")
        verify(repo, never()).airFromStart(org.mockito.ArgumentMatchers.eq(item) ?: item, org.mockito.ArgumentMatchers.anyLong())
        verify(engine, never()).armReturn(anyString())
        verify(engine, never()).tune(anyString())
        assertEquals(Lineup.ADS_ID, controller.currentChannelId())
    }

    @Test
    fun startOverForAnUnchangedMovieTuneSwitchesToTheHiddenChannel() = runBlocking<Unit> {
        val item = com.rerun.core.plex.PlexParse.movies(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"1","title":"Movie","duration":1800000}]}}""",
        ).single()
        val entry = com.rerun.core.schedule.ScheduleEntry("east", 0, 1_800_000, com.rerun.core.schedule.EntryKind.PROGRAM, item.key)
        `when`(engine.state).thenReturn(MutableStateFlow(com.rerun.tv.player.TunerState(
            channel = channel("east"), item = item, tuneId = 1,
            located = com.rerun.core.schedule.Located(entry, 0, null),
        )))
        controller.tuneTo("east")
        controller.startEpisodeOver()
        shadowOf(Looper.getMainLooper()).idle()
        verify(engine).armReturn("east")
        verify(engine).tune(Lineup.RESTART_ID)
    }

    @Test
    fun startOverRejectsAnObsoleteTuneEvenAfterReturningToTheSameChannel() = runBlocking<Unit> {
        val item = com.rerun.core.plex.PlexParse.movies(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"1","title":"Movie","duration":1800000}]}}""",
        ).single()
        val entry = com.rerun.core.schedule.ScheduleEntry("east", 0, 1_800_000, com.rerun.core.schedule.EntryKind.PROGRAM, item.key)
        val state = MutableStateFlow(com.rerun.tv.player.TunerState(
            channel = channel("east"), item = item, tuneId = 1,
            located = com.rerun.core.schedule.Located(entry, 0, null),
        ))
        `when`(engine.state).thenReturn(state)
        val finish = CompletableDeferred<Unit>()
        doAnswer {
            @Suppress("UNCHECKED_CAST")
            val continuation = it.arguments.last() as Continuation<Unit>
            (suspend { finish.await() }).startCoroutineUninterceptedOrReturn(continuation)
        }.`when`(repo).airFromStart(org.mockito.ArgumentMatchers.eq(item) ?: item, org.mockito.ArgumentMatchers.anyLong())
        controller.tuneTo("east")
        controller.startEpisodeOver()
        verify(repo).airFromStart(org.mockito.ArgumentMatchers.eq(item) ?: item, org.mockito.ArgumentMatchers.anyLong())
        controller.tuneTo("other")
        controller.tuneTo("east")
        state.value = state.value.copy(tuneId = 3)
        finish.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        verify(engine, never()).armReturn(anyString())
        verify(engine, never()).tune(Lineup.RESTART_ID)
        assertEquals("east", controller.currentChannelId())
    }

    @Test
    fun startOverWithNothingRestartableTellsTheViewer() {
        `when`(engine.state).thenReturn(MutableStateFlow(com.rerun.tv.player.TunerState()))
        controller.tuneTo("east")
        controller.startEpisodeOver()
        shadowOf(Looper.getMainLooper()).idle()
        verify(engine, timeout(2_000)).notify(anyString())
        verify(engine, never()).armReturn(anyString())
    }

    @Test
    fun editingTheAdStationOpensItsCatalogSettings() {
        controller.go(Screen.Editor(Lineup.ADS_ID))
        assertEquals(Screen.AdLibrary, controller.screen.value)
    }

    @Test
    fun disablingAdsSurvivesNavigationAndDefersReplacementPlaybackWhenStopped() = runBlocking<Unit> {
        val finish = CompletableDeferred<Unit>()
        `when`(settings.isSignedIn).thenReturn(true)
        `when`(repo.active()).thenReturn(listOf(channel("east")))
        `when`(sync.syncAds(null)).thenReturn(flow {
            emit(com.rerun.tv.data.SyncProgress("Ads disabled", 0, 0))
            finish.await()
        })
        controller.tuneTo(Lineup.ADS_ID, sound = false)
        controller.setAdLibrary(null)
        assertTrue(controller.lineupUpdate.value.busy)
        controller.go(Screen.Guide)
        controller.stop()
        clearInvocations(engine)
        finish.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.lineupUpdate.value.busy)
        assertEquals("east", controller.currentChannelId())
        assertEquals(Screen.Guide, controller.screen.value)
        verify(engine, never()).tune(anyString())
        controller.resume()
        verify(engine).tune("east")
        verify(engine, never()).setVisible(true)
    }

    @Test
    fun musicToggleRestoresTheCleanVersionMarkerOnlyAfterSuccess() = runBlocking<Unit> {
        `when`(settings.isSignedIn).thenReturn(true)
        `when`(settings.lineupAppVersion()).thenReturn("3:100")
        `when`(settings.isOn(Settings.MUSIC, false)).thenReturn(false)
        `when`(repo.active()).thenReturn(listOf(channel("east")))
        controller.setMusicEnabled(false)
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!controller.lineupUpdate.value.busy) return@repeat
            Thread.sleep(20)
        }
        assertFalse(controller.lineupUpdate.value.busy)
        verify(settings).invalidateLineupAppVersion()
        verify(settings).setLineupAppVersion("3:100")
    }

    @Test
    fun editingOpensTheSelectedChannel() {
        controller.go(Screen.Editor("custom:one"))
        assertEquals(Screen.Editor("custom:one"), controller.screen.value)
    }

    @Test
    fun leavingRestartNeverAddsTheHiddenChannelToLastChannelHistory() {
        controller.tuneTo("east", sound = false)
        controller.tuneTo(Lineup.RESTART_ID, sound = false)
        controller.tuneTo("other", sound = false)
        controller.lastChannel()
        assertEquals("east", controller.currentChannelId())
    }

    @Test
    fun normalLastChannelStillToggles() {
        controller.tuneTo("east", sound = false)
        controller.tuneTo("other", sound = false)
        controller.lastChannel()
        assertEquals("east", controller.currentChannelId())
        controller.lastChannel()
        assertEquals("other", controller.currentChannelId())
    }

    @Test
    fun guideAndSettingsStayMutedUntilReturningToThePlayer() {
        controller.go(Screen.Player)
        verify(engine).setVisible(true)
        clearInvocations(engine)
        controller.go(Screen.Guide)
        controller.go(Screen.Settings)
        verify(engine, times(2)).setVisible(false)
        controller.closeGuideWithBack()
        verify(engine).setVisible(true)
    }

    @Test
    fun guideAndChannelSurfingFollowChannelNumbers() {
        fun channel(id: String, number: Int) =
            ChannelRow.of(ChannelDef(id, number, id, ChannelKind.CUSTOM, Rules()), builtIn = false, dormant = false)
        rows.value = listOf(
            channel("three", 6), channel("one", 2), channel("two", 4),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            listOf("one", "two", "three"),
            controller.channels.value.map { it.id },
        )
        controller.tuneTo("one", sound = false)
        controller.channelUp()
        assertEquals("two", controller.currentChannelId())
        controller.channelUp()
        assertEquals("three", controller.currentChannelId())
        controller.channelDown()
        assertEquals("two", controller.currentChannelId())
        controller.channelUp()
        controller.channelUp()
        assertEquals("one", controller.currentChannelId())
        rows.value = rows.value.filterNot { it.id == "two" }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("one", "three"), controller.channels.value.map { it.id })
    }

    @Test
    fun signingOutClearsPlaybackAndNavigationHistory() = runBlocking<Unit> {
        controller.tuneTo("east", sound = false)
        controller.tuneTo("other", sound = false)
        controller.go(Screen.Settings)
        controller.signOut()
        assertEquals(Screen.Onboarding, controller.screen.value)
        assertEquals(null, controller.currentChannelId())
        controller.lastChannel()
        assertEquals(null, controller.currentChannelId())
        verify(engine).clear()
        verify(sync).signOut()
    }

    @Test
    fun missingServerConfigurationReturnsToSetupInsteadOfAnEmptyPlayer() = runBlocking<Unit> {
        `when`(settings.isSignedIn).thenReturn(true)
        `when`(settings.lastSyncMs()).thenReturn(123L)
        controller.go(Screen.Player)
        controller.startWatching()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Screen.Onboarding, controller.screen.value)
        verify(engine).clear()
    }

    @Test
    fun preferenceUpdatesWhileStoppedDoNotRestartPlayback() = runBlocking<Unit> {
        controller.stop()
        clearInvocations(engine)
        controller.setAudioLanguage("es")
        controller.setHdrMode(com.rerun.core.plex.HdrMode.AUTO)
        controller.setNormalizeLoudness(true)
        controller.setSubtitleLanguage("es")
        verify(engine, never()).refreshPlaybackPreferences()
        verify(engine, never()).setSubtitles(anyBoolean())
        verify(settings).setAudioLanguage("es")
        verify(settings).setFlag(Settings.NORMALIZE_LOUDNESS, true)
    }

    @Test
    fun resumingOnboardingRestoresForegroundEligibilityBeforeItsFirstTune() {
        controller.stop()
        controller.resume()
        assertEquals(Screen.Onboarding, controller.screen.value)
        controller.tuneTo("east", sound = false)
        verify(engine).tune("east")
    }

    @Test
    fun startupWaitsForAutomaticRefreshBeforeDecidingWhetherChannelsExist() = runBlocking<Unit> {
        val ready = delayStartup()
        val east = channel("east")
        `when`(repo.active()).thenReturn(listOf(east))
        controller.go(Screen.Player)
        controller.startWatching()
        assertTrue(controller.startup.value.loading)
        assertFalse(controller.startup.value.empty)
        verify(repo, never()).active()
        ready.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(StartupState(), controller.startup.value)
        verify(engine).tune("east")
    }

    @Test
    fun startupOnlyPublishesEmptyAfterSuccessfulRefreshAndLookup() = runBlocking<Unit> {
        val ready = delayStartup()
        `when`(repo.active()).thenReturn(emptyList())
        controller.startWatching()
        assertFalse(controller.startup.value.empty)
        ready.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(StartupState(empty = true), controller.startup.value)
        verify(engine, never()).tune(anyString())
    }

    @Test
    fun failedStartupIsNotReportedAsAnEmptyLineup() = runBlocking<Unit> {
        val ready = delayStartup()
        controller.startWatching()
        ready.completeExceptionally(IllegalStateException("Database unavailable"))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.startup.value.loading)
        assertFalse(controller.startup.value.empty)
        assertTrue(controller.startup.value.error!!.contains("Couldn't load channels"))
        verify(repo, never()).active()
    }

    @Test
    fun signingOutCancelsPendingStartupBeforeItCanTune() = runBlocking<Unit> {
        val ready = delayStartup()
        controller.startWatching()
        controller.signOut()
        ready.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(StartupState(), controller.startup.value)
        assertEquals(Screen.Onboarding, controller.screen.value)
        verify(engine, never()).tune(anyString())
    }

    @Test
    fun startupCompletingInBackgroundDefersPlaybackUntilResume() = runBlocking<Unit> {
        val ready = delayStartup()
        `when`(repo.active()).thenReturn(listOf(channel("east")))
        controller.startWatching()
        controller.stop()
        ready.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        verify(engine, never()).tune(anyString())
        controller.resume()
        verify(engine).tune("east")
    }

    @Test
    fun lineupUpdateCompletingAfterStopDefersReplacementUntilResume() = runBlocking<Unit> {
        val finish = CompletableDeferred<Unit>()
        `when`(settings.isSignedIn).thenReturn(true)
        `when`(sync.sync()).thenReturn(flow {
            emit(com.rerun.tv.data.SyncProgress("Updating guide", 0, 0))
            finish.await()
        })
        `when`(repo.active()).thenReturn(listOf(channel("east")))
        controller.tuneTo("removed", sound = false)
        controller.resyncLibrary()
        controller.stop()
        clearInvocations(engine)
        finish.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.lineupUpdate.value.busy)
        assertEquals("east", controller.currentChannelId())
        verify(engine, never()).tune(anyString())
        controller.resume()
        verify(engine).tune("east")
    }

    @Test
    fun savingLibrariesSurvivesNavigationAfterCommitAndRetunesTheRemainingStation() = runBlocking<Unit> {
        val finish = CompletableDeferred<Unit>()
        var completed = false
        `when`(repo.active()).thenReturn(listOf(channel("east")))
        `when`(sync.sync(setOf("movies"))).thenReturn(flow {
            emit(com.rerun.tv.data.SyncProgress("Updating guide", 0, 0))
            finish.await()
            completed = true
        })
        controller.tuneTo("east", sound = false)
        clearInvocations(engine)
        controller.go(Screen.Libraries)
        controller.saveLibraries(setOf("movies"))
        assertTrue(controller.lineupUpdate.value.busy)
        controller.go(Screen.Settings)
        finish.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(completed)
        assertFalse(controller.lineupUpdate.value.busy)
        assertEquals(Screen.Settings, controller.screen.value)
        verify(engine).tune("east")
        verify(engine, never()).setVisible(true)
    }

    private fun channel(id: String) =
        ChannelRow.of(ChannelDef(id, 1, id, ChannelKind.CUSTOM, Rules()), false, false)

    private suspend fun delayStartup(): CompletableDeferred<Unit> {
        val ready = CompletableDeferred<Unit>()
        `when`(settings.isSignedIn).thenReturn(true)
        `when`(settings.serverBase()).thenReturn("http://127.0.0.1:32400")
        `when`(settings.lastSyncMs()).thenReturn(123L)
        doAnswer {
            @Suppress("UNCHECKED_CAST")
            val continuation = it.rawArguments.last() as Continuation<Unit>
            (suspend { ready.await() }).startCoroutineUninterceptedOrReturn(continuation)
        }.`when`(app).awaitLineupRefresh()
        return ready
    }

    @Test
    fun removingAChannelChoosesTheFirstActiveChannelWithoutLeavingOrUnmutingSettings() = runBlocking<Unit> {
        val east = ChannelRow.of(ChannelDef("east", 1, "East", ChannelKind.CUSTOM, Rules()), false, false)
        `when`(repo.active()).thenReturn(listOf(east))
        controller.tuneTo("removed-music", sound = false)
        controller.tuneTo("removed", sound = false)
        controller.go(Screen.Settings)
        clearInvocations(engine)
        controller.reconcileChannels()
        assertEquals("east", controller.currentChannelId())
        assertEquals(Screen.Settings, controller.screen.value)
        verify(engine).tune("east")
        verify(engine, never()).setVisible(true)
        controller.lastChannel()
        assertEquals("east", controller.currentChannelId())
    }

    @Test
    fun removingTheOnlyChannelClearsPlaybackWithoutLeavingTheMenu() = runBlocking<Unit> {
        `when`(repo.active()).thenReturn(emptyList())
        controller.tuneTo("music:jazz", sound = false)
        controller.go(Screen.Settings)
        controller.reconcileChannels()
        assertEquals(null, controller.currentChannelId())
        assertEquals(Screen.Settings, controller.screen.value)
        verify(engine).clear()
    }
}
