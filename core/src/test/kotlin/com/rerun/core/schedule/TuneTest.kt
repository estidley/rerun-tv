package com.rerun.core.schedule

import com.rerun.core.HOUR
import com.rerun.core.MIN
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TuneTest {
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private val t21 = at("2026-10-23T21:00:00Z")
    private val entries = listOf(
        ScheduleEntry("c", t21, t21 + 107 * MIN, EntryKind.PROGRAM, "movie"),
        ScheduleEntry("c", t21 + 107 * MIN, t21 + 120 * MIN, EntryKind.FILLER, null),
        ScheduleEntry("c", t21 + 120 * MIN, t21 + 142 * MIN, EntryKind.PROGRAM, "ep"),
    )

    @Test
    fun midProgramOffset() {
        val loc = assertNotNull(locate(entries, t21 + 47 * MIN))
        assertEquals("movie", loc.entry.itemKey)
        assertEquals(47 * MIN, loc.offsetMs)
        assertEquals(EntryKind.FILLER, loc.next?.kind)
    }

    @Test
    fun tailSkip() {
        val loc = assertNotNull(locate(entries, t21 + 107 * MIN - 3_000))
        assertEquals(EntryKind.FILLER, loc.entry.kind)
        assertEquals(0L, loc.offsetMs)
    }

    @Test
    fun fillerIsNotTailSkipped() {
        val loc = assertNotNull(locate(entries, t21 + 120 * MIN - 2_000))
        assertEquals(EntryKind.FILLER, loc.entry.kind)
        assertEquals(13 * MIN - 2_000, loc.offsetMs)
    }

    @Test
    fun nowPastHorizonReturnsNullAndNeedsTopUp() {
        val late = t21 + 5 * 24 * HOUR
        assertNull(locate(entries, late))
        assertTrue(needsTopUp(entries.last().endMs, late))
        assertTrue(needsTopUp(null, late))
    }

    @Test
    fun beforeFirstEntryReturnsNull() = assertNull(locate(entries, t21 - 1))

    @Test
    fun exactBoundaryPicksNewEntry() {
        val loc = assertNotNull(locate(entries, t21 + 120 * MIN))
        assertEquals("ep", loc.entry.itemKey)
        assertEquals(0L, loc.offsetMs)
        assertNull(loc.next)
    }

    @Test
    fun topUpOnlyWhenUnderADayLeft() {
        assertFalse(needsTopUp(t21 + 30 * HOUR, t21))
        assertTrue(needsTopUp(t21 + 23 * HOUR, t21))
    }
}
