package com.rerun.core.catalog

import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaKind
import com.rerun.core.model.Rules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelDescriptionsTest {
    @Test
    fun networkDescriptionsExplainBroadMetadataInsteadOfInventingGenreLimits() {
        val nationalGeographic = Catalog.builtInChannels().single { it.id == "natgeo" }
        val description = ChannelDescriptions.of(nationalGeographic)
        assertEquals("Network: National Geographic · movies and episodes.", description)
        assertFalse("Documentary" in description)
        val bbc = Catalog.builtInChannels().single { it.id == "bbc" }
        assertEquals("Network: BBC · movies and episodes.", ChannelDescriptions.of(bbc))
    }

    @Test
    fun genreMusicAndCatchallDescriptionsMatchTheirActualPools() {
        val comedy = Catalog.builtInChannels().single { it.id == "comedy" }
        assertEquals("Genres: Comedy · movies and episodes.", ChannelDescriptions.of(comedy))
        val jazz = com.rerun.core.model.ChannelDef(
            "music:jazz", 2000, "Jazz", ChannelKind.MUSIC,
            Rules(genres = setOf("Jazz"), kinds = setOf(MediaKind.TRACK), excludeGenres = setOf("Podcast")),
        )
        assertTrue("Music tracks tagged Jazz" in ChannelDescriptions.of(jazz))
        assertTrue("excluded" in ChannelDescriptions.of(jazz))
        assertTrue("Otherwise-unmatched" in ChannelDescriptions.of(Lineup.catchAllChannel))
        val vault = Catalog.builtInChannels().single { it.id == "filmvault" }
        assertTrue("release 1900s–1980s" in ChannelDescriptions.of(vault))
    }

    @Test
    fun editedMusicDescriptionsRetainFiltersSourcesAndMediaKinds() {
        val jazz = com.rerun.core.model.ChannelDef(
            "music:jazz", 2000, "Jazz", ChannelKind.MUSIC,
            Rules(genres = setOf("Jazz"), kinds = setOf(MediaKind.TRACK), excludeGenres = setOf("Podcast")),
        )
        val filtered = jazz.copy(rules = jazz.rules.copy(decades = setOf(1990)))
        assertEquals(
            "Genres: Jazz · music tracks; release 1990s; exclude genres Podcast.",
            ChannelDescriptions.of(filtered),
        )
        val movies = jazz.copy(rules = jazz.rules.copy(kinds = setOf(MediaKind.MOVIE)))
        assertEquals("Genres: Jazz · movies; exclude genres Podcast.", ChannelDescriptions.of(movies))
        val withLabel = jazz.copy(rules = jazz.rules.copy(labels = setOf("Favorites")))
        assertEquals("Genres: Jazz or Labels: Favorites · music tracks; exclude genres Podcast.", ChannelDescriptions.of(withLabel))
        val blocked = jazz.copy(blocks = listOf(com.rerun.core.model.TimeBlock(
            setOf(java.time.DayOfWeek.MONDAY), java.time.LocalTime.NOON, java.time.LocalTime.of(13, 0), Rules(),
        )))
        assertEquals(
            "Genres: Jazz · music tracks; exclude genres Podcast; 1 scheduled block.",
            ChannelDescriptions.of(blocked),
        )
    }
}
