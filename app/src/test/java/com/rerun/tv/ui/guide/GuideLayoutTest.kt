package com.rerun.tv.ui.guide

import org.junit.Assert.*
import org.junit.Test

class GuideLayoutTest {
    @Test
    fun wholeRowsFillEveryAvailableHeight() {
        for (height in listOf(350f, 635f, 720f, 1715f)) {
            val layout = guideRows(height, 100, 0)
            assertTrue(layout.visibleCount > 0)
            assertEquals(height, layout.rowHeight * layout.visibleCount, 0.01f)
            assertTrue(layout.rowHeight >= 104f)
        }
    }

    @Test
    fun tallerDisplaysShowMoreRows() {
        assertTrue(guideRows(1400f, 100, 0).visibleCount > guideRows(700f, 100, 0).visibleCount)
    }

    @Test
    fun shortLineupsDoNotStretchIntoGiantRows() {
        val layout = guideRows(700f, 3, 0)
        assertEquals(3, layout.visibleCount)
        assertEquals(104f, layout.rowHeight, 0f)
        assertEquals(0, layout.firstIndex)
    }

    @Test
    fun focusedRowRemainsVisibleAtTopMiddleAndBottom() {
        for (height in listOf(350f, 700f, 1500f)) {
            for (focus in 0 until 100) {
                val layout = guideRows(height, 100, focus)
                assertTrue(focus in layout.firstIndex until layout.firstIndex + layout.visibleCount)
                assertTrue(layout.firstIndex + layout.visibleCount <= 100)
            }
        }
        assertEquals(0, guideRows(700f, 100, -1).firstIndex)
    }

    @Test
    fun emptyOrSmallViewportsRemainBounded() {
        val empty = guideRows(700f, 0, 0)
        assertEquals(0, empty.visibleCount)
        assertEquals(0f, empty.rowHeight, 0f)
        assertEquals(20f, guideRows(20f, 5, 0).rowHeight, 0f)
        assertEquals(0f, guideRows(-1f, 5, 0).rowHeight, 0f)
    }

    @Test
    fun channelNamesWrapWhenRowsHaveRoom() {
        assertEquals(1, guideRows(40f, 100, 0).channelNameLines)
        assertEquals(2, guideRows(700f, 100, 0).channelNameLines)
        assertEquals(2, guideRows(700f, 3, 0).channelNameLines)
    }

    @Test
    fun densityChangesVisibleRowsWhilePreservingFillAndFocus() {
        val compact = guideRows(700f, 100, 50, GuideDensity.COMPACT)
        val default = guideRows(700f, 100, 50, GuideDensity.DEFAULT)
        val large = guideRows(700f, 100, 50, GuideDensity.LARGE)
        assertTrue(compact.visibleCount > default.visibleCount)
        assertTrue(default.visibleCount > large.visibleCount)
        for (layout in listOf(compact, default, large)) {
            assertEquals(700f, layout.rowHeight * layout.visibleCount, 0.01f)
            assertTrue(50 in layout.firstIndex until layout.firstIndex + layout.visibleCount)
        }
        assertTrue(GuideDensity.LARGE.detailHeight > GuideDensity.DEFAULT.detailHeight)
        assertEquals("COMPACT", GuideDensity.fromLegacy("COMFORTABLE"))
        assertEquals("DEFAULT", GuideDensity.fromLegacy("LARGE"))
        assertEquals(GuideDensity.DEFAULT, GuideDensity.from("invalid"))
    }
}
