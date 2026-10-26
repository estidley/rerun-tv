package com.rerun.tv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockWatchTest {
    private var wall = 1_000_000L
    private var elapsed = 5_000L
    private var zone = "UTC"
    private val watch = ClockWatch({ wall }, { elapsed }, { zone })

    @Test
    fun steadyTimeIsNotAChange() {
        wall += 30_000
        elapsed += 30_001
        assertFalse(watch.changed())
    }

    @Test
    fun wallClockJumpIsDetectedOnceThenBaselined() {
        wall += 3_600_000
        elapsed += 30_000
        assertTrue(watch.changed())
        wall += 30_000
        elapsed += 30_000
        assertFalse(watch.changed())
    }

    @Test
    fun zoneChangeIsDetected() {
        zone = "America/Denver"
        assertTrue(watch.changed())
        assertFalse(watch.changed())
    }
}
