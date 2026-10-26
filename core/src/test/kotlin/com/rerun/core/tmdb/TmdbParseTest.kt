package com.rerun.core.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class TmdbParseTest {
    @Test
    fun readsExportLinesAndSkipsJunk() {
        val lines = sequenceOf(
            """{"id":54,"name":"Disney Channel"}""",
            "",
            "not json",
            """{"id":999,"name":"Disney Channel"}""",
            """{"name":"No Id"}""",
            """{"id":13,"name":" Nickelodeon "}""",
        )
        val entries = TmdbParse.exportEntries(lines)
        assertEquals(listOf(TmdbSource(54, "Disney Channel"), TmdbSource(999, "Disney Channel"), TmdbSource(13, "Nickelodeon")), entries)
        assertEquals(listOf(13L), TmdbParse.named(entries, listOf("nickelodeon")).map { it.id })
        assertEquals(listOf(54L, 999L), TmdbParse.named(entries, listOf("Disney Channel", "Toon Disney")).map { it.id })
    }

    @Test
    fun readsOriginCountry() {
        assertEquals("US", TmdbParse.originCountry("""{"id":54,"name":"Disney Channel","origin_country":"US"}"""))
        assertNull(TmdbParse.originCountry("""{"id":54,"origin_country":""}"""))
        for (bad in listOf("oops", "{}")) {
            assertFailsWith<IllegalArgumentException> { TmdbParse.originCountry(bad) }
        }
    }

    @Test
    fun readsDiscoverPages() {
        val page = TmdbParse.discoverPage("""{"page":2,"results":[{"id":5,"name":"A"},{"id":6}],"total_pages":7,"total_results":130}""")
        assertEquals(DiscoverPage(listOf(5, 6), 2, 7), page)
        assertEquals(DiscoverPage(emptyList(), 1, 0), TmdbParse.discoverPage("""{"page":1,"results":[],"total_pages":0}"""))
    }

    @Test
    fun invalidDiscoverResponsesMustNotMasqueradeAsAnEmptyCatalog() {
        for (text in listOf("not json", "{}", """{"page":1,"results":{},"total_pages":1}""",
            """{"page":1,"results":[{}],"total_pages":1}""")) {
            assertFailsWith<IllegalArgumentException> { TmdbParse.discoverPage(text) }
        }
    }
}
