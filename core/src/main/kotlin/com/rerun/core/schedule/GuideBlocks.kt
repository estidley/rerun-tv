package com.rerun.core.schedule

/** One cell in the guide grid. Filler is folded into the program before it. A gap is Off Air. */
data class GuideBlock(
    val startMs: Long,
    val endMs: Long,
    val itemKey: String?,
    val offAir: Boolean = false,
)

/** Turns a channel's entries into guide cells, clipped to [fromMs, toMs). */
fun guideBlocks(entries: List<ScheduleEntry>, fromMs: Long, toMs: Long): List<GuideBlock> {
    val scheduled = ArrayList<GuideBlock>()
    for (e in entries.sortedBy { it.startMs }) {
        if (e.endMs <= fromMs || e.startMs >= toMs) continue
        val s = maxOf(e.startMs, fromMs)
        val end = minOf(e.endMs, toMs)
        val last = scheduled.lastOrNull()
        if (e.kind == EntryKind.FILLER && last != null && last.endMs == s) {
            scheduled[scheduled.size - 1] = last.copy(endMs = end)
        } else {
            scheduled += GuideBlock(s, end, if (e.kind == EntryKind.PROGRAM) e.itemKey else null)
        }
    }
    if (fromMs >= toMs) return scheduled
    val out = ArrayList<GuideBlock>(scheduled.size + 2)
    var cursor = fromMs
    for (b in scheduled) {
        if (b.startMs > cursor) out += GuideBlock(cursor, b.startMs, null, offAir = true)
        out += b
        cursor = maxOf(cursor, b.endMs)
    }
    if (cursor < toMs) out += GuideBlock(cursor, toMs, null, offAir = true)
    return out
}
