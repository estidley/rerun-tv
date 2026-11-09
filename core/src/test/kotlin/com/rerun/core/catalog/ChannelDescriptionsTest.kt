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
}
