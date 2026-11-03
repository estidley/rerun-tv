package com.rerun.core.schedule

import com.rerun.core.catalog.Catalog
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.model.PlayOrder
import com.rerun.core.model.Rules
import java.nio.ByteBuffer

/**
 * Random-access UTC v1 scheduling over an already routed local pool; never replays calendar
 * history. Results are whole entries intersecting [fromMs, toMs), not truncated entries.
 * Ads and playback source offsets remain local concerns. Requests crossing the anchor omit only
 * their pre-anchor portion.
 *
 * Each deck has seeded fixed first/last anchors and a per-round shuffled interior. This slightly
 * reduces randomness (two-item decks have a fixed permutation), but gives equal exposure and
 * no adjacent repeat across round boundaries without recursively computing older rounds.
 */
object SharedPilotScheduler {
    private const val QUARTER = 15 * 60_000L
    private const val WINDOW = 12 * QUARTER
    private const val MIN_DURATION = 60_000L
    private const val MAX_REQUEST = 366 * SharedScheduleCode.DAY_MS
    private const val LIMIT = SharedScheduleCode.MAX_DAY * SharedScheduleCode.DAY_MS
    private val stock by lazy { Catalog.builtInChannels().associateBy { it.id } }

    /** Configured numbers select stock channels; stock time blocks and edited programming are unsupported. */
    fun participates(def: ChannelDef): Boolean {
        val original = stock[def.id] ?: return false
        return def.number in 100..199 &&
            def.kind != ChannelKind.MUSIC && def.kind == original.kind &&
            def.rules == original.rules && def.order == PlayOrder.SHUFFLE && def.order == original.order &&
            def.blocks.isEmpty() && original.blocks.isEmpty() &&
            def.movieEveryMs == original.movieEveryMs && def.kidsSafe == original.kidsSafe &&
            def.minimumContentHours == original.minimumContentHours
    }

    fun generate(
        def: ChannelDef,
        pool: List<MediaItem>,
        recipe: SharedScheduleCode,
        fromMs: Long,
        toMs: Long,
    ): List<ScheduleEntry> = generateMeasured(def, pool, recipe, fromMs, toMs, null)

    /** Per-call deterministic work accounting for tests; no global mutable instrumentation. */
    internal class WorkStats {
        var indexedItems = 0L
        var episodeTransitionStates = 0L
        var episodeTransitionSteps = 0L
        var episodeGraphStates = 0L
        var episodeStateLookups = 0L
        var visitedWindows = 0L
        var indexNanos = 0L
    }

    internal fun generateMeasured(
        def: ChannelDef,
        pool: List<MediaItem>,
        recipe: SharedScheduleCode,
        fromMs: Long,
        toMs: Long,
        stats: WorkStats?,
    ): List<ScheduleEntry> {
        require(participates(def)) { "Channel does not support the shared scheduling pilot" }
        require(fromMs in -LIMIT..LIMIT && toMs in -LIMIT..LIMIT) { "Shared schedule time is outside the supported range" }
        require(toMs >= fromMs && toMs - fromMs <= MAX_REQUEST) { "Shared schedule request must span 0 to 366 days" }
        val anchor = recipe.anchorDay * SharedScheduleCode.DAY_MS
        val effectiveFrom = maxOf(fromMs, anchor)
        if (effectiveFrom >= toMs) return emptyList()
        val indexStart = if (stats != null) System.nanoTime() else 0
        val index = ContentIndex(pool, stats)
        stats?.let { it.indexNanos = System.nanoTime() - indexStart }
        if (index.movies.isEmpty() && index.shows.isEmpty()) return emptyList()
        val salt = salt(def, recipe)
        val movies = Deck(index.movies.map { it.key }, salt, "movies")
        val shows = Deck(index.shows.keys.toList(), salt, "shows")
        val both = index.movies.isNotEmpty() && index.shows.isNotEmpty()
        val moviePhase = (pilotHash("phase-v1", number(recipe.seed), pilotBytes(def.id),
            number(def.number.toLong()))[0].toInt() and 1).toLong()
        val result = ArrayList<ScheduleEntry>()
        val first = (effectiveFrom - anchor) / WINDOW
        var window = first
        val last = (toMs - 1 - anchor) / WINDOW
        while (window <= last) {
            stats?.let { it.visitedWindows++ }
            val start = anchor + window * WINDOW
            val movieWindow = index.movies.isNotEmpty() && (!both || window % 2 == moviePhase)
            val ordinal = if (both) window / 2 else window
            fun add(begin: Long, end: Long, key: String?) {
                if (begin < toMs && end > effectiveFrom && begin < end) {
                    result += ScheduleEntry(def.id, begin, end, if (key == null) EntryKind.FILLER else EntryKind.PROGRAM, key)
                }
            }
            if (movieWindow) {
                val item = index.items.getValue(movies.at(ordinal))
                val end = start + minOf(item.durationMs, WINDOW)
                add(start, end, item.key)
                add(end, start + WINDOW, null)
            } else {
                val show = index.shows.getValue(shows.at(ordinal))
                var episode = show.stateAt(ordinal / index.shows.size, stats)
                var units = 0
                while (units + show.units[episode] <= 12) {
                    val item = show.items[episode]
                    val begin = start + units * QUARTER
                    val end = begin + item.durationMs
                    units += show.units[episode]
                    add(begin, end, item.key)
                    add(end, start + units * QUARTER, null)
                    episode = (episode + 1) % show.items.size
                }
                add(start + units * QUARTER, start + WINDOW, null)
            }
            window++
        }
        return result
    }

    /** Canonical source/programming fingerprint, excluding titles, presentation, tracks and ads. */
    fun fingerprint(def: ChannelDef, pool: List<MediaItem>, recipe: SharedScheduleCode): String {
        require(participates(def)) { "Channel does not support the shared scheduling pilot" }
        val records = uniqueItems(pool.filter { it.kind != MediaKind.TRACK }).values
            .map(::itemRecord).sortedWith(pilotByteOrder)
        return pilotHash("fingerprint-v1", salt(def, recipe), rulesRecord(def.rules), *records.toTypedArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun number(value: Long) = ByteBuffer.allocate(8).putLong(value).array()

    private fun salt(def: ChannelDef, recipe: SharedScheduleCode) =
        pilotHash("channel-v1", number(recipe.seed), number(recipe.anchorDay), pilotBytes(def.id),
            number(def.number.toLong()), pilotBytes(def.kind.name), pilotBytes(def.order.name),
            number(def.movieEveryMs), number(if (def.kidsSafe) 1 else 0),
            number(def.minimumContentHours?.toLong() ?: -1))

    private fun strings(values: Collection<String>) =
        pilotHash("set-v1", *values.map(::pilotBytes).sortedWith(pilotByteOrder).toTypedArray())

    private fun rulesRecord(rules: Rules) = pilotHash("rules-v1",
        strings(rules.networks), strings(rules.titles), strings(rules.tmdbIds), strings(rules.labels),
        strings(rules.collections), strings(rules.genres), strings(rules.excludeGenres),
        strings(rules.decades.map { it.toString() }), strings(rules.ratings),
        strings(rules.kinds.map { it.name }), number(rules.bucket.toLong()), number(rules.bucketCount.toLong()),
        strings(rules.franchises))

    private fun itemRecord(item: MediaItem) = pilotHash("item-v1",
        pilotBytes(item.key), pilotBytes(item.kind.name), number(item.durationMs),
        pilotBytes(item.showKey.orEmpty()), number(item.season?.toLong() ?: Long.MIN_VALUE),
        number(item.episode?.toLong() ?: Long.MIN_VALUE), number(item.year?.toLong() ?: Long.MIN_VALUE),
        strings(item.genres), pilotBytes(item.network.orEmpty()), pilotBytes(item.studio.orEmpty()),
        pilotBytes(item.contentRating.orEmpty()), pilotBytes(item.tmdbId.orEmpty()),
        strings(item.labels), strings(item.collections))

    private fun uniqueItems(pool: List<MediaItem>): Map<String, MediaItem> {
        val unique = LinkedHashMap<String, MediaItem>()
        for (item in pool) {
            requireValidKey(item.key, "item")
            item.showKey?.let { requireValidKey(it, "show") }
            val old = unique.putIfAbsent(item.key, item)
            require(old == null || itemRecord(old).contentEquals(itemRecord(item))) {
                "Conflicting shared schedule metadata for item key ${item.key}"
            }
        }
        return unique
    }

    private fun requireValidKey(key: String, kind: String) {
        require(key.isNotBlank() && key.none(Char::isISOControl) &&
            Charsets.UTF_8.newEncoder().canEncode(key)) {
            "Shared schedule $kind key must be nonblank text without control characters or malformed Unicode"
        }
    }

    private class ContentIndex(pool: List<MediaItem>, stats: WorkStats?) {
        val items: Map<String, MediaItem>
        val movies: List<MediaItem>
        val shows: Map<String, Show>
        init {
            stats?.let { it.indexedItems += pool.size }
            val eligible = uniqueItems(pool.filter { it.kind != MediaKind.TRACK }).values.filter {
                it.durationMs >= MIN_DURATION && when (it.kind) {
                    MediaKind.MOVIE -> it.durationMs < WINDOW + 2 * MIN_DURATION
                    MediaKind.EPISODE -> it.durationMs <= WINDOW
                    MediaKind.TRACK -> false
                }
            }
            items = eligible.associateBy { it.key }
            movies = items.values.filter { it.kind == MediaKind.MOVIE }
            shows = items.values.filter { it.kind == MediaKind.EPISODE }
                .groupBy { it.showKey ?: it.key }
                .mapValues { (_, episodes) ->
                    Show(episodes.sortedWith(compareBy<MediaItem> { it.season ?: Int.MAX_VALUE }
                        .thenBy { it.episode ?: Int.MAX_VALUE }
                        .thenComparator { a, b -> pilotByteOrder.compare(pilotBytes(a.key), pilotBytes(b.key)) }), stats)
                }
        }
    }

    private class Deck(keys: List<String>, private val salt: ByteArray, private val domain: String) {
        private val base = sorted(keys, -1)
        private var cachedRound = -1L
        private var cached = base
        private fun sorted(keys: List<String>, round: Long): List<String> =
            keys.map { it to pilotHash("deck-v1", salt, pilotBytes(domain), number(round), pilotBytes(it)) }
                .sortedWith { a, b ->
                    val hash = pilotByteOrder.compare(a.second, b.second)
                    if (hash != 0) hash else pilotByteOrder.compare(pilotBytes(a.first), pilotBytes(b.first))
                }.map { it.first }

        fun at(ordinal: Long): String {
            val round = ordinal / base.size
            if (round != cachedRound) {
                cached = if (base.size <= 2) base else
                    listOf(base.first()) + sorted(base.subList(1, base.lastIndex), round) + base.last()
                cachedRound = round
            }
            return cached[(ordinal % base.size).toInt()]
        }
    }

    private class Show(val items: List<MediaItem>, stats: WorkStats?) {
        val units = items.map { ((it.durationMs + QUARTER - 1) / QUARTER).toInt() }
        private val path: IntArray
        private val cycleStart: Int
        init {
            // At most twelve positive quarter-hour units fit, even when a tiny pool wraps.
            val transitions = IntArray(items.size) { initial ->
                stats?.let { it.episodeTransitionStates++ }
                var next = initial
                var used = 0
                while (used + units[next] <= 12) {
                    stats?.let { it.episodeTransitionSteps++ }
                    used += units[next]
                    next = (next + 1) % items.size
                }
                next
            }
            val seen = IntArray(items.size) { -1 }
            val visited = ArrayList<Int>()
            var next = 0
            while (seen[next] == -1) {
                stats?.let { it.episodeGraphStates++ }
                seen[next] = visited.size
                visited += next
                next = transitions[next]
            }
            cycleStart = seen[next]
            path = visited.toIntArray()
        }

        fun stateAt(appearance: Long, stats: WorkStats?): Int {
            stats?.let { it.episodeStateLookups++ }
            val position = if (appearance < path.size) appearance.toInt() else
                cycleStart + ((appearance - cycleStart) % (path.size - cycleStart)).toInt()
            return path[position]
        }
    }
}
