package com.rerun.tv.data

import android.app.Application
import androidx.room.Room
import com.rerun.core.catalog.Catalog
import com.rerun.core.plex.PlexParse
import com.rerun.core.schedule.EntryKind
import com.rerun.core.schedule.ScheduleEntry
import com.rerun.core.schedule.Scheduler
import com.rerun.core.schedule.SharedPilotScheduler
import com.rerun.core.schedule.SharedScheduleCode
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class SharedPilotRepoTest {
    private lateinit var db: RerunDb
    private lateinit var repo: ScheduleRepo
    private val now = 1_800_000_000_000L
    private val def = Catalog.builtInChannels().single { it.id == "tbs" }
    private val movie = PlexParse.movies(
        """{"MediaContainer":{"Metadata":[{"ratingKey":"1","title":"Movie","studio":"TBS","duration":3600000,
            "Media":[{"container":"mp4","Part":[{"key":"/library/parts/1/file.mp4"}]}]}]}}""",
    ).single()
    private val recipe = SharedScheduleCode(42L, Math.floorDiv(now, 86_400_000L) - 1)

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), RerunDb::class.java)
            .allowMainThreadQueries().build()
        repo = ScheduleRepo(db, Scheduler(ZoneId.of("UTC")))
        db.items().replaceAll(listOf(ItemRow.of(movie)))
        db.channels().upsert(ChannelRow.of(def, true, false))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun joinCrossingUtcBoundaryPreservesTheNewlyAiringProgramAndBreak() = runBlocking {
        val boundary = nextSharedBoundary(now)
        val requestedAt = boundary - 1_000
        val program = ScheduleEntry(def.id, boundary, boundary + 3_600_000, EntryKind.PROGRAM, movie.key)
        val filler = ScheduleEntry(def.id, program.endMs, program.endMs + 60_000, EntryKind.FILLER, null)
        db.entries().insertAll(listOf(program, filler).map(EntryRow::of))
        var elapsed = 0L
        repo = ScheduleRepo(db, Scheduler(ZoneId.of("UTC")), monotonicMs = { elapsed }) { channel, pool, code ->
            elapsed = 2_000
            SharedPilotScheduler.fingerprint(channel, pool, code)
        }
        repo.setSharedPilot(recipe.encode(), requestedAt)
        assertEquals(listOf(program, filler), repo.entries(def.id, boundary, filler.endMs))
        val cutover = db.settings().get("${Settings.SHARED_PREFIX}channel:${def.id}")!!
            .substringBefore('|').toLong()
        assertEquals(nextSharedBoundary(boundary + 1_000), cutover)
    }

    @Test
    fun fingerprintRebuildCrossingUtcBoundaryPreservesTheNewlyAiringProgramAndBreak() = runBlocking {
        var elapsed = 0L
        var delayFingerprint = false
        repo = ScheduleRepo(db, Scheduler(ZoneId.of("UTC")), monotonicMs = { elapsed }) { channel, pool, code ->
            if (delayFingerprint) elapsed += 2_000
            SharedPilotScheduler.fingerprint(channel, pool, code)
        }
        repo.setSharedPilot(recipe.encode(), now)
        repo.ensure(def.id, now)
        val boundary = nextSharedBoundary(now)
        val expected = repo.entries(def.id, boundary, boundary + 3_600_000)
        assertTrue(expected.isNotEmpty())
        val breakEntry = repo.entries(def.id, expected.last().endMs, expected.last().endMs + 1)
        db.items().replaceAll(listOf(ItemRow.of(movie.copy(durationMs = 30 * 60_000))))
        repo.invalidateLibrary()
        delayFingerprint = true
        repo.ensure(def.id, boundary - 1_000)
        assertEquals(expected, repo.entries(def.id, boundary, boundary + 3_600_000))
        assertEquals(breakEntry, repo.entries(def.id, expected.last().endMs, expected.last().endMs + 1))
        val cutover = db.settings().get("${Settings.SHARED_PREFIX}channel:${def.id}")!!
            .substringBefore('|').toLong()
        assertTrue(cutover > boundary + 1_000)
    }

    @Test
    fun fingerprintsAreReusedAcrossValidationStatusAndHorizonExtensions() = runBlocking {
        var builds = 0
        repo = ScheduleRepo(db, Scheduler(ZoneId.of("UTC"))) { channel, pool, code ->
            builds++
            SharedPilotScheduler.fingerprint(channel, pool, code)
        }
        repo.setSharedPilot(recipe.encode(), now)
        assertEquals(1, builds)
        repeat(2) { repo.sharedPilotStatus() }
        repo.ensure(def.id, now)
        repo.ensure(def.id, now + 30 * 3_600_000L)
        assertEquals(1, builds)
        val expected = SharedPilotScheduler.fingerprint(def, listOf(movie), recipe)
        assertTrue(repo.sharedPilotStatus().channels.single().contains(expected))
        assertEquals(1, builds)
    }

    @Test
    fun changedDefinitionCodeAndLibraryDoNotReuseOldFingerprints() = runBlocking {
        var builds = 0
        repo = ScheduleRepo(db, Scheduler(ZoneId.of("UTC"))) { channel, pool, code ->
            builds++
            SharedPilotScheduler.fingerprint(channel, pool, code)
        }
        repo.setSharedPilot(recipe.encode(), now)
        val original = repo.sharedPilotStatus().channels.single()
        val renumbered = def.copy(number = if (def.number == 199) 198 else 199)
        db.channels().upsert(ChannelRow.of(renumbered, true, false))
        assertNotEquals(original, repo.sharedPilotStatus().channels.single())
        assertEquals(2, builds)
        val newRecipe = recipe.copy(seed = 99L)
        repo.setSharedPilot(newRecipe.encode(), now)
        val before = repo.sharedPilotStatus().channels.single()
        assertEquals(3, builds)
        val changed = movie.copy(durationMs = 30 * 60_000L)
        db.items().replaceAll(listOf(ItemRow.of(changed)))
        repo.invalidateLibrary()
        val status = repo.sharedPilotStatus().channels.single()
        assertNotEquals(before, status)
        assertTrue(status.contains(SharedPilotScheduler.fingerprint(renumbered, listOf(changed), newRecipe)))
        assertEquals(4, builds)
        repo.sharedPilotStatus()
        assertEquals(4, builds)
    }

    @Test
    fun invalidatedInFlightFingerprintIsRecomputedFromTheNewSnapshot() = runBlocking {
        var builds = 0
        val changed = movie.copy(durationMs = 30 * 60_000L)
        repo = ScheduleRepo(db, Scheduler(ZoneId.of("UTC"))) { channel, pool, code ->
            builds++
            val result = SharedPilotScheduler.fingerprint(channel, pool, code)
            if (builds == 1) {
                runBlocking { db.items().replaceAll(listOf(ItemRow.of(changed))) }
                repo.invalidateLibrary()
            }
            result
        }
        repo.setSharedPilot(recipe.encode(), now)
        assertEquals(2, builds)
        assertTrue(repo.sharedPilotStatus().channels.single()
            .contains(SharedPilotScheduler.fingerprint(def, listOf(changed), recipe)))
        assertEquals(2, builds)
    }

    @Test
    fun fingerprintCacheIsBoundedAndDisabledStatusDoesNotResolveInvalidCatalog() = runBlocking {
        var builds = 0
        repo = ScheduleRepo(db, Scheduler(ZoneId.of("UTC"))) { channel, pool, code ->
            builds++
            SharedPilotScheduler.fingerprint(channel, pool, code)
        }
        repeat(130) { repo.setSharedPilot(recipe.copy(seed = it.toLong()).encode(), now) }
        assertEquals(130, builds)
        repo.sharedPilotStatus()
        assertEquals(130, builds)
        repo.setSharedPilot(recipe.copy(seed = 0L).encode(), now)
        assertEquals(131, builds)
        repo.setSharedPilot(null, now)
        db.items().replaceAll(listOf(ItemRow.of(movie.copy(key = "invalid\n"))))
        repo.invalidateLibrary()
        assertTrue(repo.sharedPilotStatus().channels.single().contains(": off"))
        assertEquals(131, builds)
    }

    @Test
    fun coldJoinUsesCurrentWholeBlockAndFortyEightHoursAcrossRestart() = runBlocking {
        repo.setSharedPilot(recipe.encode(), now)
        repo.ensure(now)
        assertTrue(repo.entries(def.id, now, now + SHARED_HORIZON_MS).isEmpty())
        repo.ensure(def.id, now)
        val entries = repo.entries(def.id, now, now + SHARED_HORIZON_MS)
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.first().startMs <= now)
        assertTrue(entries.last().endMs >= now + SHARED_HORIZON_MS)
        assertEquals(SharedPilotScheduler.generate(def, listOf(movie), recipe, now, now + SHARED_HORIZON_MS), entries)
        val restarted = ScheduleRepo(db, Scheduler(ZoneId.of("America/Los_Angeles")))
        restarted.ensure(def.id, now)
        assertEquals(entries, restarted.entries(def.id, now, now + SHARED_HORIZON_MS))
    }

    @Test
    fun nearbyRepeatedEnsuresReuseBookedHorizonWithoutAppendingEveryClockTick() = runBlocking {
        repo.setSharedPilot(recipe.encode(), now)
        repo.ensure(def.id, now)
        val booked = repo.entries(def.id, now, Long.MAX_VALUE)
        // Move enough to cross the previous horizon's final block. A routine ensure
        // must still use the existing >24-hour queue instead of extending each tick.
        val later = nextSharedBoundary(now) + 60_000
        repo.ensure(def.id, later)
        assertEquals(booked, repo.entries(def.id, now, Long.MAX_VALUE))
        repo.ensure(def.id, now + 30 * 3_600_000L)
        assertTrue(repo.entries(def.id, now, Long.MAX_VALUE).last().endMs > booked.last().endMs)
    }

    @Test
    fun changingRecipePreservesStraddlingProgramAndFollowingBreakUntilSafeUtcBoundary() = runBlocking {
        val boundary = nextSharedBoundary(now)
        val program = ScheduleEntry(def.id, now - 60_000, boundary + 60_000, EntryKind.PROGRAM, movie.key)
        val filler = ScheduleEntry(def.id, program.endMs, program.endMs + 60_000, EntryKind.FILLER, null)
        db.entries().insertAll(listOf(program, filler,
            program.copy(startMs = filler.endMs, endMs = filler.endMs + 3_600_000)).map(EntryRow::of))
        repo.setSharedPilot(recipe.encode(), now)
        repo.ensure(def.id, now)
        val entries = repo.entries(def.id, now, now + SHARED_HORIZON_MS)
        assertEquals(program, entries[0])
        assertEquals(filler, entries[1])
        val cutover = nextSharedBoundary(filler.endMs)
        assertEquals(cutover, entries[2].endMs)
        assertEquals(cutover, entries[3].startMs)
        assertTrue(entries.zipWithNext().all { (a, b) -> a.endMs == b.startMs })
        val old = entries.filter { it.startMs < nextSharedBoundary(now) }
        repo.setSharedPilot(SharedScheduleCode(99, recipe.anchorDay).encode(), now + 1)
        repo.ensure(def.id, now + 1)
        assertEquals(old, repo.entries(def.id, now, nextSharedBoundary(now)))
    }

    @Test
    fun invalidAndFutureCodesLeaveSettingsAndQueuesUntouched() = runBlocking {
        repo.ensure(def.id, now)
        val before = repo.entries(def.id, now, now + SHARED_HORIZON_MS)
        for (text in listOf("invalid", SharedScheduleCode(1, recipe.anchorDay + 3).encode())) {
            try {
                repo.setSharedPilot(text, now)
                fail("Expected validation failure")
            } catch (_: IllegalArgumentException) { }
            assertNull(db.settings().get(Settings.SHARED_CODE))
            assertEquals(before, repo.entries(def.id, now, now + SHARED_HORIZON_MS))
        }
    }

    @Test
    fun groupedCodeRoundTripsWithoutTruncationAndOverlongRawInputIsRejected() = runBlocking {
        val grouped = groupedSharedCode(recipe.encode())
        assertEquals(29, grouped.length)
        assertEquals(recipe, validateSharedCode(grouped, now))
        repo.setSharedPilot("  $grouped  ", now)
        assertEquals(recipe.encode(), db.settings().get(Settings.SHARED_CODE))
        try {
            repo.setSharedPilot("X".repeat(129), now)
            fail("Raw input must be bounded")
        } catch (_: IllegalArgumentException) { }
        assertEquals(recipe.encode(), db.settings().get(Settings.SHARED_CODE))
    }

    @Test
    fun stalePilotExtensionsSignalControllerWithoutBlockingSavedSnapshotGeneration() = runBlocking {
        repo.setSharedPilot(recipe.encode(), now)
        assertTrue(repo.sharedRefreshDue(now))
        repo.ensure(listOf(def.id), now)
        assertEquals(setOf(def.id), repo.sharedRefreshRequests.replayCache.single())
        assertTrue(repo.entries(def.id, now, now + 1).isNotEmpty())
        db.settings().put(SettingRow(Settings.LAST_SYNC, now.toString()))
        assertFalse(repo.sharedRefreshDue(now + 23 * 3_600_000L))
        assertTrue(repo.sharedRefreshDue(now + 24 * 3_600_000L))
        val reopened = ScheduleRepo(db, Scheduler(ZoneId.of("UTC")))
        reopened.ensure(listOf(def.id), now + 1)
        assertTrue(reopened.sharedRefreshRequests.replayCache.isEmpty())
        repo.setSharedPilot(null, now)
        assertFalse(repo.sharedRefreshDue(now + 48 * 3_600_000L))
    }

    @Test
    fun invalidCatalogIdentityIsRejectedBeforeChangingRecipeOrFuture() = runBlocking {
        repo.ensure(def.id, now)
        val before = repo.entries(def.id, now, now + SHARED_HORIZON_MS)
        db.items().replaceAll(listOf(movie, movie.copy(key = "invalid\n")).map(ItemRow::of))
        repo.invalidateLibrary()
        try {
            repo.setSharedPilot(recipe.encode(), now)
            fail("Invalid catalog identity must fail before committing")
        } catch (_: IllegalArgumentException) { }
        assertNull(db.settings().get(Settings.SHARED_CODE))
        assertEquals(before, repo.entries(def.id, now, now + SHARED_HORIZON_MS))
    }

    @Test
    fun disableKeepsCurrentSharedProgramThenReturnsToLocalScheduling() = runBlocking {
        repo.setSharedPilot(recipe.encode(), now)
        repo.ensure(def.id, now)
        val current = repo.entries(def.id, now, now + 1).single()
        repo.setSharedPilot(null, now)
        repo.ensure(def.id, now)
        assertNull(db.settings().get(Settings.SHARED_CODE))
        assertEquals(current, repo.entries(def.id, now, now + 1).single())
        assertNotNull(db.states().get(def.id))
        assertTrue(repo.sharedTiming(def.id, now))
        val cutover = db.settings().get("${Settings.SHARED_PREFIX}channel:${def.id}")!!
            .substringBefore('|').toLong()
        assertFalse(repo.sharedTiming(def.id, cutover))
    }

    @Test
    fun changedPoolRebuildsFutureOnlyAndFingerprintIsVisible() = runBlocking {
        repo.setSharedPilot(recipe.encode(), now)
        repo.ensure(def.id, now)
        val current = repo.entries(def.id, now, now + 1).single()
        val before = repo.sharedPilotStatus().channels
        db.items().replaceAll(listOf(ItemRow.of(movie.copy(durationMs = 30 * 60_000))))
        repo.invalidateLibrary()
        repo.ensure(def.id, now)
        assertEquals(current, repo.entries(def.id, now, now + 1).single())
        assertNotEquals(before, repo.sharedPilotStatus().channels)
    }

    @Test
    fun presentationEditPreservesCurrentAndRebuildsWithoutMidBlockGaps() = runBlocking {
        repo.setSharedPilot(recipe.encode(), now)
        repo.ensure(def.id, now)
        val current = repo.entries(def.id, now, now + 1).single()
        db.channels().upsert(ChannelRow.of(def.copy(name = "My station"), true, false))
        repo.resetFuture(def.id, now)
        val entries = repo.entries(def.id, now, now + SHARED_HORIZON_MS)
        assertEquals(current, entries.first())
        assertTrue(entries.zipWithNext().all { (a, b) -> a.endMs == b.startMs })
        assertTrue(entries.last().endMs >= now + SHARED_HORIZON_MS)
    }

    @Test
    fun localBookingSurvivesConcurrentRetunesRestartAndCatalogReplacementWithoutRedraw() = runBlocking {
        val entry = ScheduleEntry(def.id, now, now + 60_000, EntryKind.FILLER, null)
        val key = "${Settings.SHARED_PREFIX}break:${def.id}:${entry.startMs}:${entry.endMs}"
        db.settings().put(SettingRow(key, ""))
        val ad = movie.copy(key = "ad", durationMs = 15_000)
        repo.replaceAds("ads", listOf(AdRow.of(ad)))
        val one = async { repo.sharedAd(entry, now) }
        val two = async { repo.sharedAd(entry, now) }
        assertEquals(one.await(), two.await())
        val plan = db.settings().get(key)
        assertTrue(requireNotNull(plan).contains("ad"))
        val restarted = ScheduleRepo(db, Scheduler(ZoneId.of("UTC")))
        assertEquals(one.await(), restarted.sharedAd(entry, now))
        repo.replaceAds("ads", listOf(AdRow.of(ad.copy(key = "different"))))
        assertNull(restarted.sharedAd(entry, now))
        assertEquals(now + 15_000, restarted.sharedAdNextChange(entry, now))
        assertEquals(plan, db.settings().get(key))
        assertNull(restarted.sharedAd(entry, now + 60_000))
    }

    @Test
    fun missingSharedBookingMarkerCreatesAPersistedLocalPlanRatherThanCommonFallback() = runBlocking {
        val entry = ScheduleEntry(def.id, now, now + 60_000, EntryKind.FILLER, null)
        val key = "${Settings.SHARED_PREFIX}break:${def.id}:${entry.startMs}:${entry.endMs}"
        val ad = movie.copy(key = "ad", durationMs = 15_000)
        repo.replaceAds("ads", listOf(AdRow.of(ad)))
        assertNull(db.settings().get(key))
        val booked = repo.sharedAd(entry, now)
        assertNotNull(booked)
        val plan = db.settings().get(key)
        assertNotNull(plan)
        assertEquals(booked, ScheduleRepo(db, Scheduler(ZoneId.of("UTC"))).sharedAd(entry, now))
        assertEquals(plan, db.settings().get(key))
    }

    @Test
    fun longBridgeBooksEachClipOnceWithBoundedDurationLookupWork() {
        val duration = 150 * 60_000L
        val entry = ScheduleEntry(def.id, now, now + duration, EntryKind.FILLER, null)
        val catalog = (0 until 1_000).map { movie.copy(key = "ad:$it", durationMs = 15_000) }
        val work = SharedAdPlanWork()
        val plan = bookSharedAds(entry, catalog, kotlin.random.Random(42), work)
        assertEquals(600, plan.size)
        assertEquals(plan.size, work.bookedSlots)
        assertTrue(work.durationComparisons <= (plan.size + 1) * 10)
        assertEquals(entry.endMs, plan.last().endMs)
        assertTrue(plan.all { it.endMs <= entry.endMs })
        assertTrue(plan.zipWithNext().all { (a, b) -> a.endMs == b.startMs && a.item.key != b.item.key })
        assertEquals(plan, bookSharedAds(entry, catalog.reversed(), kotlin.random.Random(42)))
    }

    @Test
    fun expiredBookingsArePrunedAndSignoutRemovesRecipeWithoutRemovingPreferences() = runBlocking {
        db.settings().put(SettingRow("${Settings.SHARED_PREFIX}break:tbs:0:1", "[]"))
        db.settings().put(SettingRow(Settings.CRT_LEVEL, "OFF"))
        repo.setSharedPilot(recipe.encode(), now)
        repo.ensure(def.id, now)
        assertNull(db.settings().get("${Settings.SHARED_PREFIX}break:tbs:0:1"))
        db.settings().clearConnection()
        assertTrue(db.settings().withPrefix(Settings.SHARED_PREFIX).isEmpty())
        assertEquals("OFF", db.settings().get(Settings.CRT_LEVEL))
    }
}
