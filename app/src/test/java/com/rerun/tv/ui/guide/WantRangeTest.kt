package com.rerun.tv.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WantRangeTest {
    @Test
    fun firstRangeAddsAMarginAroundTheViewport() {
        assertEquals(0..11, wantRange(IntRange.EMPTY, 0, 6, 100))
        assertEquals(14..31, wantRange(IntRange.EMPTY, 20, 6, 100))
    }

    @Test
    fun scrollingInsideTheMarginKeepsTheRange() {
        val range = wantRange(IntRange.EMPTY, 20, 6, 100)
        assertEquals(range, wantRange(range, 21, 6, 100))
        assertTrue(wantRange(range, 40, 6, 100).contains(40))
    }

    @Test
    fun smallListsAreFullyCovered() {
        assertEquals(0..2, wantRange(IntRange.EMPTY, 0, 3, 3))
    }
}
