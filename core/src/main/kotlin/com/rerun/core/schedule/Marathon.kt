package com.rerun.core.schedule

import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.model.matchKey

/**
 * Marathon play order: every item once per loop, then start over.
 *
 * Episodes group into a *series* by TMDB id, else by [matchKey] of the show title, so two Plex
 * copies of one show (dub + sub libraries, a re-added show) count once. Within a series one item
 * airs per (season, episode), taken from the copy with the most episodes (tie: lowest show key).
 * Series run oldest first; inside a series regular seasons air in order and season 0 (specials)
 * airs last. Movies, if any are in the pool, follow every series, by year.
 *
 * The run keeps its place by item, not by index: the next pick is whatever follows the last
 * scheduled item (see [nextIndex]), so an episode added or removed elsewhere never shifts it.
 */
object MarathonOrder {
    /** Pointer flag: replay `recent.last()` itself instead of moving past it (set by [resumeAt]). */
    const val RESUME_KEY = "__marathon_resume"

    class Plan(val items: List<MediaItem>, private val slotOf: Map<String, Int>) {
        /** Position of [key] in the loop; dropped duplicate copies resolve to their kept slot. */
        fun indexOf(key: String): Int? = slotOf[key]
    }

    fun ordered(pool: List<MediaItem>): List<MediaItem> = plan(pool).items

    fun plan(pool: List<MediaItem>): Plan {
        val episodes = pool.filter { it.kind != MediaKind.MOVIE }
        val movies = pool.filter { it.kind == MediaKind.MOVIE }
        val ordered = ArrayList<MediaItem>()
        val aliases = HashMap<String, String>() // dropped copy key -> kept item key

        val bySeries = episodes.groupBy { seriesId(it) }
        val seriesOrder = bySeries.entries.sortedWith(
            compareBy<Map.Entry<String, List<MediaItem>>> { (_, eps) -> eps.mapNotNull { it.year }.minOrNull() ?: Int.MAX_VALUE }
                .thenBy { (_, eps) -> eps.minOf { matchKey(it.showTitle ?: it.title) } }
                .thenBy { it.key },
        )
        for ((_, eps) in seriesOrder) {
            val copySize = eps.groupingBy { it.showKey ?: it.key }.eachCount()
            val preferred = compareByDescending<MediaItem> { copySize.getValue(it.showKey ?: it.key) }
                .thenBy { it.showKey ?: it.key }.thenBy { it.key }
            val slots = eps.groupBy { (it.season ?: 1) to (it.episode ?: 0) }
            val kept = slots.values.map { copies ->
                val sorted = copies.sortedWith(preferred)
                sorted.drop(1).forEach { aliases[it.key] = sorted.first().key }
                sorted.first()
            }
            ordered += kept.sortedWith(
                compareBy<MediaItem> { if ((it.season ?: 1) == 0) 1 else 0 }
                    .thenBy { it.season ?: 1 }.thenBy { it.episode ?: 0 }.thenBy { it.key },
            )
        }
        ordered += movies.sortedWith(compareBy<MediaItem> { it.year ?: Int.MAX_VALUE }.thenBy { matchKey(it.title) }.thenBy { it.key })

        val slotOf = HashMap<String, Int>(ordered.size + aliases.size)
        ordered.forEachIndexed { i, item -> slotOf[item.key] = i }
        for ((dropped, kept) in aliases) slotOf[dropped] = slotOf.getValue(kept)
        return Plan(ordered, slotOf)
    }

    /**
     * Index of the next item to air. Anchors on [lastProgramKey] (the last program still in the
     * schedule), else on the newest entry of [recent]. If the anchor is gone from the pool, walks
     * back through what aired before it. Nothing to go on → the start of the loop.
     */
    fun nextIndex(plan: Plan, state: ChannelState, lastProgramKey: String?): Int {
        val n = plan.items.size
        if (n == 0) return 0
        if (lastProgramKey == null && state.episodePointers[RESUME_KEY] == 1) {
            state.recent.lastOrNull()?.let(plan::indexOf)?.let { return it }
        }
        val history = state.recent
        val anchorAt = lastProgramKey?.let { history.lastIndexOf(it) }?.takeIf { it >= 0 }
        lastProgramKey?.let(plan::indexOf)?.let { return (it + 1) % n }
        val searchEnd = anchorAt ?: history.size
        for (i in searchEnd - 1 downTo 0) {
            plan.indexOf(history[i])?.let { return (it + 1) % n }
        }
        return 0
    }

    /** State that makes the next extension start *at* [key] (used when a schedule is wiped). */
    fun resumeAt(state: ChannelState, key: String): ChannelState =
        state.copy(recent = (state.recent + key).takeLast(200), episodePointers = state.episodePointers + (RESUME_KEY to 1))

    /** Drops the resume flag once the run has picked up again. */
    fun settled(pointers: Map<String, Int>): Map<String, Int> = pointers - RESUME_KEY

    private fun seriesId(item: MediaItem): String =
        item.tmdbId?.let { "tmdb:$it" } ?: "title:${matchKey(item.showTitle ?: item.title)}"
}
