package com.rerun.core.catalog

import com.rerun.core.HOUR
import com.rerun.core.MIN
import com.rerun.core.episode
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaKind
import com.rerun.core.model.Rules
import com.rerun.core.movie
import com.rerun.core.schedule.ChannelState
import com.rerun.core.schedule.EntryKind
import com.rerun.core.schedule.ScheduleEntry
import com.rerun.core.schedule.Scheduler
import com.rerun.core.track
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LineupTest {
    private val banned = listOf(
        "disney", "nickelodeon", "hbo", "cnn", "cartoon network", "boomerang", "toonami",
        "syfy", "pbs", "mtv", "hgtv", "paramount", "comedy central", "adult swim",
        "freeform", "abc family", "food network", "cinemax", "touchstone", "nick jr",
        "movies-1", "movies-2",
    )

    @Test
    fun adsStationNeedsOnlyOneClipAndDoesNotGetAWestFeedOrUseAReservedNumber() {
        val custom = ChannelDef("custom:one", 998, "Custom", ChannelKind.CUSTOM, Rules())
        val existing = listOf(StoredChannel(custom, false))
        val enabled = Lineup.plan(emptyList(), existing, eastWest = true, music = false, adsPoolMs = 15_000)
        val ads = enabled.channels.single { it.def.id == Lineup.ADS_ID }
        assertEquals("Buy Me Maybe", ads.def.name)
        assertFalse(ads.dormant)
        assertEquals(15_000, ads.poolMs)
        assertNotEquals(998, ads.def.number)
        assertNotEquals(Lineup.RESTART_NUMBER, ads.def.number)
        assertTrue(enabled.channels.none { it.def.id == Lineup.westId(Lineup.ADS_ID) })
        val disabled = Lineup.plan(emptyList(), listOf(StoredChannel(ads.def, true)), false, false)
        assertTrue(Lineup.ADS_ID in disabled.deleteIds)
    }

    @Test
    fun adsScheduleIncludesShortClipsBackToBackWithoutFiller() {
        val clips = listOf(movie("a", durationMs = 15_000), movie("b", durationMs = 30_000))
        val entries = Scheduler(ZoneId.of("UTC")).extend(
            Lineup.adsChannel, clips, ChannelState(seed = 1), null, 0, horizonMs = 120_000, backToBack = true,
        ).entries
        assertTrue(entries.all { it.kind == EntryKind.PROGRAM })
        assertTrue(entries.zipWithNext().all { (a, b) -> a.endMs == b.startMs && a.itemKey != b.itemKey })
        assertTrue(entries.all { it.endMs - it.startMs in setOf(15_000L, 30_000L) })
        val tail = com.rerun.core.schedule.locate(entries, entries.first().endMs - 1, skipTail = false)
        assertEquals(entries.first(), tail?.entry)
    }

    @Test
    fun onScreenNamesAreParodies() {
        val defs = Catalog.builtInChannels() + Lineup.optionalThemes() + Lineup.movieChannels()
        for (def in defs) {
            val name = def.name.lowercase()
            for (brand in banned) assertFalse(brand in name, "${def.id} uses $brand")
        }
        assertEquals("Wonderland", defs.first { it.id == "disney" }.name)
        assertEquals("Laughing Stock", defs.first { it.id == "comedy" }.name)
        assertEquals("True-ish", defs.first { it.id == "opt:docs" }.name)
        assertEquals("Canned Laughter", defs.first { it.id == "opt:sitcom" }.name)
        assertEquals("Sob Story", defs.first { it.id == "opt:dramedy" }.name)
        assertEquals("Cold Read", defs.first { it.id == "opt:psychic" }.name)
        assertEquals("This Just In", defs.first { it.id == "opt:news" }.name)
        assertEquals(listOf("The Big Picture", "Second Feature", "Late Seating"), Lineup.movieChannels().map { it.name })
    }

    @Test
    fun chooseNameKeepsARenameAndReplacesStock() {
        assertEquals("Wonderland", Catalog.chooseName("disney", "Disney Channel", "Wonderland"))
        assertEquals("Wonderland", Catalog.chooseName("disney", "Wonderland E", "Wonderland"))
        assertEquals("Wonderland", Catalog.chooseName("disney", "Wonderland-W", "Wonderland"))
        assertEquals("Castle Club", Catalog.chooseName("disney", "Castle Club", "Wonderland"))
        assertEquals("Castle Club", Catalog.chooseName("disney", "Castle Club W", "Wonderland"))
        assertEquals("Laughing Stock", Catalog.chooseName("comedy", "The Chuckle", "Laughing Stock"))
        assertEquals("True-ish", Catalog.chooseName("opt:docs", "The Reel", "True-ish"))
        assertEquals("Fright Night", Catalog.chooseName("horror", "Horror", "Fright Night"))
    }

    @Test
    fun extrasAppearOnlyWithEnoughContent() {
        assertTrue(Lineup.channels(emptyList(), music = false).none { it.id.startsWith("opt:") || it.id.startsWith("movies:") })
        val short = listOf(movie("d", genres = setOf("Drama"), durationMs = 3 * HOUR))
        assertTrue(Lineup.channels(short, music = false).none { it.id == "opt:drama" })
        val long = listOf(movie("d", genres = setOf("Drama"), durationMs = 4 * HOUR))
        val ids = Lineup.channels(long, music = false).map { it.id }
        assertTrue("opt:drama" in ids)
        assertFalse("opt:crime" in ids)
    }

    @Test
    fun movieOverflowFollowsThePool() {
        fun ids(ms: Long) = Lineup.channels(listOf(movie("m", durationMs = ms)), music = false)
            .filter { it.id.startsWith("movies:") }.map { it.id }
        assertEquals(emptyList(), ids(3 * HOUR))
        assertEquals(listOf("movies:1"), ids(4 * HOUR))
        assertEquals(listOf("movies:1", "movies:2"), ids(24 * HOUR))
        assertEquals(listOf("movies:1", "movies:2", "movies:3"), ids(48 * HOUR))
    }

    @Test
    fun movieChannelsShuffleApart() {
        val pool = (1..12).map { movie("m$it", durationMs = 90 * MIN) }
        val now = Instant.parse("2026-10-23T21:47:13Z").toEpochMilli()
        val scheduler = Scheduler(ZoneId.of("UTC"))
        val first = Lineup.movieChannels()[0]
        val second = Lineup.movieChannels()[1]
        val a = scheduler.extend(first, pool, ChannelState(first.id.hashCode().toLong()), null, now).entries.map { it.itemKey }
        val b = scheduler.extend(second, pool, ChannelState(second.id.hashCode().toLong()), null, now).entries.map { it.itemKey }
        assertNotEquals(a, b)
    }

    @Test
    fun musicStationsFollowGenresAndTheSwitch() {
        val songs = List(8) { track("j$it", genre = "Jazz", durationMs = 30 * MIN, index = it + 1) } +
            listOf(track("both", genre = "Jazz", durationMs = 0)) +
            listOf(track("bare", genre = "", durationMs = 4 * HOUR))
        assertTrue(Lineup.channels(songs, music = false).none { it.id.startsWith("music:") })
        val on = Lineup.channels(songs, music = true).filter { it.id.startsWith("music:") }
        assertEquals(listOf("music:jazz"), on.map { it.id })
        assertEquals("Jazz", on.single().name)
        assertEquals(ChannelKind.MUSIC, on.single().kind)
        assertEquals(setOf(MediaKind.TRACK), on.single().rules.kinds)
    }

    @Test
    fun musicalMoviesDoNotBecomeMusicStations() {
        val songs = List(8) { track("t$it", genre = "Music", durationMs = 30 * MIN) }
        val off = Lineup.channels(songs, music = false).map { it.id }
        assertFalse("opt:musical" in off)
        assertTrue("music:music" in Lineup.channels(songs, music = true).map { it.id })
    }

    @Test
    fun westIsTheSameScheduleThreeHoursLater() {
        val entries = listOf(
            ScheduleEntry("disney", 1_000, 5_000, EntryKind.PROGRAM, "ep"),
            ScheduleEntry("disney", 5_000, 9_000, EntryKind.FILLER, null),
        )
        val shifted = Lineup.shift(entries, "disney:w", Lineup.WEST_LAG_MS)
        assertEquals(1_000 + Lineup.WEST_LAG_MS, shifted[0].startMs)
        assertEquals(5_000 + Lineup.WEST_LAG_MS, shifted[0].endMs)
        assertEquals("ep", shifted[0].itemKey)
        assertEquals("disney:w", shifted[1].channelId)
        assertEquals(EntryKind.FILLER, shifted[1].kind)
        assertEquals(3 * HOUR, Lineup.WEST_LAG_MS)
    }

    @Test
    fun newWestFeedLeavesTheFirstThreeHoursEmpty() {
        val now = 10_000_000L
        val east = listOf(ScheduleEntry("disney", now, now + 30 * MIN, EntryKind.PROGRAM, "ep"))
        val shifted = Lineup.shift(east, "disney:w", Lineup.WEST_LAG_MS)
        assertTrue(shifted.none { it.startMs <= now && now < it.endMs })
        assertEquals(now + Lineup.WEST_LAG_MS, shifted.first().startMs)
    }

    @Test
    fun eastWestNamesAndNumbers() {
        val shows = List(8) { episode("e$it", "Duck", episode = it + 1, durationMs = 30 * MIN, network = "Disney Channel") }
        val stored = StoredChannel(Catalog.builtInChannels().first { it.id == "disney" }.copy(name = "Disney Channel"), builtIn = true)
        val on = Lineup.plan(shows, listOf(stored), eastWest = true, music = false)
        val east = on.channels.first { it.def.id == "disney" }
        val west = on.channels.first { it.def.id == "disney:w" }
        assertEquals("Wonderland", east.def.name)
        assertEquals("Wonderland-W", west.def.name)
        assertEquals(101 + Lineup.WEST_NUMBER_OFFSET, west.def.number)
        assertEquals(east.def.rules, west.def.rules)
        assertFalse(east.dormant)
        assertFalse(west.dormant)

        val off = Lineup.plan(
            shows,
            listOf(StoredChannel(east.def, true), StoredChannel(west.def, true)),
            eastWest = false,
            music = false,
        )
        assertEquals("Wonderland", off.channels.first { it.def.id == "disney" }.def.name)
        assertTrue(off.channels.none { it.def.id == "disney:w" })
        assertTrue("disney:w" in off.deleteIds)
    }

    @Test
    fun renamedChannelKeepsItsNameOnBothFeeds() {
        val shows = List(8) { episode("e$it", "Duck", episode = it + 1, durationMs = 30 * MIN, network = "Disney Channel") }
        val stored = StoredChannel(Catalog.builtInChannels().first { it.id == "disney" }.copy(name = "Castle Club"), true)
        val plan = Lineup.plan(shows, listOf(stored), eastWest = true, music = false)
        assertEquals("Castle Club", plan.channels.first { it.def.id == "disney" }.def.name)
        assertEquals("Castle Club-W", plan.channels.first { it.def.id == "disney:w" }.def.name)
    }

    @Test
    fun dormantChannelsSkipTheWestFeed() {
        val stored = StoredChannel(Catalog.builtInChannels().first { it.id == "disney" }.copy(name = "Disney Channel E"), true)
        val plan = Lineup.plan(emptyList(), listOf(stored), eastWest = true, music = false)
        val east = plan.channels.first { it.def.id == "disney" }
        assertEquals("Wonderland", east.def.name)
        assertTrue(east.dormant)
        assertTrue(plan.channels.none { Lineup.isWest(it.def.id) })
    }

    @Test
    fun stockExtraIsDeletedAndAnEditIsKept() {
        val drama = Lineup.optionalThemes().first { it.id == "opt:drama" }
        val stock = Lineup.plan(emptyList(), listOf(StoredChannel(drama, true)), eastWest = false, music = false)
        assertTrue("opt:drama" in stock.deleteIds)
        assertTrue(stock.channels.none { it.def.id == "opt:drama" })

        val edited = Lineup.plan(emptyList(), listOf(StoredChannel(drama.copy(name = "My Dramas"), true)), eastWest = false, music = false)
        val kept = edited.channels.first { it.def.id == "opt:drama" }
        assertEquals("My Dramas", kept.def.name)
        assertTrue(kept.dormant)
        assertFalse("opt:drama" in edited.deleteIds)
    }

    @Test
    fun musicSwitchDropsStockStations() {
        val songs = List(8) { track("j$it", genre = "Jazz", durationMs = 30 * MIN) }
        val station = Lineup.musicTemplates(songs).single()
        val off = Lineup.plan(songs, listOf(StoredChannel(station, true)), eastWest = false, music = false)
        assertTrue(station.id in off.deleteIds)
        val on = Lineup.plan(songs, listOf(StoredChannel(station, true)), eastWest = false, music = true)
        assertTrue(on.channels.any { it.def.id == station.id && !it.dormant })
        assertFalse(station.id in on.deleteIds)
    }

    @Test
    fun editedExtraUsesItsOwnPoolWhenTheStockPoolDoesNotQualify() {
        val drama = Lineup.optionalThemes().first { it.id == "opt:drama" }
        val edited = drama.copy(rules = Rules(genres = setOf("Comedy")))
        val library = listOf(movie("m", genres = setOf("Comedy"), durationMs = 4 * HOUR))
        val plan = Lineup.plan(library, listOf(StoredChannel(edited, true)), eastWest = true, music = false)
        assertFalse(plan.channels.first { it.def.id == drama.id }.dormant)
        assertTrue(plan.channels.any { it.def.id == Lineup.westId(drama.id) })
    }

    @Test
    fun numberOnlyEditsAreNotDeleted() {
        val drama = Lineup.optionalThemes().first { it.id == "opt:drama" }.copy(number = 450)
        val plan = Lineup.plan(emptyList(), listOf(StoredChannel(drama, true)), eastWest = false, music = false)
        assertEquals(450, plan.channels.first { it.def.id == drama.id }.def.number)
        assertFalse(drama.id in plan.deleteIds)
    }

    @Test
    fun renamedMusicStationSurvivesTheLastTrackBeingRemoved() {
        val songs = listOf(track("t", durationMs = 4 * HOUR))
        val renamed = Lineup.musicTemplates(songs).single().copy(name = "My Jazz")
        val plan = Lineup.plan(emptyList(), listOf(StoredChannel(renamed, true)), eastWest = true, music = true)
        assertEquals("My Jazz", plan.channels.first { it.def.id == renamed.id }.def.name)
        assertTrue(plan.channels.first { it.def.id == renamed.id }.dormant)
        assertFalse(renamed.id in plan.deleteIds)
    }

    @Test
    fun collidingGenreSlugsKeepTheirIdentityWhenAnotherGenreDisappears() {
        val songs = listOf(track("a", genre = "Jazz!"), track("b", genre = "Jazz?"))
        val both = Lineup.musicTemplates(songs).associate { it.name to it.id }
        assertNotEquals(both["Jazz!"], both["Jazz?"])
        for (song in songs) {
            val one = Lineup.musicTemplates(listOf(song)).single()
            assertEquals(both.getValue(one.name), one.id)
        }
    }

    @Test
    fun customChannelGetsAWestTwin() {
        val library = listOf(movie("d", genres = setOf("Drama"), durationMs = 4 * HOUR))
        val custom = StoredChannel(
            ChannelDef("custom:abcd", 420, "Mine", ChannelKind.CUSTOM, Rules(genres = setOf("Drama"))),
            builtIn = false,
        )
        val hidden = StoredChannel(
            ChannelDef("custom:hide", 421, "Secret", ChannelKind.CUSTOM, Rules(genres = setOf("Drama")), hidden = true),
            builtIn = false,
        )
        val plan = Lineup.plan(library, listOf(custom, hidden), eastWest = true, music = false)
        val west = plan.channels.first { it.def.id == "custom:abcd:w" }
        assertEquals("Mine-W", west.def.name)
        assertEquals(920, west.def.number)
        assertEquals("Mine", plan.channels.first { it.def.id == "custom:abcd" }.def.name)
        assertTrue(plan.channels.none { it.def.id == "custom:hide:w" })
        assertEquals("Secret", plan.channels.first { it.def.id == "custom:hide" }.def.name)
    }

    @Test
    fun restartChannelIsLeftAlone() {
        val restart = StoredChannel(
            ChannelDef(Lineup.RESTART_ID, Lineup.RESTART_NUMBER, "From the Top", ChannelKind.CUSTOM, Rules(), hidden = true),
            builtIn = true,
        )
        val plan = Lineup.plan(emptyList(), listOf(restart), eastWest = true, music = true)
        assertFalse(Lineup.RESTART_ID in plan.deleteIds)
        assertTrue(plan.channels.none { it.def.id == Lineup.RESTART_ID })
    }

    @Test
    fun fullLineupNumbersAreUnique() {
        val library = listOf(
            movie("m", durationMs = 48 * HOUR, genres = setOf("Drama", "News")),
            movie("kid", durationMs = 4 * HOUR, genres = setOf("Children"), rating = "TV-Y"),
        ) + List(8) { episode("e$it", "Duck", episode = it + 1, durationMs = 30 * MIN, network = "Disney Channel") } +
            List(8) { track("j$it", genre = "Jazz", durationMs = 30 * MIN) }
        val plan = Lineup.plan(library, emptyList(), eastWest = true, music = true)
        assertEquals(emptyList(), Catalog.validateNumbers(plan.channels.map { it.def }))
        assertTrue(plan.channels.any { it.def.id == "opt:news" })
        assertTrue(plan.channels.any { it.def.id == "movies:3" })
        assertTrue(plan.channels.any { it.def.id == "music:jazz" })
        assertTrue(plan.channels.none { it.def.number == Lineup.RESTART_NUMBER })
    }
}
