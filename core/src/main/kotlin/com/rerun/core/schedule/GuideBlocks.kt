package com.rerun.core.schedule

/** One cell in the guide grid. Filler is folded into the program before it. A gap is Off Air. */
data class GuideBlock(
    val startMs: Long,
    val endMs: Long,
    val itemKey: String?,
    val offAir: Boolean = false,
    val title: String? = null,
)

/** Turns a channel's entries into guide cells, clipped to [fromMs, toMs). */
fun guideBlocks(entries: List<ScheduleEntry>, fromMs: Long, toMs: Long): List<GuideBlock> {
    if (fromMs >= toMs) return emptyList()
    val scheduled = ArrayList<GuideBlock>()
    for (e in entries.sortedBy { it.startMs }) {
        if (e.startMs >= toMs) continue
        val last = scheduled.lastOrNull()
        if (e.kind == EntryKind.FILLER && last != null && last.endMs == e.startMs) {
            scheduled[scheduled.size - 1] = last.copy(endMs = e.endMs)
        } else {
            scheduled += GuideBlock(e.startMs, e.endMs, if (e.kind == EntryKind.PROGRAM) e.itemKey else null)
        }
    }
    val out = ArrayList<GuideBlock>(scheduled.size + 2)
    var cursor = fromMs
    for (block in scheduled) {
        if (block.endMs <= fromMs) continue
        val b = block.copy(startMs = maxOf(block.startMs, fromMs), endMs = minOf(block.endMs, toMs))
        if (b.startMs > cursor) out += GuideBlock(cursor, b.startMs, null, offAir = true)
        out += b
        cursor = maxOf(cursor, b.endMs)
    }
    if (cursor < toMs) out += GuideBlock(cursor, toMs, null, offAir = true)
    return out
}

/** Groups continuous playback into hour-long station blocks. Gaps stay Off Air. */
fun hourlyGuideBlocks(entries: List<ScheduleEntry>, fromMs: Long, toMs: Long, title: String): List<GuideBlock> {
    val spans = ArrayList<GuideBlock>()
    for (block in guideBlocks(entries, fromMs, toMs)) {
        val previous = spans.lastOrNull()
        if (!block.offAir && previous != null && !previous.offAir && previous.endMs == block.startMs) {
            spans[spans.lastIndex] = previous.copy(endMs = block.endMs)
        } else {
            spans += block.copy(itemKey = null, title = if (block.offAir) null else title)
        }
    }
    val hourMs = 2 * GRID_MS
    return buildList {
        for (span in spans) {
            if (span.offAir) {
                add(span)
            } else {
                var start = span.startMs
                while (start < span.endMs) {
                    val end = minOf(span.endMs, (Math.floorDiv(start, hourMs) + 1) * hourMs)
                    add(span.copy(startMs = start, endMs = end))
                    start = end
                }
            }
        }
    }
}
