package com.rerun.core.schedule

import com.rerun.core.MIN
import com.rerun.core.catalog.Facets
import com.rerun.core.catalog.Lineup
import com.rerun.core.catalog.RelatedMovie
import com.rerun.core.catalog.RelatedShow
import com.rerun.core.catalog.has
import com.rerun.core.catalog.related
import com.rerun.core.catalog.toggle
import com.rerun.core.episode
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.model.PlayOrder
import com.rerun.core.model.Rules
import com.rerun.core.model.matchKey
import com.rerun.core.movie
import com.rerun.core.resolve.Resolver
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarathonTest {
    private val zone = ZoneId.of("America/Chicago")
    private val scheduler = Scheduler(zone)
    private val now = Instant.parse("2026-11-20T19:07:13Z").toEpochMilli()

    private val def = ChannelDef(
        "m", 400, "Marathon", ChannelKind.CUSTOM,
        Rules(titles = setOf("Ninja Show"), kinds = setOf(MediaKind.EPISODE)),
        order = PlayOrder.MARATHON, showAds = false, nonClockAligned = true,
    )

    private fun ep(key: String, season: Int, n: Int, show: String = "Ninja Show", showKey: String? = null, tmdb: String? = null) =
        episode(key, show, season = season, episode = n).let {
            it.copy(showKey = showKey ?: it.showKey, tmdbId = tmdb)
        }

    private fun series(seasons: Int = 2, perSeason: Int = 3, prefix: String = "e", showKey: String? = null) =
        (1..seasons).flatMap { s -> (1..perSeason).map { n -> ep("$prefix$s-$n", s, n, showKey = showKey) } }

    private fun keys(entries: List<ScheduleEntry>) = entries.filter { it.kind == EntryKind.PROGRAM }.map { it.itemKey!! }

    @Test
    fun ordersSeasonsThenSpecialsLastThenMovies() {
        val pool = series() + ep("sp1", 0, 1) + movie("film", title = "Ninja Show Movie")
        val order = MarathonOrder.ordered(pool).map { it.key }
        assertEquals(listOf("e1-1", "e1-2", "e1-3", "e2-1", "e2-2", "e2-3", "sp1", "film"), order)
    }

    @Test
    fun playsBackToBackInOrderAndLoops() {
        val pool = series(seasons = 1, perSeason = 3)
        val ext = scheduler.extend(def, pool, ChannelState(seed = 1), null, now, horizonMs = 3 * 22 * MIN * 3)
        val k = keys(ext.entries)
        assertEquals(listOf("e1-1", "e1-2", "e1-3", "e1-1", "e1-2", "e1-3"), k.take(6))
        // No ads, no padding: each episode starts the instant the previous one ends.
        assertTrue(ext.entries.none { it.kind == EntryKind.FILLER })
        ext.entries.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        assertEquals(now, ext.entries.first().startMs)
    }

    @Test
    fun duplicateCopiesAirOnceEach() {
        val dub = series(seasons = 1, perSeason = 3, prefix = "dub", showKey = "show:dub")
        val sub = series(seasons = 1, perSeason = 2, prefix = "sub", showKey = "show:sub")
        val order = MarathonOrder.ordered(dub + sub).map { it.key }
        assertEquals(listOf("dub1-1", "dub1-2", "dub1-3"), order)
    }

    @Test
    fun continuesAfterTheLastScheduledEpisode() {
        val pool = series(seasons = 1, perSeason = 5)
        val ext = scheduler.extend(def, pool, ChannelState(seed = 1), now, now, horizonMs = 2 * 22 * MIN, lastProgramKey = "e1-3")
        assertEquals(listOf("e1-4", "e1-5"), keys(ext.entries).take(2))
    }

    @Test
    fun insertedEpisodeDoesNotShiftTheRun() {
        val before = series(seasons = 1, perSeason = 6).filter { it.key != "e1-2" }
        val first = scheduler.extend(def, before, ChannelState(seed = 1), null, now, horizonMs = 3 * 22 * MIN)
        assertEquals(listOf("e1-1", "e1-3", "e1-4"), keys(first.entries).take(3))
        // e1-2 shows up in Plex later: the run carries on with e1-5, not a repeat of e1-4.
        val after = series(seasons = 1, perSeason = 6)
        val later = first.entries.last().endMs
        val next = scheduler.extend(def, after, first.state, later, later, horizonMs = 3 * 22 * MIN)
        assertEquals("e1-5", keys(next.entries).first())
    }

    @Test
    fun removedAnchorFallsBackToTheEpisodeBeforeIt() {
        val pool = series(seasons = 1, perSeason = 6).filter { it.key != "e1-3" }
        val state = ChannelState(seed = 1, recent = listOf("e1-1", "e1-2", "e1-3"))
        val ext = scheduler.extend(def, pool, state, now, now, horizonMs = 22 * MIN, lastProgramKey = "e1-3")
        assertEquals("e1-4", keys(ext.entries).first())
    }

    @Test
    fun resumeMarkerReplaysTheMarkedEpisode() {
        val pool = series(seasons = 1, perSeason = 6)
        val state = MarathonOrder.resumeAt(ChannelState(seed = 1), "e1-4")
        val ext = scheduler.extend(def, pool, state, null, now, horizonMs = 22 * MIN)
        assertEquals("e1-4", keys(ext.entries).first())
        val later = ext.entries.last().endMs
        val again = scheduler.extend(def, pool, ext.state, later, later, horizonMs = 22 * MIN)
        assertEquals("e1-5", keys(again.entries).first())
    }

    @Test
    fun matchKeyFoldsAccentsAndPunctuation() {
        assertEquals(matchKey("Naruto Shippuden"), matchKey("Naruto: Shippūden"))
        assertFalse(matchKey("Naruto") == matchKey("Naruto Shippuden"))
        val shippuden = episode("s1", "Naruto Shippūden")
        assertTrue(Rules(titles = setOf("Naruto Shippuden")).matches(shippuden))
        assertFalse(Rules(titles = setOf("Naruto")).matches(shippuden))
    }

    @Test
    fun stockMarathonsMatchByTmdbOrTitleAndStaySeparate() {
        val naruto = Lineup.marathonChannels().single { it.id == "marathon:naruto" }
        val shippuden = Lineup.marathonChannels().single { it.id == "marathon:shippuden" }
        val oddlyNamed = episode("n1", "NARUTO (2002)").copy(tmdbId = "46260")
        val plain = episode("n2", "Naruto")
        val part2 = episode("s1", "Naruto: Shippuden")
        val boruto = episode("b1", "Boruto: Naruto Next Generations")
        val lib = listOf(oddlyNamed, plain, part2, boruto)
        assertEquals(setOf("n1", "n2"), lib.filter { naruto.rules.matches(it) }.map { it.key }.toSet())
        assertEquals(setOf("s1"), lib.filter { shippuden.rules.matches(it) }.map { it.key }.toSet())
        for (d in listOf(naruto, shippuden)) {
            assertEquals(PlayOrder.MARATHON, d.order)
            assertFalse(d.showAds)
            assertTrue(d.nonClockAligned)
        }
    }

    @Test
    fun stockMarathonsAppearOnlyWithEnoughContent() {
        val run = (1..40).map { episode("n$it", "Naruto", episode = it, durationMs = 23 * MIN) }
        val ids = Lineup.channels(run, music = false).map { it.id }
        assertTrue("marathon:naruto" in ids)
        assertFalse("marathon:shippuden" in ids)
        assertTrue(Resolver.pool(Lineup.marathonChannels().first(), run).size == 40)
    }

    @Test
    fun relatedFindsEverythingWithTheWord() {
        val lib = listOf(
            episode("n1", "Naruto"), episode("n2", "Naruto", episode = 2),
            episode("s1", "Naruto Shippūden"), episode("b1", "Boruto: Naruto Next Generations"),
            episode("x1", "Bleach"),
            movie("m1", title = "Naruto the Movie"),
            movie("m2", title = "Other").copy(collections = setOf("Naruto Collection")),
        )
        val r = Facets.related("naruto", lib)
        assertEquals(listOf("Boruto: Naruto Next Generations", "Naruto", "Naruto Shippūden"), r.shows.map { it.title })
        assertEquals(2, r.shows.single { it.title == "Naruto" }.episodes)
        assertEquals(listOf("Naruto the Movie"), r.movies.map { it.title })
        assertEquals(listOf("Naruto Collection"), r.collections)
        assertTrue(Facets.related("shippuden", lib).shows.single().title == "Naruto Shippūden")
        assertTrue(Facets.related(" ", lib).shows.isEmpty())
    }

    @Test
    fun tickingRelatedRowsEditsTheRules() {
        val stock = Lineup.marathonChannels().first().rules
        val shown = RelatedShow("Naruto", "46260", 220, 1, 84)
        assertTrue(stock.has(shown))
        val off = stock.toggle(shown)
        assertFalse(off.has(shown))
        assertTrue(off.titles.isEmpty() && off.tmdbIds.isEmpty())
        val renamed = RelatedShow("NARUTO (2002)", null, 220, 1, 84)
        val on = off.toggle(renamed)
        assertTrue(on.matches(episode("x", "Naruto (2002)")))
        val film = RelatedMovie("Naruto the Movie", 2004)
        val withFilm = stock.toggle(film)
        assertTrue(withFilm.matches(movie("f", title = "Naruto the Movie")))
        assertFalse(withFilm.toggle(film).has(film))
    }
}
