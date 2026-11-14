package com.rerun.core.schedule

/** One cell in the guide grid. Filler is folded into the program before it. A gap is Off Air. */
data class GuideBlock(
    val startMs: Long,
    val endMs: Long,
    val itemKey: String?,
    val offAir: Boolean = false,
    val title: String? = null,
)

/** Guide cells overlapping [fromMs, toMs), retaining scheduled boundaries for program details. */
fun guideBlocks(entries: List<ScheduleEntry>, fromMs: Long, toMs: Long): List<GuideBlock> {
    if (fromMs >= toMs) return emptyList()
    val scheduled = ArrayList<GuideBlock>()
    for (e in entries.sortedBy { it.startMs }) {
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
        if (block.endMs <= fromMs || block.startMs >= toMs) continue
        if (block.startMs > cursor) out += GuideBlock(cursor, block.startMs, null, offAir = true)
        out += block
        cursor = maxOf(cursor, block.endMs)
    }
    if (cursor < toMs) out += GuideBlock(cursor, toMs, null, offAir = true)
    return out
}

/** Groups continuous playback into hour-long station blocks. Gaps stay Off Air. */
fun hourlyGuideBlocks(entries: List<ScheduleEntry>, fromMs: Long, toMs: Long, title: String): List<GuideBlock> {
    val spans = ArrayList<GuideBlock>()
    for (scheduled in guideBlocks(entries, fromMs, toMs)) {
        val block = scheduled.copy(startMs = maxOf(scheduled.startMs, fromMs), endMs = minOf(scheduled.endMs, toMs))
        val previous = spans.lastOrNull()
        if (!block.offAir && previous != null && !previous.offAir && previous.endMs == block.startMs) {
            spans[spans.lastIndex] = previous.copy(endMs = block.endMs)
        } else {
            spans += block.copy(itemKey = null, title = if (block.offAir) null else title)
        }
    }
    val hourMs = 3_600_000L
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
