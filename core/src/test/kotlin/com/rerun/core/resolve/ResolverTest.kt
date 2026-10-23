package com.rerun.core.resolve

import com.rerun.core.HOUR
import com.rerun.core.MIN
import com.rerun.core.catalog.Catalog
import com.rerun.core.episode
import com.rerun.core.movie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolverTest {
    private fun def(id: String) = Catalog.builtInChannels().first { it.id == id }

    @Test
    fun aliasBeatsRouter() =
        assertEquals("disneyxd", Resolver.networkOf(episode("e", "Show", network = "Disney XD", studio = "Marvel Animation", rating = "PG-13")))

    @Test
    fun routerUsedWhenNoAlias() =
        assertEquals("touchstone", Resolver.networkOf(movie("m", studio = "Marvel Studios", rating = "PG-13")))

    @Test
    fun toonamiFindsDbzByTitle() {
        val dbz = episode("e1", "Dragon Ball Z", network = "Fuji TV")
        val other = episode("e2", "Some Sitcom", network = "NBC")
        assertEquals(listOf(dbz), Resolver.pool(def("toonami"), listOf(dbz, other)))
    }

    @Test
    fun titleMatchIgnoresPunctuation() {
        val tenchi = episode("e1", "Tenchi Muyo", network = "AIC")
        assertEquals(listOf(tenchi), Resolver.pool(def("toonami"), listOf(tenchi)))
    }

    @Test
    fun boomerangFindsScooby() {
        val scooby = episode("e1", "Scooby-Doo, Where Are You!", network = "CBS", rating = "TV-G")
        assertEquals(listOf(scooby), Resolver.pool(def("boomerang"), listOf(scooby)))
    }

    @Test
    fun networkChannelUsesRouter() {
        val avatar = episode("e1", "Avatar Next", network = "Paramount+", rating = "TV-Y7", genres = setOf("Animation"))
        assertEquals(listOf(avatar), Resolver.pool(def("nick"), listOf(avatar)))
    }

    @Test
    fun cartoonNetworkPoolIncludesToonamiBlockShows() {
        val dbz = episode("e1", "Dragon Ball Z", network = "Fuji TV")
        val dexter = episode("e2", "Dexter Toon", network = "Cartoon Network")
        assertEquals(setOf(dbz, dexter), Resolver.pool(def("cn"), listOf(dbz, dexter)).toSet())
    }

    @Test
    fun poolExcludesShortAndZeroDuration() {
        val ok = movie("ok", genres = setOf("Horror"))
        val zero = movie("zero", genres = setOf("Horror"), durationMs = 0)
        val short = movie("short", genres = setOf("Horror"), durationMs = 30_000)
        assertEquals(listOf(ok), Resolver.pool(def("horror"), listOf(ok, zero, short)))
    }

    @Test
    fun dormantUnderFourHours() {
        assertTrue(Resolver.isDormant(listOf(movie("a", durationMs = 4 * HOUR - MIN))))
        assertFalse(Resolver.isDormant(listOf(movie("a", durationMs = 2 * HOUR), movie("b", durationMs = 2 * HOUR))))
        assertTrue(Resolver.isDormant(emptyList()))
    }
}
