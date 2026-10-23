package com.rerun.core.schedule

/** One cell in the guide grid. Filler is folded into the program before it. */
data class GuideBlock(val startMs: Long, val endMs: Long, val itemKey: String?)

/** Turns a channel's entries into guide cells, clipped to [fromMs, toMs). */
fun guideBlocks(entries: List<ScheduleEntry>, fromMs: Long, toMs: Long): List<GuideBlock> {
    val out = ArrayList<GuideBlock>()
    for (e in entries.sortedBy { it.startMs }) {
        if (e.endMs <= fromMs || e.startMs >= toMs) continue
        val s = maxOf(e.startMs, fromMs)
        val end = minOf(e.endMs, toMs)
        val last = out.lastOrNull()
        if (e.kind == EntryKind.FILLER && last != null && last.endMs == s) {
            out[out.size - 1] = last.copy(endMs = end)
        } else {
            out += GuideBlock(s, end, if (e.kind == EntryKind.PROGRAM) e.itemKey else null)
        }
    }
    return out
}
