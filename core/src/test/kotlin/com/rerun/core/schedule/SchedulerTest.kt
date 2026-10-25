package com.rerun.core.schedule

import com.rerun.core.HOUR
import com.rerun.core.MIN
import com.rerun.core.catalog.Catalog
import com.rerun.core.episode
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.model.Rules
import com.rerun.core.model.TimeBlock
import com.rerun.core.movie
import com.rerun.core.track
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SchedulerTest {
    private val zone = ZoneId.of("America/Chicago")
    private val scheduler = Scheduler(zone)
    private val now = Instant.parse("2026-10-23T21:47:13Z").toEpochMilli()
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private val horrorDef = ChannelDef("horror", 301, "Horror", ChannelKind.THEME, Rules(genres = setOf("Horror")))
    private val anyDef = ChannelDef("any", 400, "Any", ChannelKind.CUSTOM, Rules())

    private fun movies(n: Int) = (1..n).map { movie("m$it", genres = setOf("Horror"), durationMs = (85 + it) * MIN) }
    private fun programs(entries: List<ScheduleEntry>) = entries.filter { it.kind == EntryKind.PROGRAM }

    @Test
    fun fillsFortyEightHoursFromGridFloor() {
        val ext = scheduler.extend(horrorDef, movies(10), ChannelState(seed = 42), null, now)
        assertEquals(gridFloor(now), ext.entries.first().startMs)
        assertTrue(ext.entries.last().endMs >= now + 48 * HOUR)
    }

    @Test
    fun entriesAreContiguousAndProgramsStartOnGrid() {
        val ext = scheduler.extend(horrorDef, movies(10), ChannelState(seed = 42), null, now)
        ext.entries.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        programs(ext.entries).forEach { assertEquals(0L, it.startMs % GRID_MS, "program at ${it.startMs}") }
        ext.entries.filter { it.kind == EntryKind.FILLER }.forEach { assertTrue(it.endMs > it.startMs) }
    }

    @Test
    fun deckHasNoRepeatsWithinOneDeck() {
        val ext = scheduler.extend(horrorDef, movies(10), ChannelState(seed = 7), null, now)
        val first10 = programs(ext.entries).take(10).map { it.itemKey }
        assertEquals(10, first10.toSet().size)
    }

    @Test
    fun noImmediateRepeatAcrossDeckBoundary() {
        for (seed in 1L..30L) {
            val keys = programs(scheduler.extend(horrorDef, movies(5), ChannelState(seed = seed), null, now).entries).map { it.itemKey }
            keys.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b, "seed $seed") }
            // every title still plays once per deck
            assertEquals(5, keys.take(10).drop(5).toSet().size, "seed $seed second deck")
        }
    }

    @Test
    fun episodesPlayInOrderPerShow() {
        val a = listOf(episode("a1", "Alpha", 1, 1), episode("a2", "Alpha", 1, 2), episode("a3", "Alpha", 2, 1))
        val b = listOf(episode("b1", "Beta", 1, 1), episode("b2", "Beta", 1, 2))
        val pool = (a + b).shuffled(java.util.Random(3))
        val keys = programs(scheduler.extend(anyDef, pool, ChannelState(seed = 11), null, now).entries).map { it.itemKey }
        val alphaOrder = keys.filter { it!!.startsWith("a") }.take(6)
        assertEquals(listOf("a1", "a2", "a3", "a1", "a2", "a3"), alphaOrder)
        val betaOrder = keys.filter { it!!.startsWith("b") }.take(4)
        assertEquals(listOf("b1", "b2", "b1", "b2"), betaOrder)
    }

    @Test
    fun mixedChannelInsertsMovieAboutEverySixHours() {
        val eps = (1..40).map { episode("e$it", "Show${it % 4}", 1, it) }
        val mv = (1..10).map { movie("m$it", durationMs = 95 * MIN) }
        val ext = scheduler.extend(anyDef, eps + mv, ChannelState(seed = 5), null, now)
        val window = programs(ext.entries).filter { it.startMs < now + 48 * HOUR }
        val movieCount = window.count { it.itemKey!!.startsWith("m") }
        assertTrue(movieCount in 7..9, "movies in 48h: $movieCount")
    }

    @Test
    fun blockNarrowsPoolDuringWindow() {
        val cn = Catalog.builtInChannels().first { it.id == "cn" }
        val dbz = (1..30).map { episode("dbz$it", "Dragon Ball Z", 1, it, network = "Fuji TV") }
        val cnShows = (1..30).map { episode("cn$it", "Dexter Toon", 1, it, network = "Cartoon Network") }
        val monday = at("2026-10-26T15:00:00Z")
        val ext = scheduler.extend(cn, dbz + cnShows, ChannelState(seed = 9), null, monday, horizonMs = 12 * HOUR)
        val blockStart = at("2026-10-26T21:00:00Z") // 16:00 CDT
        val blockEnd = at("2026-10-27T00:00:00Z")   // 19:00 CDT
        for (p in programs(ext.entries)) {
            val inBlock = p.startMs in blockStart until blockEnd
            assertEquals(inBlock, p.itemKey!!.startsWith("dbz"), "program at ${Instant.ofEpochMilli(p.startMs)}")
        }
    }

    @Test
    fun dstFallBackBlockUsesWallClock() {
        val def = ChannelDef(
            "late", 401, "Late", ChannelKind.CUSTOM, Rules(genres = setOf("Comedy")),
            blocks = listOf(TimeBlock(setOf(DayOfWeek.SATURDAY), LocalTime.of(21, 0), LocalTime.of(3, 0), Rules(genres = setOf("Horror")))),
        )
        val comedy = (1..20).map { episode("c$it", "Sitcom", 1, it, durationMs = 30 * MIN, genres = setOf("Comedy")) }
        val horror = (1..20).map { episode("h$it", "Creepy", 1, it, durationMs = 30 * MIN, genres = setOf("Horror")) }
        val start = at("2026-10-31T23:00:00Z") // Sat 18:00 CDT
        val entries = scheduler.extend(def, comedy + horror, ChannelState(seed = 1), null, start, horizonMs = 14 * HOUR).entries
        val blockStart = at("2026-11-01T02:00:00Z") // Sat 21:00 CDT
        val blockEnd = at("2026-11-01T09:00:00Z")   // Sun 03:00 CST (after fall-back)
        val cdt0130 = at("2026-11-01T06:30:00Z")
        val cst0130 = at("2026-11-01T07:30:00Z")
        for (p in programs(entries)) {
            val inBlock = p.startMs in blockStart until blockEnd
            assertEquals(inBlock, p.itemKey!!.startsWith("h"), "program at ${Instant.ofEpochMilli(p.startMs)}")
        }
        assertTrue(programs(entries).any { it.startMs == cdt0130 && it.itemKey!!.startsWith("h") })
        assertTrue(programs(entries).any { it.startMs == cst0130 && it.itemKey!!.startsWith("h") })
    }

    @Test
    fun extendContinuesFromLastEnd() {
        val first = scheduler.extend(horrorDef, movies(6), ChannelState(seed = 3), null, now)
        val lastEnd = first.entries.last().endMs
        val second = scheduler.extend(horrorDef, movies(6), first.state, lastEnd, now + 30 * HOUR)
        assertEquals(lastEnd, second.entries.first().startMs)
        assertTrue(second.entries.all { it.startMs >= lastEnd })
        assertTrue(second.entries.last().endMs >= now + 30 * HOUR + 48 * HOUR)
    }

    @Test
    fun wakingAfterDaysRestartsAtNow() {
        val first = scheduler.extend(horrorDef, movies(6), ChannelState(seed = 3), null, now)
        val later = now + 5 * 24 * HOUR
        val second = scheduler.extend(horrorDef, movies(6), first.state, first.entries.last().endMs, later)
        assertEquals(gridFloor(later), second.entries.first().startMs)
    }

    @Test
    fun sameStateSameOutput() {
        val pool = movies(8) + (1..10).map { episode("e$it", "Show", 1, it, genres = setOf("Horror")) }
        val a = scheduler.extend(horrorDef, pool, ChannelState(seed = 77), null, now)
        val b = scheduler.extend(horrorDef, pool.reversed(), ChannelState(seed = 77), null, now)
        assertEquals(a, b)
    }

    @Test
    fun emptyPoolReturnsNothing() {
        val ext = scheduler.extend(horrorDef, emptyList<MediaItem>(), ChannelState(seed = 1), null, now)
        assertTrue(ext.entries.isEmpty())
    }

    @Test
    fun inOrderPlaysMoviesByYear() {
        val def = anyDef.copy(order = com.rerun.core.model.PlayOrder.IN_ORDER)
        val pool = listOf(movie("c", year = 2001), movie("a", year = 1985), movie("b", year = 1992))
        val keys = programs(scheduler.extend(def, pool, ChannelState(seed = 1), null, now).entries).map { it.itemKey }.take(6)
        assertEquals(listOf("a", "b", "c", "a", "b", "c"), keys)
    }

    @Test
    fun musicIsBackToBack() {
        val def = ChannelDef(
            "music:jazz", 2000, "Jazz", ChannelKind.MUSIC,
            Rules(genres = setOf("Jazz"), kinds = setOf(MediaKind.TRACK)),
        )
        val tracks = (1..4).map { track("t$it", genre = "Jazz", album = "Night", index = it, durationMs = 3 * MIN) }
        val ext = scheduler.extend(def, tracks, ChannelState(seed = 2), null, now, horizonMs = 20 * MIN)
        assertTrue(ext.entries.isNotEmpty())
        assertTrue(ext.entries.none { it.kind == EntryKind.FILLER })
        ext.entries.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        assertTrue(ext.entries.all { it.endMs - it.startMs == 3 * MIN })
    }

    @Test
    fun kindsArePreservedInPool() {
        val ext = scheduler.extend(anyDef, listOf(movie("m1")), ChannelState(seed = 1), null, now)
        assertTrue(programs(ext.entries).all { it.itemKey == "m1" })
        assertEquals(MediaKind.MOVIE, movie("m1").kind)
    }
}
