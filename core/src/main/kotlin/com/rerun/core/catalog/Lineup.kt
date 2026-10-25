package com.rerun.core.catalog

import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.model.Rules
import com.rerun.core.resolve.Resolver
import com.rerun.core.schedule.ScheduleEntry
import java.util.UUID

/** A channel row the lineup planner can see. Room stays outside core. */
data class StoredChannel(val def: ChannelDef, val builtIn: Boolean)

/** One channel the database should upsert after a library refresh. */
data class PlannedChannel(
    val def: ChannelDef,
    val builtIn: Boolean,
    val dormant: Boolean,
    val poolMs: Long,
)

/** Channels to write, and generated rows whose schedule should be deleted. */
data class LineupPlan(val channels: List<PlannedChannel>, val deleteIds: List<String>)

/**
 * What goes on the dial.
 *
 * Built-in channels always exist (dormant until they have 4 hours). Extra themes, movie
 * overflow, and music stations are created only once their pool is long enough. West feeds
 * are the East schedule shifted forward by [WEST_LAG_MS], so West at time T is what East
 * played at T − 3 hours.
 */
object Lineup {
    const val RESTART_ID = "from-the-top"
    const val RESTART_NUMBER = 999
    const val WEST_LAG_MS = 3 * 3_600_000L
    const val WEST_NUMBER_OFFSET = 500
    const val MUSIC_BASE = 2000
    const val ADS_ID = "ads:only"
    val adsChannel = ChannelDef(ADS_ID, 998, "Buy Me Maybe", ChannelKind.CUSTOM, Rules(kinds = setOf(MediaKind.MOVIE)))

    fun isWest(id: String): Boolean = id.endsWith(":w")
    fun westId(eastId: String): String = if (isWest(eastId)) eastId else "$eastId:w"
    fun eastOf(id: String): String = if (isWest(id)) id.removeSuffix(":w") else id

    /** Rows this app invented. A refresh may drop them. Custom channels are not generated. */
    fun isGenerated(id: String): Boolean =
        id == ADS_ID || id.startsWith("opt:") || id.startsWith("movies:") || id.startsWith("music:") || isWest(id)

    /** Built-ins plus every extra that currently has enough content. No West twins. */
    fun channels(library: List<MediaItem>, music: Boolean): List<ChannelDef> {
        val movieMs = moviePoolMs(library)
        val extras = optionalThemes().filter { qualified(it, library) }
        val features = movieChannels().filter { movieMs >= movieThreshold(it.id) }
        val stations = if (music) musicTemplates(library).filter { qualified(it, library) } else emptyList()
        return Catalog.builtInChannels() + extras + features + stations
    }

    fun optionalThemes(): List<ChannelDef> = optional
    fun movieChannels(): List<ChannelDef> = movies

    /** Copies [entries] onto [channelId], moving each slot forward by [lagMs]. */
    fun shift(entries: List<ScheduleEntry>, channelId: String, lagMs: Long): List<ScheduleEntry> =
        entries.map { it.copy(channelId = channelId, startMs = it.startMs + lagMs, endMs = it.endMs + lagMs) }

    /**
     * Merges the catalog with channels already stored.
     * Stock generated rows that no longer qualify are listed in [LineupPlan.deleteIds].
     * A generated row the user edited is kept and marked dormant instead.
     * [RESTART_ID] is left untouched.
     */
    fun plan(
        library: List<MediaItem>, existing: List<StoredChannel>, eastWest: Boolean, music: Boolean,
        adsPoolMs: Long = 0,
    ): LineupPlan {
        val existingById = existing.associateBy { it.def.id }
        val durations = HashMap<ChannelDef, Long>()
        fun duration(def: ChannelDef): Long = durations.getOrPut(def) { poolMs(def, library) }
        val builtIn = Catalog.builtInChannels()
        val stations = musicTemplates(library)
        val movieMs = moviePoolMs(library)
        val qualified = (builtIn + optional.filter { duration(it) >= Resolver.DORMANT_MS } +
            movies.filter { movieMs >= movieThreshold(it.id) } +
            if (music) stations.filter { duration(it) >= Resolver.DORMANT_MS } else emptyList()).map { it.id }.toSet()
        val templates = builtIn + optional + movies + stations
        val upserts = ArrayList<PlannedChannel>()
        val seen = HashSet<String>()

        for (template in templates) {
            if (isGenerated(template.id) && template.id !in qualified) {
                val prev = existingById[template.id] ?: continue
                if (looksStock(prev.def, template)) continue
                val kept = prev.def.copy(name = Catalog.chooseName(template.id, prev.def.name, template.name))
                val pool = duration(kept)
                val dormant = (kept.kind == ChannelKind.MUSIC && !music) || pool < Resolver.DORMANT_MS
                upserts += PlannedChannel(kept, prev.builtIn, dormant, pool)
                if (feedsOn(eastWest, kept, dormant)) upserts += westOf(kept, kept.name, pool)
                seen += template.id
                continue
            }
            val prev = existingById[template.id]
            val base = prev?.def ?: template
            val baseName = Catalog.chooseName(template.id, prev?.def?.name, template.name)
            val pool = duration(base)
            val dormant = pool < Resolver.DORMANT_MS
            val showFeed = feedsOn(eastWest, base, dormant)
            val east = base.copy(name = baseName)
            upserts += PlannedChannel(east, prev?.builtIn ?: true, dormant, pool)
            seen += template.id
            if (showFeed) upserts += westOf(east, baseName, pool)
        }

        for (prev in existing) {
            val id = prev.def.id
            if (id in seen || id == RESTART_ID || isWest(id)) continue
            if (isGenerated(id)) {
                // A genre can disappear completely; retain edits even without a live template.
                if (id.startsWith("music:") && !looksStockMusic(prev.def)) {
                    upserts += PlannedChannel(prev.def, prev.builtIn, dormant = true, poolMs = duration(prev.def))
                }
                continue
            }
            val pool = duration(prev.def)
            val dormant = pool < Resolver.DORMANT_MS
            val baseName = Catalog.stripFeed(prev.def.name)
            val showFeed = feedsOn(eastWest, prev.def, dormant)
            val east = prev.def.copy(name = baseName)
            upserts += PlannedChannel(east, prev.builtIn, dormant, pool)
            if (showFeed) upserts += westOf(east, baseName, pool)
        }

        if (adsPoolMs > 0) {
            val number = existingById[ADS_ID]?.def?.number ?: adsChannel.number
            upserts += PlannedChannel(adsChannel.copy(number = number), true, false, adsPoolMs)
        }
        val keep = upserts.map { it.def.id }.toSet()
        val deleteIds = existing.map { it.def.id }
            .filter { it !in keep && it != RESTART_ID && isGenerated(it) }
            .sorted()
        return LineupPlan(uniqueNumbers(upserts), deleteIds)
    }

    private fun westOf(east: ChannelDef, baseName: String, poolMs: Long): PlannedChannel {
        val west = east.copy(
            id = westId(east.id),
            number = east.number + WEST_NUMBER_OFFSET,
            name = "$baseName-W",
            hidden = false,
        )
        return PlannedChannel(west, builtIn = true, dormant = false, poolMs = poolMs)
    }

    /**
     * Keeps a custom or built-in number where the user left it.
     * Generated rows (extras, music, West) take the next free number when theirs is taken.
     */
    private fun uniqueNumbers(channels: List<PlannedChannel>): List<PlannedChannel> {
        val used = HashSet<Int>()
        used += RESTART_NUMBER
        for (row in channels) if (!isGenerated(row.def.id)) used += row.def.number
        return channels.map { row ->
            if (!isGenerated(row.def.id)) row
            else {
                var number = row.def.number
                while (number in used) number++
                used += number
                if (number == row.def.number) row else row.copy(def = row.def.copy(number = number))
            }
        }
    }

    private fun feedsOn(eastWest: Boolean, def: ChannelDef, dormant: Boolean): Boolean =
        eastWest && !def.hidden && !dormant && def.id != RESTART_ID && !isWest(def.id)

    private fun qualified(def: ChannelDef, library: List<MediaItem>): Boolean =
        !Resolver.isDormant(Resolver.pool(def, library))

    private fun poolMs(def: ChannelDef, library: List<MediaItem>): Long =
        Resolver.pool(def, library).sumOf { it.durationMs }

    private fun moviePoolMs(library: List<MediaItem>): Long =
        library.filter { it.kind == MediaKind.MOVIE && it.durationMs >= Resolver.MIN_ITEM_MS }.sumOf { it.durationMs }

    private fun movieThreshold(id: String): Long = when (id) {
        "movies:1" -> Resolver.DORMANT_MS
        "movies:2" -> 24 * 3_600_000L
        "movies:3" -> 48 * 3_600_000L
        else -> Long.MAX_VALUE
    }

    private fun looksStock(stored: ChannelDef, template: ChannelDef): Boolean {
        val name = Catalog.stripFeed(stored.name)
        val nameOk = name == template.name || name in Catalog.retiredNames(template.id)
        return nameOk &&
            stored.number == template.number &&
            stored.kind == template.kind &&
            stored.rules == template.rules &&
            stored.order == template.order &&
            stored.blocks == template.blocks &&
            stored.hidden == template.hidden &&
            stored.kidsSafe == template.kidsSafe &&
            stored.movieEveryMs == template.movieEveryMs
    }

    private fun looksStockMusic(stored: ChannelDef): Boolean {
        val genre = stored.rules.genres.singleOrNull() ?: return false
        return stored == ChannelDef(
            stored.id, stored.number, genre, ChannelKind.MUSIC,
            Rules(genres = setOf(genre), kinds = setOf(MediaKind.TRACK)),
        )
    }

    fun musicTemplates(library: List<MediaItem>): List<ChannelDef> {
        val totals = LinkedHashMap<String, Long>()
        for (item in library) {
            if (item.kind != MediaKind.TRACK || item.durationMs < Resolver.MIN_ITEM_MS) continue
            for (genre in item.genres) {
                if (genre.isBlank()) continue
                totals[genre] = (totals[genre] ?: 0L) + item.durationMs
            }
        }
        val reserved = reservedNumbers()
        var next = MUSIC_BASE
        return totals.keys.sorted().distinctBy { it.lowercase() }.sortedWith(String.CASE_INSENSITIVE_ORDER).map { genre ->
            while (next in reserved) next++
            val number = next++
            ChannelDef(
                id = musicId(genre),
                number = number,
                name = genre,
                kind = ChannelKind.MUSIC,
                rules = Rules(genres = setOf(genre), kinds = setOf(MediaKind.TRACK)),
            )
        }
    }

    private fun musicId(genre: String): String {
        val slug = genre.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString("-")
            .ifEmpty { "genre" }
        val normalized = genre.trim().lowercase()
        val suffix = if (normalized == slug) "" else "-${UUID.nameUUIDFromBytes(normalized.toByteArray(Charsets.UTF_8))}"
        return "music:$slug$suffix"
    }

    private fun reservedNumbers(): Set<Int> {
        val east = (Catalog.builtInChannels() + optional + movies).map { it.number }
        return (east + east.map { it + WEST_NUMBER_OFFSET } + RESTART_NUMBER).toSet()
    }

    private val kidRatings = setOf("G", "PG", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG")
    private val video = setOf(MediaKind.MOVIE, MediaKind.EPISODE)

    private val optional: List<ChannelDef> = listOf(
        opt("opt:drama", 320, "The Matinee", Rules(genres = setOf("Drama"))),
        opt("opt:crime", 321, "Case File", Rules(genres = setOf("Crime"))),
        opt("opt:mystery", 322, "Whodunit", Rules(genres = setOf("Mystery"))),
        opt("opt:romance", 323, "Heartline", Rules(genres = setOf("Romance"))),
        opt("opt:docs", 324, "True-ish", Rules(genres = setOf("Documentary", "Docuseries"))),
        opt("opt:western", 325, "High Noon", Rules(genres = setOf("Western"))),
        opt("opt:war", 326, "The Front", Rules(genres = setOf("War", "War & Politics"))),
        opt("opt:musical", 327, "The Overture", Rules(genres = setOf("Musical", "Music"), kinds = video)),
        opt("opt:anime", 328, "Import Toons", Rules(genres = setOf("Anime"))),
        opt("opt:reality", 329, "Unscripted", Rules(genres = setOf("Reality"))),
        opt("opt:sports", 330, "The Arena", Rules(genres = setOf("Sport", "Sports"))),
        opt("opt:news", 331, "This Just In", Rules(genres = setOf("News"))),
        opt("opt:kids", 332, "Playroom", Rules(genres = setOf("Children", "Kids"), ratings = kidRatings, kinds = video), kidsSafe = true),
        opt("opt:sitcom", 333, "Canned Laughter", Rules(genres = setOf("Sitcom", "Situation Comedy"), kinds = setOf(MediaKind.EPISODE))),
        opt("opt:dramedy", 334, "Sob Story", Rules(genres = setOf("Dramedy", "Comedy-drama", "Comedy Drama"))),
        opt("opt:psychic", 335, "Cold Read", Rules(genres = setOf("Psychic", "Paranormal"))),
    )

    private val movies: List<ChannelDef> = listOf(
        ChannelDef("movies:1", 360, "The Big Picture", ChannelKind.THEME, Rules(kinds = setOf(MediaKind.MOVIE))),
        ChannelDef("movies:2", 361, "Second Feature", ChannelKind.THEME, Rules(kinds = setOf(MediaKind.MOVIE))),
        ChannelDef("movies:3", 362, "Late Seating", ChannelKind.THEME, Rules(kinds = setOf(MediaKind.MOVIE))),
    )

    private fun opt(id: String, number: Int, name: String, rules: Rules, kidsSafe: Boolean = false) =
        ChannelDef(id, number, name, ChannelKind.THEME, rules, kidsSafe = kidsSafe)
}
