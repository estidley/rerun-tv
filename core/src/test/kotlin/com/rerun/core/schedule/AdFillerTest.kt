package com.rerun.core.schedule

import com.rerun.core.movie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdFillerTest {
    private val entry = ScheduleEntry("east", 1_000, 66_000, EntryKind.FILLER, null)
    private val ads = listOf(movie("a", durationMs = 30_000), movie("b", durationMs = 30_000))

    @Test
    fun tuningMidBreakUsesTheSameClipAndPositionRegardlessOfCatalogOrder() {
        val first = adFiller(entry, ads, 1_000)!!
        assertEquals(first, adFiller(entry, ads.reversed(), 15_000))
        assertEquals(1_000, first.startMs)
        assertEquals(31_000, first.endMs)
        val second = adFiller(entry, ads, 31_000)!!
        assertNotEquals(first.item.key, second.item.key)
        assertEquals(31_000, second.startMs)
        assertTrue(second.endMs <= entry.endMs)
    }

    @Test
    fun emptyCatalogShortRemainderInvalidDurationsAndProgramsDoNotPlayAds() {
        assertNull(adFiller(entry, emptyList(), 1_000))
        assertNull(adFiller(entry, ads, 61_000))
        assertNull(adFiller(entry, ads, entry.endMs))
        assertNull(adFiller(entry, ads, 0))
        assertNull(adFiller(entry.copy(kind = EntryKind.PROGRAM), ads, 1_000))
        assertNull(adFiller(entry, listOf(movie("invalid", durationMs = 0)), 1_000))
    }

    @Test
    fun oneClipCanRepeatAndAnExactFitEndsAtTheProgramBoundary() {
        val exact = entry.copy(endMs = 61_000)
        val second = adFiller(exact, ads.take(1), 40_000)!!
        assertEquals("a", second.item.key)
        assertEquals(exact.endMs, second.endMs)
    }
}
