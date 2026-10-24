package com.rerun.core.schedule

import com.rerun.core.MIN
import kotlin.test.Test
import kotlin.test.assertEquals

class GuideBlocksTest {
    private val t0 = 1_800_000_000_000L - (1_800_000_000_000L % GRID_MS)
    private fun p(s: Long, e: Long, k: String) = ScheduleEntry("c", t0 + s * MIN, t0 + e * MIN, EntryKind.PROGRAM, k)
    private fun f(s: Long, e: Long) = ScheduleEntry("c", t0 + s * MIN, t0 + e * MIN, EntryKind.FILLER, null)

    @Test
    fun fillerMergesIntoPreviousProgram() {
        val blocks = guideBlocks(listOf(p(0, 22, "a"), f(22, 30), p(30, 52, "b"), f(52, 60)), t0, t0 + 60 * MIN)
        assertEquals(listOf(GuideBlock(t0, t0 + 30 * MIN, "a"), GuideBlock(t0 + 30 * MIN, t0 + 60 * MIN, "b")), blocks)
    }

    @Test
    fun clipsToWindow() {
        val blocks = guideBlocks(listOf(p(-60, 47, "movie"), f(47, 60), p(60, 82, "ep")), t0, t0 + 70 * MIN)
        assertEquals(listOf(GuideBlock(t0, t0 + 60 * MIN, "movie"), GuideBlock(t0 + 60 * MIN, t0 + 70 * MIN, "ep")), blocks)
    }

    @Test
    fun leadingFillerBecomesUntitledBlock() {
        val blocks = guideBlocks(listOf(f(-5, 10), p(10, 30, "x")), t0, t0 + 30 * MIN)
        assertEquals(listOf(GuideBlock(t0, t0 + 10 * MIN, null), GuideBlock(t0 + 10 * MIN, t0 + 30 * MIN, "x")), blocks)
    }

    @Test
    fun emptyWindowIsOffAir() {
        assertEquals(listOf(GuideBlock(t0, t0 + 30 * MIN, null, offAir = true)), guideBlocks(emptyList(), t0, t0 + 30 * MIN))
    }

    @Test
    fun holeBetweenProgramsIsOffAir() {
        val blocks = guideBlocks(listOf(p(0, 20, "a"), p(40, 60, "b")), t0, t0 + 60 * MIN)
        assertEquals(
            listOf(
                GuideBlock(t0, t0 + 20 * MIN, "a"),
                GuideBlock(t0 + 20 * MIN, t0 + 40 * MIN, null, offAir = true),
                GuideBlock(t0 + 40 * MIN, t0 + 60 * MIN, "b"),
            ),
            blocks,
        )
    }

    @Test
    fun musicCombinesSongsIntoHourlyBlocksWithoutChangingTheSchedule() {
        val start = Math.floorDiv(t0, 60 * MIN) * 60 * MIN
        val entries = (0 until 60).map {
            ScheduleEntry("music:blues", start + it * 3 * MIN, start + (it + 1) * 3 * MIN, EntryKind.PROGRAM, "song:$it")
        }
        val blocks = musicGuideBlocks(entries, start, start + 180 * MIN, "Blues Music")
        assertEquals((0 until 3).map {
            GuideBlock(start + it * 60 * MIN, start + (it + 1) * 60 * MIN, null, title = "Blues Music")
        }, blocks)
        assertEquals(60, entries.size)
        assertEquals("song:0", entries.first().itemKey)
    }

    @Test
    fun hourlyMusicClipsAtWindowEdgesAndPreservesOffAirGaps() {
        val start = Math.floorDiv(t0, 60 * MIN) * 60 * MIN
        val entries = listOf(
            ScheduleEntry("music", start, start + 65 * MIN, EntryKind.PROGRAM, "a"),
            ScheduleEntry("music", start + 75 * MIN, start + 120 * MIN, EntryKind.PROGRAM, "b"),
        )
        assertEquals(listOf(
            GuideBlock(start + 30 * MIN, start + 60 * MIN, null, title = "Classic Country Music"),
            GuideBlock(start + 60 * MIN, start + 65 * MIN, null, title = "Classic Country Music"),
            GuideBlock(start + 65 * MIN, start + 75 * MIN, null, offAir = true),
            GuideBlock(start + 75 * MIN, start + 90 * MIN, null, title = "Classic Country Music"),
        ), musicGuideBlocks(entries, start + 30 * MIN, start + 90 * MIN, "Classic Country Music"))
        assertEquals(
            listOf(GuideBlock(start, start + 60 * MIN, null, offAir = true)),
            musicGuideBlocks(emptyList(), start, start + 60 * MIN, "Blues Music"),
        )
        assertEquals(emptyList(), musicGuideBlocks(entries, start, start, "Blues Music"))
    }
}
