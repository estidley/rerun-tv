package com.rerun.core.schedule

import com.rerun.core.model.MediaItem
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

    @Test
    fun indexedPlanMatchesADirectWalkForMixedDurations() {
        val mixed = listOf(
            movie("c", durationMs = 10_000),
            movie("a", durationMs = 40_000),
            movie("b", durationMs = 15_000),
            movie("d", durationMs = 5_000),
        )
        val breakEntry = ScheduleEntry("mixed-break", 5_000, 130_000, EntryKind.FILLER, null)
        var time = breakEntry.startMs
        while (time < breakEntry.endMs) {
            val expected = directWalk(breakEntry, mixed, time)
            assertEquals(expected, adFiller(breakEntry, mixed.reversed(), time))
            if (expected == null) break
            time = expected.endMs
        }
    }

    @Test
    fun longBreakLookupResumesInsteadOfReplayingThePrefix() {
        val ads = (1..200).map { movie("ad${it.toString().padStart(4, '0')}", durationMs = 15_000) }
        val breakEntry = ScheduleEntry("long-break", 0, 150 * 60_000, EntryKind.FILLER, null)
        val mid = adFiller(breakEntry, ads, 140 * 60_000)!!
        val firstSteps = cachedBreakLastSteps(breakEntry.channelId, breakEntry.startMs, breakEntry.endMs)
        assertTrue(firstSteps != null && firstSteps > 100)
        assertEquals(mid, adFiller(breakEntry, ads, mid.startMs))
        assertEquals(0, cachedBreakLastSteps(breakEntry.channelId, breakEntry.startMs, breakEntry.endMs))
        val next = adFiller(breakEntry, ads.reversed(), mid.endMs)!!
        assertEquals(1, cachedBreakLastSteps(breakEntry.channelId, breakEntry.startMs, breakEntry.endMs))
        assertEquals(mid.endMs, next.startMs)
        val rewound = adFiller(breakEntry, ads, 0)
        assertEquals(directWalk(breakEntry, ads, 0), rewound)
        assertEquals(1, cachedBreakLastSteps(breakEntry.channelId, breakEntry.startMs, breakEntry.endMs))
    }

    private fun directWalk(entry: ScheduleEntry, catalog: List<MediaItem>, nowMs: Long): AdSlot? {
        if (entry.kind != EntryKind.FILLER || nowMs !in entry.startMs until entry.endMs) return null
        val choices = catalog.filter { it.durationMs > 0 }.sortedBy { it.key }
        val random = kotlin.random.Random(entry.channelId.hashCode().toLong() xor entry.startMs)
        var start = entry.startMs
        var previous: String? = null
        while (start < entry.endMs) {
            val fitting = choices.filter { it.durationMs <= entry.endMs - start }
            if (fitting.isEmpty()) return null
            val eligible = fitting.filter { it.key != previous }.ifEmpty { fitting }
            val item = eligible[random.nextInt(eligible.size)]
            val end = start + item.durationMs
            if (nowMs < end) return AdSlot(item, start, end)
            start = end
            previous = item.key
        }
        return null
    }
}
