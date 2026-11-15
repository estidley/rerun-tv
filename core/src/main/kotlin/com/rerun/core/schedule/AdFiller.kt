package com.rerun.core.schedule

import com.rerun.core.model.AdBreakLength
import com.rerun.core.model.MediaItem
import kotlin.random.Random

data class AdSlot(val item: MediaItem, val startMs: Long, val endMs: Long)

private const val PLAN_CACHE_LIMIT = 4

private data class PlanKey(
    val channelId: String,
    val startMs: Long,
    val endMs: Long,
    val breakLength: AdBreakLength?,
)

private val plansLock = Any()
private val plans = LinkedHashMap<PlanKey, AdBreakPlan>(PLAN_CACHE_LIMIT, 0.75f, true)

/**
 * The same break and catalog always yield the same clips, including when tuning in midway.
 * A bounded cache resumes a long retained break instead of replaying its prefix on every rollover.
 */
fun adFiller(
    entry: ScheduleEntry,
    catalog: List<MediaItem>,
    nowMs: Long,
    breakLength: AdBreakLength? = null,
): AdSlot? {
    if (entry.kind != EntryKind.FILLER || nowMs !in entry.startMs until entry.endMs) return null
    val plan = synchronized(plansLock) {
        val key = PlanKey(entry.channelId, entry.startMs, entry.endMs, breakLength)
        val existing = plans[key]
        if (existing != null && existing.matches(catalog)) existing
        else AdBreakPlan(entry, AdBreakCatalog(catalog), breakLength).also {
            plans[key] = it
            if (plans.size > PLAN_CACHE_LIMIT) {
                val eldest = plans.entries.iterator()
                eldest.next()
                eldest.remove()
            }
        }
    }
    return synchronized(plan) { plan.slot(nowMs) }
}

/** Selects a variable-length break without aligning its end to a time boundary. */
fun planAdBreak(
    channelId: String,
    startMs: Long,
    length: AdBreakLength,
    catalog: List<MediaItem>,
): List<AdSlot> = AdBreakCatalog(catalog).plan(channelId, startMs, length)

/** Steps taken by the most recent cached lookup for this break, or null when nothing is cached. */
internal fun cachedBreakLastSteps(channelId: String, startMs: Long, endMs: Long): Int? =
    synchronized(plansLock) {
        plans[PlanKey(channelId, startMs, endMs, null)]?.let { plan -> synchronized(plan) { plan.lastSteps } }
    }

internal class AdBreakCatalog(catalog: List<MediaItem>) {
    val ads = catalog.filter { it.durationMs > 0 }.sortedBy { it.key }
    val maxDuration = ads.maxOfOrNull { it.durationMs } ?: 0L
    val uniqueKeys = ads.map { it.key }.toHashSet().size == ads.size
    private val fingerprint = ads.map { it.key to it.durationMs }

    fun matches(catalog: List<MediaItem>): Boolean {
        val next = catalog.filter { it.durationMs > 0 }.map { it.key to it.durationMs }.sortedBy { it.first }
        return next == fingerprint
    }

    fun plan(channelId: String, startMs: Long, length: AdBreakLength): List<AdSlot> {
        val entry = ScheduleEntry(channelId, startMs, startMs + length.targetMs, EntryKind.FILLER, null)
        val plan = AdBreakPlan(entry, this, length)
        val slots = ArrayList<AdSlot>(length.maxAds)
        var cursor = startMs
        while (cursor < entry.endMs) {
            val slot = plan.slot(cursor) ?: break
            slots += slot
            cursor = slot.endMs
        }
        return slots
    }
}

/**
 * One deterministic walk of a filler break. The cursor stays on the current clip so a later
 * lookup continues forward, and a backward lookup rebuilds from the break start.
 */
internal class AdBreakPlan(
    private val entry: ScheduleEntry,
    private val catalog: AdBreakCatalog,
    breakLength: AdBreakLength? = null,
) {
    private val ads = catalog.ads
    private val maxDuration = catalog.maxDuration
    private val uniqueKeys = catalog.uniqueKeys
    private val seed = entry.channelId.hashCode().toLong() xor entry.startMs
    private val selectionEndMs = entry.startMs + (breakLength?.targetMs ?: entry.endMs - entry.startMs)
    private val maxAds = breakLength?.maxAds ?: Int.MAX_VALUE
    private var random = Random(seed)
    private var cursor = entry.startMs
    private var previousIndex = -1
    private var previousKey: String? = null
    private var current: AdSlot? = null
    private var clipCount = 0
    var lastSteps: Int = 0
        private set

    fun matches(catalog: List<MediaItem>): Boolean = this.catalog.matches(catalog)

    fun slot(nowMs: Long): AdSlot? {
        if (entry.kind != EntryKind.FILLER || nowMs !in entry.startMs until entry.endMs) return null
        if (ads.isEmpty()) return null
        var steps = 0
        val covered = current
        if (covered != null && nowMs < covered.startMs) reset()
        var slot = current
        while (slot == null || nowMs >= slot.endMs) {
            slot = advance() ?: run {
                lastSteps = steps
                return null
            }
            steps++
        }
        lastSteps = steps
        return slot
    }

    private fun reset() {
        random = Random(seed)
        cursor = entry.startMs
        previousIndex = -1
        previousKey = null
        current = null
        clipCount = 0
    }

    private fun advance(): AdSlot? {
        if (cursor >= entry.endMs || cursor >= selectionEndMs || clipCount >= maxAds) return null
        val pick = pick(selectionEndMs - cursor) ?: return null
        val end = cursor + pick.item.durationMs
        val slot = AdSlot(pick.item, cursor, end)
        current = slot
        cursor = end
        clipCount++
        previousIndex = pick.index
        previousKey = pick.item.key
        return slot
    }

    private data class Pick(val item: MediaItem, val index: Int)

    private fun pick(remaining: Long): Pick? {
        if (ads.isEmpty()) return null
        if (uniqueKeys && remaining >= maxDuration) {
            val exclude = previousIndex >= 0 && ads.size > 1
            var index = random.nextInt(if (exclude) ads.size - 1 else ads.size)
            if (exclude && index >= previousIndex) index++
            return Pick(ads[index], index)
        }
        val fitting = ArrayList<Int>(ads.size)
        for (i in ads.indices) if (ads[i].durationMs <= remaining) fitting.add(i)
        if (fitting.isEmpty()) return null
        val choices = if (previousKey == null) fitting
            else fitting.filter { ads[it].key != previousKey }.ifEmpty { fitting }
        val index = choices[random.nextInt(choices.size)]
        return Pick(ads[index], index)
    }
}
