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
    @Test
    fun stockToonukiUpgradesToAnimeWhilePreservingUserPresentationAndEditedPools() {
        val template = Catalog.builtInChannels().single { it.id == "toonami" }
        val legacy = template.copy(name = "My Anime", number = 777, kidsSafe = true,
            rules = Rules(networks = setOf("toonami"), titles = Curated.toonamiShows.toSet()))
        val library = listOf(episode("anime", "New Anime", genres = setOf("Anime"), durationMs = 4 * HOUR))
        val plan = Lineup.plan(library, listOf(StoredChannel(legacy, true)), music = false)
        val east = plan.channels.single { it.def.id == "toonami" }
        assertEquals(legacy.copy(rules = template.rules, kidsSafe = false), east.def)
        assertFalse(east.dormant)
        assertEquals(4 * HOUR, east.poolMs)
        val refreshed = Lineup.plan(library, plan.channels.map { StoredChannel(it.def, it.builtIn) }, false)
        assertEquals(east, refreshed.channels.single { it.def.id == "toonami" })
        val edited = legacy.copy(rules = Rules(genres = setOf("Comedy")))
        assertEquals(edited, Lineup.plan(library, listOf(StoredChannel(edited, true)), false)
            .channels.single { it.def.id == "toonami" }.def)
    }

    @Test
    fun decadesQualifyIndependentlyByMediaTypeAndEligibleDuration() {
        val library = listOf(
            episode("tv", "Show", durationMs = 24 * HOUR, year = 1985),
            episode("short", "Other", durationMs = 24 * HOUR - 1, year = 1975),
            track("music", durationMs = 12 * HOUR).copy(year = 1995),
            track("short-music", durationMs = 12 * HOUR - 1).copy(year = 2005),
            movie("movie", durationMs = 100 * HOUR, year = 1965),
            episode("unknown", "Unknown", durationMs = 100 * HOUR, year = null),
            track("invalid", durationMs = 100 * HOUR).copy(year = 0),
            track("tiny", durationMs = 59_999).copy(year = 2005),
        )
        val enabled = Lineup.channels(library, music = true).filter { it.id.startsWith("decade:") }
        assertEquals(setOf("decade:tv:1980", "decade:music:1990"), enabled.map { it.id }.toSet())
        assertEquals(listOf("decade:tv:1980"), Lineup.channels(library, music = false)
            .filter { it.id.startsWith("decade:") }.map { it.id })
        val plan = Lineup.plan(library, emptyList(), music = true)
        assertEquals(enabled.toSet(), plan.channels.filter { it.def.id.startsWith("decade:") }.map { it.def }.toSet())
        assertTrue(plan.channels.filter { it.def.id.startsWith("decade:") }.all { !it.dormant })
    }

    @Test
    fun decadeRefreshPreservesEditsAndRemovesUnqualifiedStock() {
        val library = listOf(
            episode("tv", "Show", durationMs = 24 * HOUR, year = 1985),
            track("music", durationMs = 12 * HOUR).copy(year = 1985),
        )
        val initial = Lineup.plan(library, emptyList(), music = true)
        val decades = initial.channels.filter { it.def.id.startsWith("decade:") }
        assertEquals(2, decades.size)
        val existing = decades.map { StoredChannel(it.def, true) }
        val refreshed = Lineup.plan(library, existing, music = true)
        assertEquals(decades, refreshed.channels.filter { it.def.id.startsWith("decade:") })
        val removed = Lineup.plan(emptyList(), existing, music = true)
        assertTrue(removed.deleteIds.containsAll(decades.map { it.def.id }))
        val musicOff = Lineup.plan(library, existing, music = false)
        assertTrue(musicOff.channels.none { it.def.id.startsWith("decade:music:") })
        val tv = decades.first { it.def.id == "decade:tv:1980" }.def
        val edited = tv.copy(name = "My Eighties", rules = Rules(kinds = setOf(MediaKind.TRACK)))
        val kept = Lineup.plan(library, listOf(StoredChannel(edited, true)), music = true)
        assertEquals(edited, kept.channels.first { it.def.id == tv.id }.def)
        assertEquals(12 * HOUR, kept.channels.first { it.def.id == tv.id }.poolMs)
        assertTrue(kept.channels.first { it.def.id == tv.id }.dormant)
        val dormant = Lineup.plan(emptyList(), listOf(StoredChannel(edited, true)), true)
        assertTrue(dormant.channels.first { it.def.id == tv.id }.dormant)
        val collision = Lineup.plan(library, listOf(StoredChannel(
            ChannelDef("custom:reserved", tv.number, "Mine", ChannelKind.CUSTOM, Rules()), false,
        )), true)
        assertEquals(collision.channels.size, collision.channels.map { it.def.number }.toSet().size)
    }

    private val banned = listOf(
        "disney", "nickelodeon", "hbo", "cnn", "cartoon network", "boomerang", "toonami",
        "syfy", "pbs", "mtv", "hgtv", "paramount", "comedy central", "adult swim",
        "freeform", "abc family", "food network", "cinemax", "touchstone", "nick jr",
        "movies-1", "movies-2",
    )

    @Test
    fun adsStationNeedsOnlyOneClipAndDoesNotUseAReservedNumber() {
        val custom = ChannelDef("custom:one", 998, "Custom", ChannelKind.CUSTOM, Rules())
        val existing = listOf(StoredChannel(custom, false))
        val enabled = Lineup.plan(emptyList(), existing, music = false, adsPoolMs = 15_000)
        val ads = enabled.channels.single { it.def.id == Lineup.ADS_ID }
        assertEquals("Buy Me Maybe", ads.def.name)
        assertFalse(ads.dormant)
        assertEquals(15_000, ads.poolMs)
        assertNotEquals(998, ads.def.number)
        assertNotEquals(Lineup.RESTART_NUMBER, ads.def.number)
        val disabled = Lineup.plan(emptyList(), listOf(StoredChannel(ads.def, true)), false)
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
            for (brand in banned) {
                assertFalse(Regex("\\b${Regex.escape(brand)}\\b").containsMatchIn(name), "${def.id} uses $brand")
            }
        }
        assertEquals("Dinky Channel", defs.first { it.id == "disney" }.name)
        assertEquals("Laugh Track", defs.first { it.id == "comedy" }.name)
        assertEquals("True-ish", defs.first { it.id == "opt:docs" }.name)
        assertEquals("Canned Laughter", defs.first { it.id == "opt:sitcom" }.name)
        assertEquals("Sob Story", defs.first { it.id == "opt:dramedy" }.name)
        assertEquals("Cold Read", defs.first { it.id == "opt:psychic" }.name)
        assertEquals("This Just In", defs.first { it.id == "opt:news" }.name)
        assertEquals(listOf("The Big Picture", "Second Feature", "Late Seating"), Lineup.movieChannels().map { it.name })
    }

    @Test
    fun chooseNameKeepsARenameAndReplacesStock() {
        assertEquals("Dinky Channel", Catalog.chooseName("disney", "Disney Channel", "Dinky Channel"))
        assertEquals("Castle Club", Catalog.chooseName("disney", "Castle Club", "Dinky Channel"))
        assertEquals("Castle Club W", Catalog.chooseName("disney", "Castle Club W", "Dinky Channel"))
        assertEquals("Laughing Stock", Catalog.chooseName("comedy", "The Chuckle", "Laughing Stock"))
        assertEquals("True-ish", Catalog.chooseName("opt:docs", "The Reel", "True-ish"))
        assertEquals("Fright Night", Catalog.chooseName("horror", "Horror", "Fright Night"))
        assertEquals("Ted's TV", Catalog.chooseName("tbs", "Laugh Track", "Ted's TV"))
        assertEquals("Laugh Track", Catalog.chooseName("comedy", "Laughing Stock", "Laugh Track"))
        assertEquals("Remote Control", Catalog.chooseName("mtv", "The Video Channel", "Remote Control"))
        assertEquals("My Superstation", Catalog.chooseName("tbs", "My Superstation", "Ted's TV"))
    }

    @Test
    fun tbsDocumentariesStayOnTedsTvAndComedyAloneFeedsLaughTrack() {
        val defs = Catalog.builtInChannels().associateBy { it.id }
        val documentary = movie("history", genres = setOf("Documentary"), studio = "TBS")
        val comedy = movie("funny", genres = setOf("Comedy"), studio = "Other")
        assertEquals("Ted's TV", defs.getValue("tbs").name)
        assertEquals(listOf(documentary), com.rerun.core.resolve.Resolver.pool(defs.getValue("tbs"), listOf(documentary, comedy)))
        assertEquals(listOf(comedy), com.rerun.core.resolve.Resolver.pool(defs.getValue("comedy"), listOf(documentary, comedy)))
        val old = defs.getValue("tbs").copy(name = "Laugh Track")
        val refreshed = Lineup.plan(listOf(documentary.copy(durationMs = 4 * HOUR)),
            listOf(StoredChannel(old, true)), music = false)
        assertEquals("Ted's TV", refreshed.channels.first { it.def.id == "tbs" }.def.name)
    }

    @Test
    fun allPredefinedChannelsKeepTheirMinimumUnlessExplicitlyOverridden() {
        val defs = Catalog.builtInChannels() + Lineup.optionalThemes() + Lineup.movieChannels()
        for (def in defs) {
            val required = Lineup.minimumContentMs(def)
            assertTrue(required >= 4 * HOUR, def.id)
            for (edited in listOf(def, def.copy(name = "My station"))) {
                assertTrue(Lineup.isDormant(edited, required - 1), def.id)
                assertFalse(Lineup.isDormant(edited, required), def.id)
                assertFalse(Lineup.isDormant(edited.copy(minimumContentHours = 1), HOUR), def.id)
                assertTrue(Lineup.isDormant(edited.copy(minimumContentHours = 0), 0), def.id)
                assertFalse(Lineup.isDormant(edited.copy(minimumContentHours = 0), MIN), def.id)
            }
        }
        val movieDef = Lineup.movieChannels()[1].copy(name = "Small movie night")
        val below = Lineup.plan(listOf(movie("m", durationMs = 4 * HOUR)),
            listOf(StoredChannel(movieDef, true)), false)
        assertTrue(below.channels.first { it.def.id == movieDef.id }.dormant)
        val override = Lineup.plan(listOf(movie("m", durationMs = HOUR)),
            listOf(StoredChannel(movieDef.copy(minimumContentHours = 1), true)), false)
        assertFalse(override.channels.first { it.def.id == movieDef.id }.dormant)
    }

    @Test
    fun decadeOverridesSurviveRefreshButDoNotBypassTheMusicSwitch() {
        val tracks = listOf(track("t", durationMs = HOUR))
        val stock = Lineup.channels(listOf(track("big", durationMs = 12 * HOUR)), true)
            .first { it.id == "decade:music:1990" }
        val def = stock.copy(minimumContentHours = 1)
        val on = Lineup.plan(tracks, listOf(StoredChannel(def, true)), true)
        assertFalse(on.channels.first { it.def.id == def.id }.dormant)
        val off = Lineup.plan(tracks, listOf(StoredChannel(def, true)), false)
        assertTrue(off.channels.first { it.def.id == def.id }.dormant)
        assertEquals(1, off.channels.first { it.def.id == def.id }.def.minimumContentHours)
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
    fun renamedChannelKeepsItsNameAndHasOneSchedule() {
        val shows = List(8) { episode("e$it", "Duck", episode = it + 1, durationMs = 30 * MIN, network = "Disney Channel") }
        val stored = StoredChannel(Catalog.builtInChannels().first { it.id == "disney" }.copy(name = "Castle Club"), true)
        val plan = Lineup.plan(shows, listOf(stored), music = false)
        assertEquals("Castle Club", plan.channels.first { it.def.id == "disney" }.def.name)
        assertEquals(1, plan.channels.count { it.def.rules == stored.def.rules })
        assertTrue(plan.channels.none { it.def.id.endsWith(":w") })
    }

    @Test
    fun dormantChannelsKeepTheirStockName() {
        val stored = StoredChannel(Catalog.builtInChannels().first { it.id == "disney" }.copy(name = "Disney Channel"), true)
        val plan = Lineup.plan(emptyList(), listOf(stored), music = false)
        val channel = plan.channels.first { it.def.id == "disney" }
        assertEquals("Dinky Channel", channel.def.name)
        assertTrue(channel.dormant)
    }

    @Test
    fun stockExtraIsDeletedAndAnEditIsKept() {
        val drama = Lineup.optionalThemes().first { it.id == "opt:drama" }
        val stock = Lineup.plan(emptyList(), listOf(StoredChannel(drama, true)), music = false)
        assertTrue("opt:drama" in stock.deleteIds)
        assertTrue(stock.channels.none { it.def.id == "opt:drama" })

        val edited = Lineup.plan(emptyList(), listOf(StoredChannel(drama.copy(name = "My Dramas"), true)), music = false)
        val kept = edited.channels.first { it.def.id == "opt:drama" }
        assertEquals("My Dramas", kept.def.name)
        assertTrue(kept.dormant)
        assertFalse("opt:drama" in edited.deleteIds)
    }

    @Test
    fun musicSwitchDropsStockStations() {
        val songs = List(8) { track("j$it", genre = "Jazz", durationMs = 30 * MIN) }
        val station = Lineup.musicTemplates(songs).single()
        val off = Lineup.plan(songs, listOf(StoredChannel(station, true)), music = false)
        assertTrue(station.id in off.deleteIds)
        val on = Lineup.plan(songs, listOf(StoredChannel(station, true)), music = true)
        assertTrue(on.channels.any { it.def.id == station.id && !it.dormant })
        assertFalse(station.id in on.deleteIds)
    }

    @Test
    fun editedExtraUsesItsOwnPoolWhenTheStockPoolDoesNotQualify() {
        val drama = Lineup.optionalThemes().first { it.id == "opt:drama" }
        val edited = drama.copy(rules = Rules(genres = setOf("Comedy")))
        val library = listOf(movie("m", genres = setOf("Comedy"), durationMs = 4 * HOUR))
        val plan = Lineup.plan(library, listOf(StoredChannel(edited, true)), music = false)
        assertFalse(plan.channels.first { it.def.id == drama.id }.dormant)
    }

    @Test
    fun numberOnlyEditsAreNotDeleted() {
        val drama = Lineup.optionalThemes().first { it.id == "opt:drama" }.copy(number = 450)
        val plan = Lineup.plan(emptyList(), listOf(StoredChannel(drama, true)), music = false)
        assertEquals(450, plan.channels.first { it.def.id == drama.id }.def.number)
        assertFalse(drama.id in plan.deleteIds)
    }

    @Test
    fun renamedMusicStationSurvivesTheLastTrackBeingRemoved() {
        val songs = listOf(track("t", durationMs = 4 * HOUR))
        val renamed = Lineup.musicTemplates(songs).single().copy(name = "My Jazz")
        val plan = Lineup.plan(emptyList(), listOf(StoredChannel(renamed, true)), music = true)
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
    fun customAndHiddenChannelsKeepTheirIdentity() {
        val library = listOf(movie("d", genres = setOf("Drama"), durationMs = 4 * HOUR))
        val custom = StoredChannel(
            ChannelDef("custom:abcd", 420, "Mine", ChannelKind.CUSTOM, Rules(genres = setOf("Drama"))),
            builtIn = false,
        )
        val hidden = StoredChannel(
            ChannelDef("custom:hide", 421, "Secret", ChannelKind.CUSTOM, Rules(genres = setOf("Drama")), hidden = true),
            builtIn = false,
        )
        val plan = Lineup.plan(library, listOf(custom, hidden), music = false)
        assertEquals("Mine", plan.channels.first { it.def.id == "custom:abcd" }.def.name)
        assertEquals("Secret", plan.channels.first { it.def.id == "custom:hide" }.def.name)
        assertTrue(plan.channels.first { it.def.id == "custom:hide" }.def.hidden)
        assertTrue(plan.channels.none { it.def.id.endsWith(":w") })
    }

    @Test
    fun restartChannelIsLeftAlone() {
        val restart = StoredChannel(
            ChannelDef(Lineup.RESTART_ID, Lineup.RESTART_NUMBER, "From the Top", ChannelKind.CUSTOM, Rules(), hidden = true),
            builtIn = true,
        )
        val plan = Lineup.plan(emptyList(), listOf(restart), music = true)
        assertFalse(Lineup.RESTART_ID in plan.deleteIds)
        assertTrue(plan.channels.none { it.def.id == Lineup.RESTART_ID })
    }

    @Test
    fun toonukiOwnsAnimeSoTheDuplicateStockStationIsRetiredButEditsSurvive() {
        val anime = Rules(genres = setOf("Anime"))
        val library = listOf(episode("anime", "Show", genres = setOf("Anime"), durationMs = 4 * HOUR))
        val stock = StoredChannel(ChannelDef("opt:anime", 328, "Import Toons", ChannelKind.THEME, anime), true)
        val retired = Lineup.plan(library, listOf(stock), music = false)
        assertTrue("opt:anime" in retired.deleteIds)
        assertTrue(retired.channels.none { it.def.rules == anime && it.def.id != "toonami" })
        val renamed = StoredChannel(stock.def.copy(name = "My Anime"), true)
        val kept = Lineup.plan(library, listOf(renamed), music = false)
        assertEquals("My Anime", kept.channels.single { it.def.id == "opt:anime" }.def.name)
    }
    @Test
    fun fullLineupNumbersAreUnique() {
        val library = listOf(
            movie("m", durationMs = 48 * HOUR, genres = setOf("Drama", "News")),
            movie("kid", durationMs = 4 * HOUR, genres = setOf("Children"), rating = "TV-Y"),
        ) + List(8) { episode("e$it", "Duck", episode = it + 1, durationMs = 30 * MIN, network = "Disney Channel") } +
            List(8) { track("j$it", genre = "Jazz", durationMs = 30 * MIN) }
        val plan = Lineup.plan(library, emptyList(), music = true)
        assertEquals(emptyList(), Catalog.validateNumbers(plan.channels.map { it.def }))
        assertTrue(plan.channels.any { it.def.id == "opt:news" })
        assertTrue(plan.channels.any { it.def.id == "movies:3" })
        assertTrue(plan.channels.any { it.def.id == "music:jazz" })
        assertTrue(plan.channels.none { it.def.number == Lineup.RESTART_NUMBER })
    }
}
