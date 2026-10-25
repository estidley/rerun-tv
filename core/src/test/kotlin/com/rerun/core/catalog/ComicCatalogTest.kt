package com.rerun.core.catalog

import com.rerun.core.HOUR
import com.rerun.core.episode
import com.rerun.core.movie
import com.rerun.core.track
import com.rerun.core.codec.ChannelCodec
import com.rerun.core.codec.StateCodec
import com.rerun.core.model.MediaKind
import com.rerun.core.model.PlayOrder
import com.rerun.core.resolve.Resolver
import com.rerun.core.schedule.ChannelState
import com.rerun.core.schedule.EntryKind
import com.rerun.core.schedule.Scheduler
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComicCatalogTest {
    private fun channel(id: String) = ComicCatalog.channels.single { it.id == id }

    @Test
    fun membershipUsesSpecificMetadataAndKnownAdaptationsNotGenericOwnersOrGenres() {
        val marvel = movie("iron", "Iron Man", year = 2008, studio = "Paramount Pictures")
        val spider = movie("spider", "Spider-Man", year = 2002, studio = "Columbia Pictures")
        val xmen = movie("xmen", "X-Men", year = 2000, studio = "20th Century Fox")
        val newMarvel = movie("future", studio = "Marvel Studios")
        val tagged = movie("tagged", labels = setOf("Marvel"))
        val collection = movie("collection").copy(collections = setOf("Marvel Cinematic Universe"))
        val dc = movie("dc", "The Dark Knight", year = 2008, studio = "Warner Bros.")
        val newDc = movie("new-dc", studio = "DC Studios")
        val joker = movie("joker", "Joker: Folie à Deux", year = 2024)
        val unrelated = movie("other", "A Superhero Story", studio = "Warner Bros.", genres = setOf("Action"))
        val wrongYear = movie("unrelated-echo", "Daredevil", year = 2010)
        for (item in listOf(marvel, spider, xmen, newMarvel, tagged, collection)) {
            assertTrue(ComicCatalog.matches(item, "marvel"), item.title)
            assertFalse(ComicCatalog.matches(item, "dc"), item.title)
        }
        for (item in listOf(dc, newDc, joker)) assertTrue(ComicCatalog.matches(item, "dc"), item.title)
        assertFalse(ComicCatalog.matches(unrelated, "dc"))
        assertFalse(ComicCatalog.matches(wrongYear, "marvel"))
        assertFalse(ComicCatalog.matches(track("song").copy(labels = setOf("Marvel")), "marvel"))
    }

    @Test
    fun fiveChannelsSeparateMoviesAndShowsAndPreserveExistingNetworkRouting() {
        val film = movie("film", "Avengers: Endgame", year = 2019, durationMs = 4 * HOUR, studio = "Marvel Studios")
        val show = episode("show", "Marvel's Daredevil", durationMs = 4 * HOUR)
        val dcFilm = movie("batman", "Batman Begins", year = 2005, durationMs = 4 * HOUR)
        val dcShow = episode("arrow", "Arrow", durationMs = 4 * HOUR)
        val library = listOf(film, show, dcFilm, dcShow)
        assertEquals(listOf(film), Resolver.pool(channel("marvel:movies"), library))
        assertEquals(listOf(show), Resolver.pool(channel("marvel:tv"), library))
        assertEquals(listOf(film, show), Resolver.pool(channel("marvel:timeline"), library))
        assertEquals(listOf(dcFilm), Resolver.pool(channel("dc:movies"), library))
        assertEquals(listOf(dcShow), Resolver.pool(channel("dc:tv"), library))
        assertEquals("disney", Resolver.networkOf(film))
        assertEquals(5, ComicCatalog.channels.size)
        assertEquals(PlayOrder.CHRONOLOGICAL, channel("marvel:timeline").order)
        assertEquals(setOf(MediaKind.MOVIE), channel("dc:movies").rules.kinds)
        for (def in ComicCatalog.channels) assertEquals(def, ChannelCodec.decode(ChannelCodec.encode(def)))
        val plan = Lineup.plan(library, emptyList(), eastWest = true, music = false)
        for (def in ComicCatalog.channels) {
            assertFalse(plan.channels.single { it.def.id == def.id }.dormant)
            assertTrue(plan.channels.any { it.def.id == Lineup.westId(def.id) })
        }
        assertEquals(emptyList(), Catalog.validateNumbers(Catalog.builtInChannels()))
    }

    @Test
    fun timelineFollowsOfficialSeasonPositionsThenEachLegacyUniverseSkippingMissingTitles() {
        val captain = movie("cap", "Captain America: The First Avenger", year = 2011)
        val iron = movie("iron", "Iron Man", year = 2008)
        val daredevil1 = episode("dd1", "Daredevil", season = 1, episode = 2)
        val daredevil2 = episode("dd2", "Daredevil", season = 2, episode = 1)
        val ultron = movie("ultron", "Avengers: Age of Ultron", year = 2015)
        val firstClass = movie("first", "X-Men: First Class", year = 2011)
        val xmen = movie("xmen", "X-Men", year = 2000)
        val spider = movie("spider", "Spider-Man", year = 2002)
        val expected = listOf(captain, iron, daredevil1, ultron, daredevil2, firstClass, xmen, spider)
        assertEquals(expected, ComicCatalog.ordered(expected.reversed()))
        assertEquals(listOf(captain, daredevil2, xmen), ComicCatalog.ordered(listOf(xmen, daredevil2, captain)))
        assertEquals(expected, ComicCatalog.ordered(expected.shuffled(kotlin.random.Random(17))))
        val unknownReboot = movie("unknown", "Doctor Strange", year = null)
        assertEquals(listOf(xmen, unknownReboot), ComicCatalog.ordered(listOf(unknownReboot, xmen)))
    }

    @Test
    fun mixedTimelineResumesAcrossPersistedStateWrapsAndSkipsRemovedItems() {
        val a = movie("a", "Captain America: The First Avenger", year = 2011, durationMs = HOUR)
        val b = episode("b", "Daredevil", durationMs = HOUR)
        val c = movie("c", "X-Men", year = 2000, durationMs = HOUR)
        val scheduler = Scheduler(ZoneId.of("UTC"))
        val def = channel("marvel:timeline")
        val first = scheduler.extend(def, listOf(c, b, a), ChannelState(1), null, 0, horizonMs = HOUR)
        assertEquals(listOf("a"), first.entries.filter { it.kind == EntryKind.PROGRAM }.map { it.itemKey })
        val restored = StateCodec.decode(StateCodec.encode(first.state))
        val next = scheduler.extend(def, listOf(c, b, a), restored, HOUR, HOUR, horizonMs = 3 * HOUR)
        assertEquals(listOf("b", "c", "a"), next.entries.filter { it.kind == EntryKind.PROGRAM }.map { it.itemKey })
        val removed = scheduler.extend(def, listOf(c, a), restored, HOUR, HOUR, horizonMs = HOUR)
        assertEquals(listOf("c"), removed.entries.filter { it.kind == EntryKind.PROGRAM }.map { it.itemKey })
    }

    @Test
    fun newStationsAvoidCustomNumbersAndRetainEditsAndDormancy() {
        val def = channel("marvel:movies")
        val custom = def.copy(id = "custom:reserved", name = "My channel", rules = com.rerun.core.model.Rules())
        val library = listOf(movie("film", "Iron Man", year = 2008, durationMs = 4 * HOUR))
        val plan = Lineup.plan(library, listOf(StoredChannel(custom, false)), eastWest = true, music = false)
        assertEquals(plan.channels.size, plan.channels.map { it.def.number }.distinct().size)
        assertEquals(custom, plan.channels.single { it.def.id == custom.id }.def)
        val edited = def.copy(name = "My Marvel", number = 470, rules = def.rules.copy(ratings = setOf("G")))
        val refreshed = Lineup.plan(library, listOf(StoredChannel(edited, true)), true, false)
        assertEquals(edited, refreshed.channels.single { it.def.id == def.id }.def)
        assertTrue(refreshed.channels.single { it.def.id == def.id }.dormant)
        assertTrue(refreshed.channels.none { it.def.id == Lineup.westId(def.id) })
    }

    @Test
    fun changingFromShuffleDoesNotReuseTheOldRandomMovieDeck() {
        val captain = movie("cap", "Captain America: The First Avenger", year = 2011, durationMs = HOUR)
        val iron = movie("iron", "Iron Man", year = 2008, durationMs = HOUR)
        val oldState = ChannelState(1, deck = listOf("iron", "cap"))
        val scheduled = Scheduler(ZoneId.of("UTC")).extend(channel("marvel:timeline"), listOf(iron, captain),
            oldState, null, 0, horizonMs = HOUR)
        assertEquals("cap", scheduled.entries.single().itemKey)
    }
}
