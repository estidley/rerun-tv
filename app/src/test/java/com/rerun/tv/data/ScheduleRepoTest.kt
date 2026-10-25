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
    fun toonukiRuleUpgradeKeepsCurrentProgramAndRebuildsFutureAndWest() = runBlocking {
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
            val westId = Lineup.westId("toonami")
            db.channels().upsert(ChannelRow.of(old.copy(id = westId, number = 605), true, false))
            db.entries().insertAll(Lineup.shift(listOf(current, future), westId, Lineup.WEST_LAG_MS).map(EntryRow::of))
            repo.refreshChannels(true, false)
            assertEquals(listOf(current), repo.entries("toonami", at, at + DAY))
            repo.ensure("toonami", at)
            repo.ensure(westId, at)
            val rebuilt = repo.entries("toonami", at, at + DAY)
            assertEquals(current, rebuilt.first())
            assertTrue(rebuilt.drop(1).filter { it.itemKey != null }.all { it.itemKey == anime.key })
            assertEquals(Lineup.shift(rebuilt, westId, Lineup.WEST_LAG_MS),
                repo.entries(westId, at + Lineup.WEST_LAG_MS, at + DAY + Lineup.WEST_LAG_MS))
            repo.refreshChannels(true, false)
            assertEquals(rebuilt, repo.entries("toonami", at, at + DAY))
        }
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
        repo.refreshChannels(eastWest = true, music = false, items = library)
        val id = "decade:tv:1980"
        assertEquals("1980s TV", repo.channel(id)?.name)
        assertNotNull(repo.channel(Lineup.westId(id)))
        repo.ensure(id, now)
        val entries = repo.entries(id, now, now + DAY)
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.filter { it.itemKey != null }.all { it.itemKey!!.startsWith("e") })
        repo.refreshChannels(eastWest = true, music = false)
        assertEquals(entries, repo.entries(id, now, now + DAY))
        repo.refreshChannels(eastWest = true, music = false, items = library.dropLast(1))
        assertNull(repo.channel(id))
        assertNull(repo.channel(Lineup.westId(id)))
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
        repo.refreshChannels(true, false, listOf(movie("show")), selectedLibraries = setOf("regular"))
        repo.ensure(Lineup.ADS_ID, now)
        assertNotNull(repo.channel(Lineup.ADS_ID))
        assertNull(repo.channel(Lineup.westId(Lineup.ADS_ID)))
        val before = repo.entries(Lineup.ADS_ID, now, now + 120_000)
        repo.refreshChannels(false, false, listOf(movie("show")))
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
    fun westWaitsThenCopiesEastAndFollowsEdits() = runBlocking {
        val original = movie("1")
        repo.refreshChannels(eastWest = true, music = false, items = listOf(original, movie("2")))
        val east = requireNotNull(repo.channel("movies:1"))
        val westId = Lineup.westId(east.id)
        repo.ensure(westId, now)
        val eastEntries = repo.entries(east.id, now - 1, now + DAY)
        val westEntries = repo.entries(westId, now - 1, now + DAY)
        assertTrue(westEntries.none { it.startMs <= now && now < it.endMs })
        assertEquals(eastEntries.first().startMs + Lineup.WEST_LAG_MS, westEntries.first().startMs)

        val future = now + Lineup.WEST_LAG_MS
        assertEquals(
            repo.entries(east.id, now, now + 1).single().itemKey,
            repo.entries(westId, future, future + 1).single().itemKey,
        )
        val edited = east.def.copy(name = "Edited", rules = east.def.rules.copy(titles = setOf("2")))
        repo.saveChannel(ChannelRow.of(edited, true, false))
        repo.refreshChannels(eastWest = true, music = false)
        repo.resetFuture(east.id, now)
        assertEquals("Edited-W", repo.channel(westId)?.name)
        val rebuilt = repo.entries(east.id, now, now + DAY)
        val shifted = repo.entries(westId, now + Lineup.WEST_LAG_MS, now + DAY + Lineup.WEST_LAG_MS)
        assertEquals(Lineup.shift(rebuilt, westId, Lineup.WEST_LAG_MS), shifted)
        assertTrue(rebuilt.drop(1).filter { it.itemKey != null }.all { it.itemKey == "2" })
    }

    @Test
    fun dormantAndHiddenEastLoseTheirTwins() = runBlocking {
        repo.refreshChannels(true, false, listOf(movie("1")))
        val east = requireNotNull(repo.channel("movies:1"))
        repo.ensure(Lineup.westId(east.id), now)
        repo.saveChannel(ChannelRow.of(east.def.copy(hidden = true), true, false))
        assertNull(repo.channel(Lineup.westId(east.id)))
        assertTrue(repo.entries(Lineup.westId(east.id), 0, Long.MAX_VALUE).isEmpty())
        repo.saveChannel(ChannelRow.of(east.def.copy(rules = east.def.rules.copy(titles = setOf("missing"))), true, true))
        repo.refreshChannels(true, false)
        assertNull(repo.channel(Lineup.westId(east.id)))
    }

    @Test
    fun libraryRefreshInvalidatesCachedItems() = runBlocking {
        repo.refreshChannels(false, false, listOf(movie("1")))
        assertNotNull(repo.item("1"))
        repo.refreshChannels(false, false, listOf(movie("2")))
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
    fun deletingEastDeletesWestScheduleAndState() = runBlocking {
        repo.refreshChannels(true, false, listOf(movie("1")))
        repo.ensure("movies:1:w", now)
        repo.deleteChannel("movies:1")
        assertNull(repo.channel("movies:1:w"))
        assertTrue(repo.entries("movies:1:w", 0, Long.MAX_VALUE).isEmpty())
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
        val entries = repo.allEntries(now, next.endMs).getValue("east")
        assertEquals(listOf(program, filler, next), entries)
        assertEquals(listOf(
            com.rerun.core.schedule.GuideBlock(now, filler.endMs, "show"),
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
