package com.rerun.tv.data

import android.app.Application
import androidx.room.Room
import com.rerun.core.catalog.Lineup
import com.rerun.core.model.MediaItem
import com.rerun.core.plex.PlexParse
import com.rerun.core.schedule.Scheduler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import org.mockito.Mockito.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import com.rerun.core.codec.StateCodec
import com.rerun.core.schedule.ChannelState
import com.rerun.core.schedule.EntryKind
import com.rerun.core.schedule.ScheduleEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ScheduleRepoTest {
    private lateinit var db: RerunDb
    private lateinit var repo: ScheduleRepo
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), RerunDb::class.java)
            .allowMainThreadQueries().build()
        repo = ScheduleRepo(db, Scheduler(ZoneId.of("UTC")))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun toonukiRuleUpgradeKeepsCurrentProgramAndRebuildsFuture() = runBlocking {
        for (alreadyUpgraded in listOf(false, true)) {
            db.settings().clearConnection()
            val at = System.currentTimeMillis()
            val template = com.rerun.core.catalog.Catalog.builtInChannels().single { it.id == "toonami" }
            val old = template.copy(rules = com.rerun.core.model.Rules(networks = setOf("toonami"),
                titles = com.rerun.core.catalog.Curated.toonamiShows.toSet()), kidsSafe = true)
            val anime = movie("anime").copy(genres = setOf("Anime"))
            val oldShow = movie("old-show")
            db.items().replaceAll(listOf(anime, oldShow).map(ItemRow::of))
            db.channels().upsert(ChannelRow.of(if (alreadyUpgraded) template else old, true, false))
            val current = com.rerun.core.schedule.ScheduleEntry("toonami", at - 60_000, at + 60_000,
                com.rerun.core.schedule.EntryKind.PROGRAM, oldShow.key)
            val future = current.copy(startMs = current.endMs, endMs = at + DAY)
            db.entries().insertAll(listOf(current, future).map(EntryRow::of))
            repo.refreshChannels(false)
            assertEquals(listOf(current), repo.entries("toonami", at, at + DAY))
            repo.ensure("toonami", at)
            val rebuilt = repo.entries("toonami", at, at + DAY)
            assertEquals(current, rebuilt.first())
            assertTrue(rebuilt.drop(1).filter { it.itemKey != null }.all { it.itemKey == anime.key })
            repo.refreshChannels(false)
            assertEquals(rebuilt, repo.entries("toonami", at, at + DAY))
        }
    }

    @Test
    fun cableStockNamesUpdateWithoutChangingTheLockedScheduleOrPersonalRenames() = runBlocking {
        val documentary = movie("history").copy(studio = "TBS", durationMs = 4 * 3_600_000L)
        repo.refreshChannels(false, listOf(documentary))
        val tbs = requireNotNull(repo.channel("tbs"))
        db.channels().upsert(ChannelRow.of(tbs.def.copy(name = "Laugh Track"), true, false, tbs.poolMs))
        repo.ensure("tbs", now)
        val before = repo.entries("tbs", now, now + DAY)
        assertTrue(before.isNotEmpty())
        repo.refreshChannels(false)
        assertEquals("Ted's TV", repo.channel("tbs")?.name)
        assertEquals(before, repo.entries("tbs", now, now + DAY))
        repo.saveChannel(ChannelRow.of(tbs.def.copy(name = "My Superstation", minimumContentHours = 1), true))
        repo.refreshChannels(false)
        assertEquals("My Superstation", repo.channel("tbs")?.name)
        assertEquals(1, repo.channel("tbs")?.def?.minimumContentHours)
        assertEquals(before, repo.entries("tbs", now, now + DAY))
    }

    @Test
    fun minimumOverridesPersistAndCannotAccidentallyActivateAnEmptyOrSmallStockPool() = runBlocking {
        val item = movie("one").copy(durationMs = 3_600_000)
        repo.refreshChannels(false, listOf(item))
        val stock = Lineup.movieChannels()[1]
        repo.saveChannel(ChannelRow.of(stock.copy(name = "Renamed"), true, false, 99 * 3_600_000L))
        assertTrue(requireNotNull(repo.channel(stock.id)).dormant)
        val override = stock.copy(name = "Small movie night", minimumContentHours = 1)
        repo.saveChannel(ChannelRow.of(override, true))
        assertFalse(requireNotNull(repo.channel(stock.id)).dormant)
        repo.refreshChannels(false)
        assertEquals(1, repo.channel(stock.id)?.def?.minimumContentHours)
        repo.ensure(stock.id, now)
        assertTrue(repo.entries(stock.id, now, now + DAY).isNotEmpty())
        repo.saveChannel(ChannelRow.of(override.copy(minimumContentHours = null), true, false))
        assertTrue(requireNotNull(repo.channel(stock.id)).dormant)
        repo.refreshChannels(false, emptyList())
        repo.saveChannel(ChannelRow.of(override.copy(minimumContentHours = 0), true, false))
        assertTrue(requireNotNull(repo.channel(stock.id)).dormant)
    }

    @Test
    fun decadeStationsPersistScheduleAndDisappearWhenTheirPoolNoLongerQualifies() = runBlocking {
        val library = PlexParse.episodes(
            """{"MediaContainer":{"Metadata":[${
                (0 until 48).joinToString(",") {
                    """{"ratingKey":"e$it","title":"Episode $it","grandparentRatingKey":"show",
                    "grandparentTitle":"Eighties","index":${it + 1},"year":1985,"duration":1800000}"""
                }
            }]}}""", emptyMap(),
        )
        repo.refreshChannels(music = false, items = library)
        val id = "decade:tv:1980"
        assertEquals("1980s TV", repo.channel(id)?.name)
        repo.ensure(id, now)
        val entries = repo.entries(id, now, now + DAY)
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.filter { it.itemKey != null }.all { it.itemKey!!.startsWith("e") })
        repo.refreshChannels(music = false)
        assertEquals(entries, repo.entries(id, now, now + DAY))
        repo.refreshChannels(music = false, items = library.dropLast(1))
        assertNull(repo.channel(id))
        assertTrue(repo.entries(id, 0, Long.MAX_VALUE).isEmpty())
        assertNull(db.states().get(id))
    }

    @Test
    fun adsStationUsesOnlyTheAdCatalogAndSurvivesRegularLibraryRefreshes() = runBlocking {
        val clip = movie("ad").copy(durationMs = 15_000, partKey = "/library/parts/ad/file.mp4")
        repo.replaceAds("commercials", listOf(AdRow.of(clip)))
        val station = requireNotNull(repo.channel(Lineup.ADS_ID))
        assertEquals("Buy Me Maybe", station.name)
        assertFalse(station.dormant)
        assertTrue(repo.libraryItems().isEmpty())
        assertNull(repo.item("ad"))
        assertEquals(clip.key, repo.adItem("ad")?.key)
        repo.ensure(Lineup.ADS_ID, now)
        val entries = repo.entries(Lineup.ADS_ID, now, now + 120_000)
        assertTrue(entries.size >= 8)
        assertTrue(entries.all { it.kind == com.rerun.core.schedule.EntryKind.PROGRAM && it.itemKey == "ad" })
        assertTrue(entries.zipWithNext().all { (a, b) -> a.endMs == b.startMs })
        repo.refreshChannels(false, listOf(movie("show")), selectedLibraries = setOf("regular"))
        repo.ensure(Lineup.ADS_ID, now)
        assertNotNull(repo.channel(Lineup.ADS_ID))
        val before = repo.entries(Lineup.ADS_ID, now, now + 120_000)
        repo.refreshChannels(false, listOf(movie("show")))
        assertEquals(before, repo.entries(Lineup.ADS_ID, now, now + 120_000))
        repo.replaceAds("commercials", listOf(AdRow.of(clip)))
        assertEquals(before, repo.entries(Lineup.ADS_ID, now, now + 120_000))
        repo.replaceAds("commercials", listOf(AdRow.of(clip.copy(key = "new"))))
        assertTrue(repo.entries(Lineup.ADS_ID, now, now + 120_000).isEmpty())
        repo.ensure(Lineup.ADS_ID, now)
        assertTrue(repo.entries(Lineup.ADS_ID, now, now + 120_000).all { it.itemKey == "new" })
        repo.replaceAds(null, emptyList())
        assertNull(repo.channel(Lineup.ADS_ID))
        assertTrue(repo.entries(Lineup.ADS_ID, 0, Long.MAX_VALUE).isEmpty())
        assertNull(db.states().get(Lineup.ADS_ID))
        repo.replaceAds("commercials", emptyList())
        assertNull(repo.channel(Lineup.ADS_ID))
    }

    @Test
    fun editingKeepsTheCurrentProgramAndRebuildsTheFuture() = runBlocking {
        val original = movie("1")
        repo.refreshChannels(music = false, items = listOf(original, movie("2")))
        val channel = requireNotNull(repo.channel("movies:1"))
        repo.ensure(channel.id, now)
        val current = repo.entries(channel.id, now, now + 1).single()
        val edited = channel.def.copy(name = "Edited", rules = channel.def.rules.copy(titles = setOf("2")))
        repo.saveChannel(ChannelRow.of(edited, true, false))
        repo.resetFuture(channel.id, now)
        val rebuilt = repo.entries(channel.id, now, now + DAY)
        assertEquals(current, rebuilt.first())
        assertEquals("Edited", repo.channel(channel.id)?.name)
        assertTrue(rebuilt.drop(1).filter { it.itemKey != null }.all { it.itemKey == "2" })
    }

    @Test
    fun resyncKeepsAiringAndStillValidEntriesAndTrimsOnlyRemovedItems() = runBlocking {
        repo.refreshChannels(false, listOf(movie("1"), movie("2"), movie("3")))
        val id = "movies:1"
        val t = System.currentTimeMillis()
        val hour = 3_600_000L
        val rows = listOf("1" to -hour / 2, "2" to hour / 2, "3" to 3 * hour / 2, "1" to 5 * hour / 2)
        db.entries().insertAll(rows.map { (key, offset) ->
            EntryRow.of(ScheduleEntry(id, t + offset, t + offset + hour, EntryKind.PROGRAM, key))
        })
        db.states().put(StateRow(id, StateCodec.encode(ChannelState(seed = 42))))
        repo.refreshChannels(false, listOf(movie("1"), movie("2"), movie("3")))
        assertEquals(4, db.entries().range(id, 0, Long.MAX_VALUE).size)

        repo.refreshChannels(false, listOf(movie("1"), movie("3")))
        val kept = db.entries().range(id, 0, Long.MAX_VALUE).filter { it.startMs < t + 3 * hour / 2 }
        assertEquals(listOf("1"), kept.map { it.itemKey })
        assertEquals(42L, StateCodec.decode(db.states().get(id)!!.json).seed)
    }

    @Test
    fun newChannelStatesGetRandomSeedsSoResyncsDoNotReplayTheSameOrder() = runBlocking {
        repo.refreshChannels(false, listOf(movie("1")))
        repo.ensure("movies:1", now)
        val seed = StateCodec.decode(db.states().get("movies:1")!!.json).seed
        assertNotEquals("movies:1".hashCode().toLong(), seed)
    }

    @Test
    fun dormantAndHiddenChannelsDoNotGenerateSchedules() = runBlocking {
        repo.refreshChannels(false, listOf(movie("1")))
        val channel = requireNotNull(repo.channel("movies:1"))
        repo.saveChannel(ChannelRow.of(channel.def.copy(hidden = true), true, false))
        repo.ensure(channel.id, now)
        assertTrue(repo.entries(channel.id, 0, Long.MAX_VALUE).isEmpty())
        repo.saveChannel(ChannelRow.of(channel.def.copy(rules = channel.def.rules.copy(titles = setOf("missing"))), true, true))
        repo.refreshChannels(false)
        repo.ensure(channel.id, now)
        assertTrue(repo.entries(channel.id, 0, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun libraryRefreshInvalidatesCachedItems() = runBlocking {
        repo.refreshChannels(false, listOf(movie("1")))
        assertNotNull(repo.item("1"))
        repo.refreshChannels(false, listOf(movie("2")))
        assertNull(repo.item("1"))
        assertNotNull(repo.item("2"))
    }

    @Test
    fun cachedLibrarySharesOneListAndInvalidationReloadsIt() = runBlocking {
        db.items().replaceAll(listOf(ItemRow.of(movie("1"))))
        val first = repo.libraryItems()
        assertSame(first, repo.libraryItems())
        assertSame(first.single(), repo.item("1"))
        db.items().replaceAll(listOf(ItemRow.of(movie("2"))))
        repo.invalidateLibrary()
        val second = repo.libraryItems()
        assertNotSame(first, second)
        assertEquals(listOf("2"), second.map { it.key })
        assertNull(repo.item("1"))
    }

    @Test
    fun concurrentColdReadersShareOneDatabaseReadOffMainThread() = runBlocking<Unit> {
        val database = mock(RerunDb::class.java)
        val dao = mock(ItemDao::class.java)
        `when`(database.items()).thenReturn(dao)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            assertNotEquals(android.os.Looper.getMainLooper().thread, Thread.currentThread())
            started.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            listOf(ItemRow.of(movie("1")))
        }.`when`(dao).all()
        val cached = ScheduleRepo(database, Scheduler(ZoneId.of("UTC")))
        val first = async(Dispatchers.Default) { cached.libraryItems() }
        val second = async(Dispatchers.Default) { cached.libraryItems() }
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS))
        } finally {
            release.countDown()
        }
        assertSame(first.await(), second.await())
        verify(dao, times(1)).all()
    }

    @Test
    fun invalidationDuringColdLoadCannotPublishStaleLibrary() = runBlocking {
        val database = mock(RerunDb::class.java)
        val dao = mock(ItemDao::class.java)
        `when`(database.items()).thenReturn(dao)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reads = AtomicInteger()
        doAnswer {
            if (reads.incrementAndGet() == 1) {
                started.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                listOf(ItemRow.of(movie("old")))
            } else listOf(ItemRow.of(movie("new")))
        }.`when`(dao).all()
        val cached = ScheduleRepo(database, Scheduler(ZoneId.of("UTC")))
        val pending = async(Dispatchers.Default) { cached.libraryItems() }
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS))
            cached.invalidateLibrary()
        } finally {
            release.countDown()
        }
        assertEquals(listOf("new"), pending.await().map { it.key })
        assertNull(cached.item("old"))
        assertEquals(2, reads.get())
    }

    @Test
    fun failedLibraryReadPropagatesAndCanBeRetried() = runBlocking {
        val database = mock(RerunDb::class.java)
        val dao = mock(ItemDao::class.java)
        `when`(database.items()).thenReturn(dao)
        `when`(dao.all()).thenThrow(IllegalStateException("Storage unavailable"))
            .thenReturn(listOf(ItemRow.of(movie("1"))))
        val cached = ScheduleRepo(database, Scheduler(ZoneId.of("UTC")))
        try {
            cached.libraryItems()
            fail("Storage failures must not create an empty snapshot")
        } catch (e: IllegalStateException) {
            assertEquals("Storage unavailable", e.message)
        }
        assertEquals(listOf("1"), cached.libraryItems().map { it.key })
    }

    @Test
    fun deletingAChannelDeletesItsScheduleAndState() = runBlocking {
        repo.refreshChannels(false, listOf(movie("1")))
        repo.ensure("movies:1", now)
        assertNotNull(db.states().get("movies:1"))
        repo.deleteChannel("movies:1")
        assertNull(repo.channel("movies:1"))
        assertTrue(repo.entries("movies:1", 0, Long.MAX_VALUE).isEmpty())
        assertNull(db.states().get("movies:1"))
    }

    @Test
    fun restartQueueUsesShowIdentityAndEpisodeOrderAndSurvivesPausingAndRepositoryRestart() = runBlocking {
        fun episode(key: String, season: Int?, number: Int?, show: String = "show") =
            movie(key).copy(kind = com.rerun.core.model.MediaKind.EPISODE, showKey = show,
                season = season, episode = number, durationMs = 1_800_000)
        val current = episode("current", 1, 2)
        val next = episode("next", 1, 4)
        val seasonTwo = episode("s2", 2, 1)
        val library = listOf(seasonTwo, episode("earlier", 1, 1), episode("other", 1, 3, "other"),
            next, current, episode("unknown", null, 5), episode("special", 0, 1))
        repo.refreshChannels(false, library)
        repo.airFromStart(current, "source", queueEpisodes = true)
        val session = requireNotNull(repo.restartSession())
        assertEquals(listOf("current", "next", "s2"), session.keys)
        assertFalse(requireNotNull(repo.channel(Lineup.RESTART_ID)).hidden)
        repo.saveRestartProgress(session.id, 0, 123_456, paused = true)
        repo.saveRestartProgress(session.id, 0, 60_000, paused = true)

        val reopened = ScheduleRepo(db, Scheduler(ZoneId.of("UTC")))
        val restored = requireNotNull(reopened.restartSession())
        assertEquals(60_000L, restored.positionMs)
        assertEquals("source", restored.returnChannel)
        assertTrue(reopened.active().any { it.id == Lineup.RESTART_ID })
        val tomorrow = now + DAY
        val projected = reopened.restartEntries(tomorrow)
        assertEquals(tomorrow - 60_000, projected.first().startMs)
        assertEquals(session.keys, projected.map { it.itemKey })
        reopened.refreshChannels(false, library)
        assertEquals(restored, reopened.restartSession())
        assertTrue(reopened.active().any { it.id == Lineup.RESTART_ID })

        val advanced = requireNotNull(reopened.advanceRestart(session.id, 0))
        assertEquals(1, advanced.index)
        assertEquals(0L, advanced.positionMs)
        reopened.saveRestartProgress(session.id, 0, 900_000, paused = false)
        assertEquals(advanced, reopened.restartSession())
        reopened.advanceRestart(session.id, 1)
        assertNull(reopened.advanceRestart(session.id, 2))
        assertNull(reopened.channel(Lineup.RESTART_ID))
    }

    @Test
    fun singleProgramRestartRemovalAndSignOutNeverRestoreAnOldCheckpoint() = runBlocking {
        val item = movie("one")
        repo.refreshChannels(false, listOf(item))
        repo.airFromStart(item, "source", queueEpisodes = false)
        val old = requireNotNull(repo.restartSession())
        assertEquals(listOf(item.key), old.keys)
        repo.removeRestartStation()
        repo.saveRestartProgress(old.id, old.index, 5000, paused = true)
        assertNull(repo.restartSession())
        assertNull(repo.channel(Lineup.RESTART_ID))
        repo.airFromStart(item, "source", queueEpisodes = false)
        repo.saveRestartProgress(old.id, old.index, 9000, paused = false)
        assertEquals(0L, requireNotNull(repo.restartSession()).positionMs)
        db.settings().clearConnection()
        assertNull(repo.restartSession())
    }

    @Test
    fun restartStationAvoidsNumberCollisionsAndInvalidatesWhenQueuedContentIsRemoved() = runBlocking {
        val item = movie("one")
        repo.refreshChannels(false, listOf(item))
        db.channels().upsert(ChannelRow.of(com.rerun.core.model.ChannelDef(
            "custom", Lineup.RESTART_NUMBER, "Custom", com.rerun.core.model.ChannelKind.CUSTOM,
            com.rerun.core.model.Rules()), false, false))
        repo.airFromStart(item, "source", queueEpisodes = false)
        assertNotEquals(Lineup.RESTART_NUMBER, repo.channel(Lineup.RESTART_ID)?.number)
        repo.refreshChannels(false, emptyList())
        assertNull(repo.restartSession())
        assertNull(repo.channel(Lineup.RESTART_ID))
    }

    @Test
    fun guideQueryIncludesTheFollowingBreakBeyondTheRightCacheBoundary() = runBlocking {
        val program = ScheduleEntry("channel", now + 60 * 60_000, now + 82 * 60_000, EntryKind.PROGRAM, "show")
        val filler = ScheduleEntry("channel", program.endMs, now + 90 * 60_000, EntryKind.FILLER, null)
        val next = ScheduleEntry("channel", filler.endMs, now + 112 * 60_000, EntryKind.PROGRAM, "later")
        db.entries().insertAll(listOf(program, filler, next).map(EntryRow::of))
        val entries = repo.entriesFor(listOf("channel"), now, now + 70 * 60_000).getValue("channel")
        assertEquals(listOf(program, filler), entries)
        assertEquals(listOf(
            com.rerun.core.schedule.GuideBlock(now, program.startMs, null, offAir = true),
            com.rerun.core.schedule.GuideBlock(program.startMs, filler.endMs, "show"),
        ), com.rerun.core.schedule.guideBlocks(entries, now, now + 70 * 60_000))
    }

    @Test
    fun guideStartingInABreakIncludesThePreviousProgramButNotAdTitles() = runBlocking {
        val program = com.rerun.core.schedule.ScheduleEntry(
            "east", now - 1_800_000, now - 480_000, com.rerun.core.schedule.EntryKind.PROGRAM, "show",
        )
        val filler = com.rerun.core.schedule.ScheduleEntry(
            "east", program.endMs, now + 120_000, com.rerun.core.schedule.EntryKind.FILLER, null,
        )
        val next = com.rerun.core.schedule.ScheduleEntry(
            "east", filler.endMs, now + 1_920_000, com.rerun.core.schedule.EntryKind.PROGRAM, "next",
        )
        db.entries().insertAll(listOf(program, filler, next).map(EntryRow::of))
        val entries = repo.entriesFor(listOf("east"), now, next.endMs).getValue("east")
        assertEquals(listOf(program, filler, next), entries)
        assertEquals(listOf(
            com.rerun.core.schedule.GuideBlock(program.startMs, filler.endMs, "show"),
            com.rerun.core.schedule.GuideBlock(next.startMs, next.endMs, "next"),
        ), com.rerun.core.schedule.guideBlocks(entries, now, next.endMs))
    }

    private fun movie(key: String): MediaItem = PlexParse.movies(
        """{"MediaContainer":{"Metadata":[{"ratingKey":"$key","title":"$key","duration":14400000}]}}""",
    ).single()

    private companion object {
        const val DAY = 24 * 3_600_000L
    }
}
