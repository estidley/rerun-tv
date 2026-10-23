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
        assertTrue(defs.any { it.name == "Touchstone" && it.number == 109 })
        assertTrue(defs.any { it.name == "Disney XD" && it.number == 108 })
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
    fun kidsSafeNetworksAreMarked() {
        val byId = Catalog.builtInChannels().associateBy { it.id }
        for (id in listOf("disney", "disneyxd", "disneyjr", "nick", "nickjr", "cn", "boomerang", "toonami", "pbskids")) {
            assertTrue(byId.getValue(id).kidsSafe, "$id should be kids-safe")
        }
        assertTrue(!byId.getValue("touchstone").kidsSafe)
    }
}
