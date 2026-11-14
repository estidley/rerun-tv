package com.rerun.core.schedule

import com.rerun.core.MIN
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class PaddingTest {
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun ceilOfExactBoundaryIsItself() {
        assertEquals(at("2026-10-23T10:00:00Z"), gridCeil(at("2026-10-23T10:00:00Z")))
        assertEquals(at("2026-10-23T10:15:00Z"), gridCeil(at("2026-10-23T10:15:00Z")))
    }

    @Test
    fun sevenMinuteEpisodePadsToFifteen() {
        val start = at("2026-10-23T10:00:00Z")
        assertEquals(at("2026-10-23T10:15:00Z"), gridCeil(start + 7 * MIN))
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
    fun floorAlignsToQuarterHour() {
        assertEquals(at("2026-10-23T21:45:00Z"), gridFloor(at("2026-10-23T21:47:13Z")))
        assertEquals(at("2026-10-23T21:15:00Z"), gridFloor(at("2026-10-23T21:29:59Z")))
    }

    @Test
    fun localMarksRespectNonHourOffsets() {
        val zone = ZoneId.of("Asia/Kathmandu")
        assertEquals(at("2026-10-23T04:15:00Z"), gridFloor(at("2026-10-23T04:29:59Z"), zone))
        assertEquals(at("2026-10-23T04:30:00Z"), gridCeil(at("2026-10-23T04:16:00Z"), zone))
        assertEquals(at("2026-10-23T04:15:00Z"), gridCeil(at("2026-10-23T04:15:00Z"), zone))
    }

    @Test
    fun chicagoSpringForwardSkipsTheMissingHour() {
        val zone = ZoneId.of("America/Chicago")
        assertEquals(at("2026-03-08T07:45:00Z"), gridFloor(at("2026-03-08T07:59:59Z"), zone))
        assertEquals(at("2026-03-08T08:00:00Z"), gridCeil(at("2026-03-08T07:59:59Z"), zone))
    }

    @Test
    fun chicagoFallBackRetainsMarksInBothOffsets() {
        val zone = ZoneId.of("America/Chicago")
        val firstOneFifty = at("2026-11-01T06:50:00Z")
        val secondOneTen = at("2026-11-01T07:10:00Z")
        assertEquals(at("2026-11-01T06:45:00Z"), gridFloor(firstOneFifty, zone))
        assertEquals(at("2026-11-01T07:00:00Z"), gridCeil(firstOneFifty, zone))
        assertEquals(at("2026-11-01T07:00:00Z"), gridFloor(secondOneTen, zone))
        assertEquals(at("2026-11-01T07:15:00Z"), gridCeil(secondOneTen, zone))
        assertEquals(at("2026-11-01T07:15:00Z"), gridFloor(at("2026-11-01T07:15:00Z"), zone))
        assertEquals(at("2026-11-01T07:15:00Z"), gridCeil(at("2026-11-01T07:15:00Z"), zone))
    }

    @Test
    fun gridIsFifteenMinutes() = assertEquals(15 * MIN, GRID_MS)
}
