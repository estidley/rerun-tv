package com.rerun.core.schedule

import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.model.PlayOrder
import com.rerun.core.resolve.Resolver
import java.time.Instant
import java.time.ZoneId
import java.util.Random

data class Extension(val entries: List<ScheduleEntry>, val state: ChannelState)

/**
 * Writes a channel's schedule forward. Never touches entries that already exist: it only
 * appends from [lastEndMs] (or restarts at the current half-hour if that's in the past).
 *
 * Movies are dealt from a shuffled deck (no repeats within a deck); shows use a cyclic
 * shuffle (random show, weighted by episode count, episodes in order). Every program is
 * padded to the next :00/:30 with a FILLER entry. Music stations play back to back.
 * Chronology channels use one ordered deck across movies and episodes instead of the movie rotation.
 */
class Scheduler(private val zone: ZoneId) {

    fun extend(
        def: ChannelDef,
        pool: List<MediaItem>,
        state: ChannelState,
        lastEndMs: Long?,
        nowMs: Long,
        horizonMs: Long = 48 * 3_600_000L,
        backToBack: Boolean = def.kind == ChannelKind.MUSIC,
    ): Extension {
        if (pool.isEmpty()) return Extension(emptyList(), state)
        val items = pool.sortedBy { it.key } // input order must not change the schedule
        val music = def.kind == ChannelKind.MUSIC
        val base = Candidates(items.filter { Resolver.inBase(def, it) }.ifEmpty { items }, music)
        val blockSets = def.blocks.map { b ->
            Candidates(items.filter { b.rules.matches(it, if (b.rules.networks.isEmpty()) emptySet() else Resolver.networksOf(it)) }, music)
        }

        val floor = gridFloor(nowMs)
        var cursor = if (lastEndMs == null || lastEndMs < floor) floor else lastEndMs
        val end = nowMs + horizonMs
        val run = Run(state)
        val out = ArrayList<ScheduleEntry>()

        while (cursor < end) {
            val local = Instant.ofEpochMilli(cursor).atZone(zone)
            val bi = def.blocks.indexOfFirst { it.activeAt(local) }
            val candidates = if (bi >= 0 && blockSets[bi].items.isNotEmpty()) blockSets[bi] else base
            val item = run.pick(def, candidates)

            val length = item.durationMs.coerceAtLeast(1L)
            val progEnd = cursor + length
            val slotEnd = if (backToBack) progEnd else gridCeil(progEnd)
            out += ScheduleEntry(def.id, cursor, progEnd, EntryKind.PROGRAM, item.key)
            if (!backToBack && slotEnd > progEnd) out += ScheduleEntry(def.id, progEnd, slotEnd, EntryKind.FILLER, null)

            run.recent = (run.recent + item.key).takeLast(RECENT_CAP)
            if (!backToBack) {
                run.msSinceMovie = if (item.kind == MediaKind.MOVIE) slotEnd - cursor else run.msSinceMovie + (slotEnd - cursor)
            }
            cursor = slotEnd
        }
        return Extension(out, run.toState())
    }

    /** Pools stay fixed throughout an extension; prepare lookup/order data once, not per airing. */
    private class Candidates(val items: List<MediaItem>, music: Boolean) {
        val chronology by lazy { com.rerun.core.catalog.ComicCatalog.ordered(items) }
        val byKey by lazy { items.associateBy { it.key } }
        val movies = items.filter { it.kind == MediaKind.MOVIE }
        val episodes = if (music) items else items.filter { it.kind == MediaKind.EPISODE }
        val moviesByKey by lazy { movies.associateBy { it.key } }
        val shows by lazy {
            episodes.groupBy { it.showKey ?: it.key }.toSortedMap().mapValues { (_, eps) ->
                eps.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }, { it.key }))
            }
        }
        val showOrder by lazy {
            episodes.groupBy { it.showKey ?: it.key }.toSortedMap().entries
                .sortedBy { (key, eps) -> eps.first().showTitle ?: key }.map { it.key }
        }
    }

    /** Mutable working copy of [ChannelState] for one extend() call. */
    private class Run(s: ChannelState) {
        val seed = s.seed
        var deck: MutableList<String> = s.deck.toMutableList()
        var recent: List<String> = s.recent
        val pointers: MutableMap<String, Int> = s.episodePointers.toMutableMap()
        var msSinceMovie = s.msSinceMovie
        var rngCalls = s.rngCalls

        fun toState() = ChannelState(seed, deck.toList(), recent, pointers.toMap(), msSinceMovie, rngCalls)

        private fun rng(): Random = Random(seed + rngCalls).also { rngCalls++ }

        fun pick(def: ChannelDef, candidates: Candidates): MediaItem {
            if (def.order == PlayOrder.CHRONOLOGICAL) return pickChronological(candidates)
            if (pointers.remove(CHRONOLOGY_KEY) != null) deck.clear()
            if (def.kind == ChannelKind.MUSIC) return pickEpisode(def, candidates)
            val movies = candidates.movies
            val episodes = candidates.episodes
            val wantMovie = when {
                movies.isEmpty() -> false
                episodes.isEmpty() -> true
                else -> msSinceMovie >= def.movieEveryMs
            }
            return if (wantMovie) pickMovie(def, candidates) else pickEpisode(def, candidates)
        }

        private fun pickChronological(candidates: Candidates): MediaItem {
            if (pointers[CHRONOLOGY_KEY] != 1) {
                deck.clear()
                pointers[CHRONOLOGY_KEY] = 1
            }
            deck.removeAll { it !in candidates.byKey }
            if (deck.isEmpty()) deck.addAll(candidates.chronology.map { it.key })
            return candidates.byKey.getValue(deck.removeAt(0))
        }

        private fun pickMovie(def: ChannelDef, candidates: Candidates): MediaItem {
            val byKey = candidates.moviesByKey
            var idx = deck.indexOfFirst { it in byKey }
            if (idx < 0) {
                deck.addAll(freshDeck(def, candidates.movies))
                idx = deck.indexOfFirst { it in byKey }
            }
            return byKey.getValue(deck.removeAt(idx))
        }

        private fun freshDeck(def: ChannelDef, movies: List<MediaItem>): List<String> {
            if (def.order == PlayOrder.IN_ORDER) {
                return movies.sortedWith(compareBy({ it.year ?: 0 }, { it.title }, { it.key })).map { it.key }
            }
            val keys = movies.map { it.key }
            val keySet = keys.toHashSet()
            val holdBackCount = maxOf(1, keys.size / 5)
            val holdBack = recent.asReversed().filter { it in keySet }.distinct().take(holdBackCount).toSet()
            val rest = keys.filter { it !in holdBack }
            if (rest.isEmpty()) return keys.shuffled(rng())
            // Recently played titles go to the back of the new deck instead of being dropped.
            return rest.shuffled(rng()) + holdBack.toList().sorted().shuffled(rng())
        }

        private fun pickEpisode(def: ChannelDef, candidates: Candidates): MediaItem {
            val shows = candidates.shows
            val showKey = if (def.order == PlayOrder.IN_ORDER) {
                val ordered = candidates.showOrder
                val rr = pointers[RR_KEY] ?: 0
                pointers[RR_KEY] = rr + 1
                ordered[rr % ordered.size]
            } else {
                val total = candidates.episodes.size
                var r = rng().nextInt(total)
                shows.entries.first { (_, eps) -> (r < eps.size).also { if (!it) r -= eps.size } }.key
            }
            val eps = shows.getValue(showKey)
            val ptr = pointers[showKey] ?: 0
            pointers[showKey] = ptr + 1
            return eps[ptr % eps.size]
        }
    }

    private companion object {
        const val RECENT_CAP = 200
        const val RR_KEY = "__round_robin"
        const val CHRONOLOGY_KEY = "__chronology"
    }
}
