package com.rerun.tv.data

import com.rerun.core.schedule.SharedScheduleCode
import com.rerun.core.schedule.ScheduleEntry
import com.rerun.core.schedule.AdSlot
import com.rerun.core.model.MediaItem
import kotlin.random.Random

data class SharedPilotStatus(
    val code: String? = null,
    val effectiveFromMs: Long = 0,
    val channels: List<String> = emptyList(),
)

internal const val SHARED_BLOCK_MS = 3 * 3_600_000L
internal const val SHARED_HORIZON_MS = 48 * 3_600_000L
internal fun nextSharedBoundary(nowMs: Long): Long =
    (Math.floorDiv(nowMs, SHARED_BLOCK_MS) + 1) * SHARED_BLOCK_MS

internal fun validateSharedCode(text: String, nowMs: Long): SharedScheduleCode {
    require(text.length <= 128) { "Shared code input must be at most 128 characters" }
    val recipe = requireNotNull(SharedScheduleCode.decode(text.trim())) { "Invalid shared scheduling code" }
    require(recipe.anchorDay <= Math.floorDiv(nowMs, 86_400_000L)) {
        "This code starts on a future UTC day"
    }

    return recipe
}

internal fun groupedSharedCode(code: String): String = code.chunked(4).joinToString("-")

internal class SharedAdPlanWork {
    var bookedSlots = 0
    var durationComparisons = 0
}

/** One forward pass; duration indexing avoids replaying the break prefix for every clip. */
internal fun bookSharedAds(
    entry: ScheduleEntry,
    catalog: List<MediaItem>,
    random: Random,
    work: SharedAdPlanWork = SharedAdPlanWork(),
): List<AdSlot> {
    val ads = catalog.filter { it.durationMs > 0 }.distinctBy { it.key }
        .sortedWith(compareBy<MediaItem> { it.durationMs }.thenBy { it.key })
    val result = ArrayList<AdSlot>()
    var start = entry.startMs
    var previous = -1
    while (start < entry.endMs) {
        val remaining = entry.endMs - start
        var low = 0
        var high = ads.size
        while (low < high) {
            work.durationComparisons++
            val middle = (low + high) ushr 1
            if (ads[middle].durationMs <= remaining) low = middle + 1 else high = middle
        }
        if (low == 0) break
        val exclude = low > 1 && previous in 0 until low
        var index = random.nextInt(if (exclude) low - 1 else low)
        if (exclude && index >= previous) index++
        val item = ads[index]
        val end = start + item.durationMs
        result += AdSlot(item, start, end)
        work.bookedSlots++
        start = end
        previous = index
    }
    return result
}
