package com.rerun.core.schedule

import com.rerun.core.model.MediaItem
import kotlin.random.Random

data class AdSlot(val item: MediaItem, val startMs: Long, val endMs: Long)

/** The same break and catalog always yield the same clips, including when tuning in midway. */
fun adFiller(entry: ScheduleEntry, catalog: List<MediaItem>, nowMs: Long): AdSlot? {
    if (entry.kind != EntryKind.FILLER || nowMs !in entry.startMs until entry.endMs) return null
    val ads = catalog.filter { it.durationMs > 0 }.sortedBy { it.key }
    val random = Random(entry.channelId.hashCode().toLong() xor entry.startMs)
    var start = entry.startMs
    var previous: String? = null
    while (start < entry.endMs) {
        val fitting = ads.filter { it.durationMs <= entry.endMs - start }
        if (fitting.isEmpty()) return null
        val choices = fitting.filter { it.key != previous }.ifEmpty { fitting }
        val item = choices[random.nextInt(choices.size)]
        val end = start + item.durationMs
        if (nowMs < end) return AdSlot(item, start, end)
        start = end
        previous = item.key
    }
    return null
}
