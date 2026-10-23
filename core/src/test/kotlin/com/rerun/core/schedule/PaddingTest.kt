package com.rerun.core.schedule

import com.rerun.core.MIN
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class PaddingTest {
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun ceilOfExactBoundaryIsItself() {
        assertEquals(at("2026-10-23T10:00:00Z"), gridCeil(at("2026-10-23T10:00:00Z")))
        assertEquals(at("2026-10-23T10:30:00Z"), gridCeil(at("2026-10-23T10:30:00Z")))
    }

    @Test
    fun twentyTwoMinuteEpisodePadsToThirty() {
        val start = at("2026-10-23T10:00:00Z")
        assertEquals(at("2026-10-23T10:30:00Z"), gridCeil(start + 22 * MIN))
    }

    @Test
    fun oneHourFortySevenPadsToTwoHours() {
        val start = at("2026-10-23T10:00:00Z")
        assertEquals(at("2026-10-23T12:00:00Z"), gridCeil(start + 107 * MIN))
    }

    @Test
    fun floorAlignsToHalfHour() {
        assertEquals(at("2026-10-23T21:30:00Z"), gridFloor(at("2026-10-23T21:47:13Z")))
        assertEquals(at("2026-10-23T21:00:00Z"), gridFloor(at("2026-10-23T21:29:59Z")))
    }

    @Test
    fun gridIsThirtyMinutes() = assertEquals(30 * MIN, GRID_MS)
}
