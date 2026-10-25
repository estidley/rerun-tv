package com.rerun.core.tmdb

import com.rerun.core.catalog.Catalog
import com.rerun.core.episode
import com.rerun.core.movie
import com.rerun.core.resolve.Resolver
import com.rerun.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkReferenceTest {
    private fun ref(vararg channels: ChannelReference) = NetworkReference(channels.toList(), "2026-10-25", "US")

    private fun channel(id: String, shows: Set<Long> = emptySet(), movies: Set<Long> = emptySet()) = ChannelReference(
        channelId = id, realName = Catalog.networks.first { it.id == id }.realName,
        networks = listOf(TmdbSource(1, "Net")), companies = emptyList(), shows = shows, movies = movies,
    )

    private fun <T> withReference(reference: NetworkReference, block: () -> T): T {
        NetworkReference.use(reference)
        try { return block() } finally { NetworkReference.use(null) }
    }

    @Test
    fun episodesUseShowIdsAndMoviesUseMovieIds() {
        val r = ref(channel("disney", shows = setOf(100), movies = setOf(200)))
        assertEquals(setOf("disney"), r.channelsFor(episode("e", "Lizzie").copy(tmdbId = "100")))
        assertEquals(emptySet(), r.channelsFor(episode("e", "Other").copy(tmdbId = "200")))
        assertEquals(setOf("disney"), r.channelsFor(movie("m").copy(tmdbId = "200")))
        assertEquals(emptySet(), r.channelsFor(movie("m").copy(tmdbId = "100")))
        assertEquals(emptySet(), r.channelsFor(movie("m")))
        assertEquals(emptySet(), r.channelsFor(track("t").copy(tmdbId = "100")))
    }

    @Test
    fun jsonRoundTrips() {
        val r = ref(
            channel("nick", shows = setOf(3, 1, 2)),
            ChannelReference("hbo", "HBO", listOf(TmdbSource(49, "HBO")), listOf(TmdbSource(7429, "HBO Films")), emptySet(), setOf(9L)),
        )
        val back = NetworkReference.parse(r.toJson())
        assertEquals(r.channels, back.channels)
        assertEquals("2026-10-25", back.generated)
        assertEquals("US", back.country)
    }

    @Test
    fun unknownVersionOrGarbageIsEmpty() {
        assertTrue(NetworkReference.parse("""{"version":99,"channels":[]}""").isEmpty)
        assertTrue(NetworkReference.parse("""{"channels":[]}""").isEmpty)
        assertTrue(runCatching { NetworkReference.parse("not json") }.getOrDefault(NetworkReference.EMPTY).isEmpty)
    }

    @Test
    fun aShowLandsOnEveryNetworkItAiredOnPlusThePlexNetwork() = withReference(
        ref(channel("nick", shows = setOf(42)), channel("cn", shows = setOf(42))),
    ) {
        val show = episode("e", "Shared Show", network = "Boomerang").copy(tmdbId = "42")
        assertEquals(setOf("nick", "cn", "boomerang"), Resolver.networksOf(show))
        assertEquals("nick", Resolver.networkOf(show))
        val nick = Catalog.builtInChannels().first { it.id == "nick" }
        assertEquals(listOf(show), Resolver.pool(nick, listOf(show)))
    }

    @Test
    fun theReferenceBeatsOwnerRouting() = withReference(ref(channel("hbo", movies = setOf(7)))) {
        val m = movie("m", studio = "Walt Disney Pictures", rating = "PG").copy(tmdbId = "7")
        assertEquals(setOf("hbo"), Resolver.networksOf(m))
    }

    @Test
    fun withoutAMatchRoutingStillApplies() = withReference(ref(channel("hbo", movies = setOf(7)))) {
        val m = movie("m", studio = "Walt Disney Pictures", rating = "PG").copy(tmdbId = "8")
        assertEquals(setOf("disney"), Resolver.networksOf(m))
    }

    @Test
    fun everyNetworkKeepsItsRealName() {
        for (n in Catalog.networks) {
            assertTrue(n.realName.isNotBlank(), "${n.id} needs a real name")
            assertTrue(n.realName != n.name, "${n.id} shows its real name on screen")
        }
    }
}
