package com.rerun.core.schedule

/** Where a channel is "right now": the entry on air and how far into it we are. */
data class Located(val entry: ScheduleEntry, val offsetMs: Long, val next: ScheduleEntry?)

/** A program with less than this left is skipped; you land on whatever comes next. */
const val TAIL_SKIP_MS = 5_000L

private const val TOP_UP_BELOW_MS = 24 * 3_600_000L

/** Finds the entry covering [nowMs] in start-sorted [entries]; null if none does. */
fun locate(entries: List<ScheduleEntry>, nowMs: Long, skipTail: Boolean = true): Located? {
    var lo = 0
    var hi = entries.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val e = entries[mid]
        when {
            nowMs < e.startMs -> hi = mid - 1
            nowMs >= e.endMs -> lo = mid + 1
            else -> { found = mid; break }
        }
    }
    if (found < 0) return null
    val e = entries[found]
    val next = entries.getOrNull(found + 1)
    if (skipTail && e.kind == EntryKind.PROGRAM && e.endMs - nowMs < TAIL_SKIP_MS && next != null) {
        return Located(next, 0, entries.getOrNull(found + 2))
    }
    return Located(e, nowMs - e.startMs, next)
}

/** True when the channel has less than a day of schedule left (or none at all). */
fun needsTopUp(lastEndMs: Long?, nowMs: Long): Boolean =
    lastEndMs == null || lastEndMs - nowMs < TOP_UP_BELOW_MS
