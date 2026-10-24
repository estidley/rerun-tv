package com.rerun.tv.data

import android.app.Application
import androidx.room.Room
import com.rerun.core.catalog.Lineup
import com.rerun.core.model.MediaItem
import com.rerun.core.plex.PlexParse
import com.rerun.core.schedule.Scheduler
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
