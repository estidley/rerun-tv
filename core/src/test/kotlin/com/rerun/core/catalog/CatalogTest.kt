package com.rerun.core.catalog

import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.Rules
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTest {
    @Test
    fun aliasLookup() {
        assertEquals("syfy", Catalog.canonicalNetwork("Sci Fi Channel"))
        assertEquals("disney", Catalog.canonicalNetwork("Toon Disney"))
        assertEquals("disneyxd", Catalog.canonicalNetwork(" disney xd "))
        assertEquals("adultswim", Catalog.canonicalNetwork("[adult swim]"))
        assertNull(Catalog.canonicalNetwork("Fuji TV"))
        assertNull(Catalog.canonicalNetwork(null))
    }

    @Test
    fun validateNumbersRejectsDuplicates() {
        val a = ChannelDef("a", 301, "A", ChannelKind.CUSTOM, Rules())
        val b = ChannelDef("b", 301, "B", ChannelKind.CUSTOM, Rules())
        val c = ChannelDef("c", 302, "C", ChannelKind.CUSTOM, Rules())
        val errors = Catalog.validateNumbers(listOf(a, b, c))
        assertEquals(1, errors.size)
        assertTrue("301" in errors[0])
    }

    @Test
    fun builtInsHaveUniqueNumbers() {
        val defs = Catalog.builtInChannels()
        assertEquals(emptyList(), Catalog.validateNumbers(defs))
        assertEquals(defs.size, defs.map { it.id }.toSet().size)
        assertTrue(defs.any { it.name == "The Backlot" && it.number == 109 })
        assertTrue(defs.any { it.name == "Dinky XD" && it.number == 108 })
    }

    @Test
    fun cartoonNetworkHasToonamiBlock() {
        val cn = assertNotNull(Catalog.builtInChannels().firstOrNull { it.id == "cn" })
        val block = cn.blocks.single()
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), block.days)
        assertEquals(LocalTime.of(16, 0), block.start)
        assertEquals(LocalTime.of(19, 0), block.end)
        assertTrue("Dragon Ball Z" in block.rules.titles)
    }

    @Test
    fun expandedCableChannelsHaveStableRulesNumbersAndAliases() {
        val expected = mapOf(
            "bbcamerica" to ("The Beeb" to 127),
            "natgeo" to ("The Atlas" to 128),
            "animalplanet" to ("The Wild" to 129),
            "hallmark" to ("The Hearth" to 131),
            "gsn" to ("The Game Room" to 132),
            "tbsjapan" to ("Tokyo Tower" to 133),
        )
        val definitions = Catalog.builtInChannels().associateBy { it.id }
        for ((id, presentation) in expected) {
            val def = definitions.getValue(id)
            assertEquals(presentation.first, def.name)
            assertEquals(presentation.second, def.number)
            assertEquals(ChannelKind.NETWORK, def.kind)
            assertEquals(setOf(id), def.rules.networks)
        }
        val film = definitions.getValue("filmvault")
        assertEquals("The Film Vault", film.name)
        assertEquals(363, film.number)
        assertEquals(ChannelKind.THEME, film.kind)
        assertEquals(setOf(com.rerun.core.model.MediaKind.MOVIE), film.rules.kinds)
        assertEquals((1900..1980 step 10).toSet(), film.rules.decades)
        assertEquals(setOf("Western"), Lineup.optionalThemes().single { it.id == "opt:western" }.rules.genres)
        for ((alias, id) in mapOf(
            "BBC America" to "bbcamerica", "Nat Geo" to "natgeo", "Animal Planet" to "animalplanet",
            "Hallmark" to "hallmark", "GSN" to "gsn", "Tokyo Broadcasting System" to "tbsjapan",
            "TBS Japan" to "tbsjapan", "Turner Broadcasting System" to "tbs", "TBS" to "tbs",
        )) assertEquals(id, Catalog.canonicalNetwork(alias))
        val aliases = Catalog.networks.flatMap { network -> network.aliases.map { it.trim().lowercase() to network.id } }
        val duplicateAliases = aliases.groupBy { it.first }.filterValues { entries -> entries.map { it.second }.distinct().size > 1 }
        assertEquals(emptySet(), duplicateAliases.keys)
    }

    @Test
    fun kidsSafeNetworksAreMarked() {
        val byId = Catalog.builtInChannels().associateBy { it.id }
        for (id in listOf("disney", "disneyxd", "disneyjr", "nick", "nickjr", "cn", "boomerang", "pbskids")) {
            assertTrue(byId.getValue(id).kidsSafe, "$id should be kids-safe")
        }
        assertTrue(!byId.getValue("touchstone").kidsSafe)
        assertTrue(!byId.getValue("toonami").kidsSafe)
    }
}
