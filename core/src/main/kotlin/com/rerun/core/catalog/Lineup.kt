package com.rerun.core.catalog

import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.model.Rules
import com.rerun.core.resolve.Resolver
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
 * overflow, and music stations are created only once their pool is long enough.
 */
object Lineup {
    const val RESTART_ID = "from-the-top"
    const val RESTART_NUMBER = 999
    const val MUSIC_BASE = 2000
    const val ADS_ID = "ads:only"
    const val TV_DECADE_MIN_MS = 24 * 3_600_000L
    const val MUSIC_DECADE_MIN_MS = 12 * 3_600_000L
    val adsChannel = ChannelDef(ADS_ID, 998, "Buy Me Maybe", ChannelKind.CUSTOM, Rules(kinds = setOf(MediaKind.MOVIE)))

    /** Toonuki now owns the Anime genre; only a user-edited former Import Toons station is retained. */
    private fun isEditedRetiredAnime(def: ChannelDef): Boolean =
        def.id == "opt:anime" && !looksStock(
            def, ChannelDef("opt:anime", 328, "Import Toons", ChannelKind.THEME, Rules(genres = setOf("Anime"))),
        )

    /** Rows this app invented. A refresh may drop them. Custom channels are not generated. */
    fun isGenerated(id: String): Boolean =
        id == ADS_ID || id.startsWith("decade:") || id.startsWith("opt:") || id.startsWith("movies:") ||
            id.startsWith("music:") || id.startsWith("marvel:") || id.startsWith("dc:")

    /** Built-ins plus every extra that currently has enough content. */
    fun channels(library: List<MediaItem>, music: Boolean): List<ChannelDef> {
        val extras = optionalThemes().filter { qualified(it, library) }
        val features = movieTemplates(library)
        val stations = if (music) musicTemplates(library).filter { qualified(it, library) } else emptyList()
        val totals = decadeTotals(library)
        val decades = decadeTemplates.filter {
            (it.kind != ChannelKind.MUSIC || music) && totals.getOrDefault(it.id, 0L) >= decadeMinimum(it)
        }
        return Catalog.builtInChannels() + extras + features + stations + decades
    }

    fun optionalThemes(): List<ChannelDef> = optional
    fun movieChannels(): List<ChannelDef> = movies

    /**
     * Movie channels the library supports. With two or more, each plays its own disjoint slice
     * of the movie pool, unless a slice would be too small to stay on the air.
     */
    private fun movieTemplates(library: List<MediaItem>): List<ChannelDef> {
        val movieMs = moviePoolMs(library)
        val active = movies.filter { movieMs >= movieThreshold(it.id) }
        if (active.size <= 1) return active
        val sliced = active.mapIndexed { index, def ->
            def.copy(rules = def.rules.copy(bucket = index, bucketCount = active.size))
        }
        return if (sliced.all { qualified(it, library) }) sliced else active
    }

    /** Stock movie channels differ only in their bucket slice; anything else is a user edit. */
    private fun sameIgnoringMovieBucket(stored: ChannelDef, template: ChannelDef): Boolean =
        if (template.id.startsWith("movies:")) {
            stored.rules.copy(bucket = 0, bucketCount = 1) == template.rules.copy(bucket = 0, bucketCount = 1)
        } else stored.rules == template.rules

    /**
     * Merges the catalog with channels already stored.
     * Stock generated rows that no longer qualify are listed in [LineupPlan.deleteIds].
     * A generated row the user edited is kept and marked dormant instead.
     * [RESTART_ID] is left untouched.
     */
    fun plan(
        library: List<MediaItem>, existing: List<StoredChannel>, music: Boolean,
        adsPoolMs: Long = 0,
    ): LineupPlan {
        val existingById = existing.associateBy { it.def.id }
        val durations = HashMap<ChannelDef, Long>()
        val decadeTotals = decadeTotals(library)
        val decadeDurations = decadeTemplates.associate { it.rules to decadeTotals.getOrDefault(it.id, 0L) }
        fun duration(def: ChannelDef): Long = durations.getOrPut(def) {
            if (def.blocks.isEmpty()) decadeDurations[def.rules] ?: poolMs(def, library)
            else poolMs(def, library)
        }
        val builtIn = Catalog.builtInChannels()
        val stations = musicTemplates(library)
        val activeMovies = movieTemplates(library).associateBy { it.id }
        val templates = builtIn + optional + movies.map { activeMovies[it.id] ?: it } + stations + decadeTemplates
        val upserts = ArrayList<PlannedChannel>()
        val seen = HashSet<String>()

        for (template in templates) {
            val prev = existingById[template.id]
            val stored = prev?.def
            // Upgrade the old stock Toonami pool, retaining names, numbers and play order.
            // Explicitly edited rules or time blocks remain owned by the user.
            val base = if (template.id == "toonami" && prev?.builtIn == true &&
                stored?.rules == Rules(networks = setOf("toonami"), titles = Curated.toonamiShows.toSet()) &&
                stored.blocks.isEmpty()
            ) stored.copy(rules = template.rules, kidsSafe = template.kidsSafe)
            // Stock movie channels follow the current bucket split as the library grows or shrinks.
            else if (stored != null && template.id.startsWith("movies:") && sameIgnoringMovieBucket(stored, template))
                stored.copy(rules = template.rules)
            else stored ?: template
            val baseName = Catalog.chooseName(template.id, prev?.def?.name, template.name, prev?.def?.userRenamed ?: false)
            val pool = duration(base)
            val dormant = isDormant(base, pool, music)
            if (isGenerated(template.id) && dormant && (prev == null || looksStock(prev.def, template))) continue
            upserts += PlannedChannel(base.copy(name = baseName), prev?.builtIn ?: true, dormant, pool)
            seen += template.id
        }

        for (prev in existing) {
            val id = prev.def.id
            if (id in seen || id == RESTART_ID) continue
            if (isGenerated(id)) {
                // A genre can disappear completely; retain edits even without a live template.
                if ((id.startsWith("music:") && !looksStockMusic(prev.def)) || isEditedRetiredAnime(prev.def)) {
                    val pool = duration(prev.def)
                    upserts += PlannedChannel(prev.def, prev.builtIn, dormant = isDormant(prev.def, pool, music), poolMs = pool)
                }
                continue
            }
            val pool = duration(prev.def)
            val dormant = isDormant(prev.def, pool, music)
            upserts += PlannedChannel(prev.def, prev.builtIn, dormant, pool)
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

    /**
     * Keeps a custom or built-in number where the user left it.
     * Generated rows (extras and music) take the next free number when theirs is taken.
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

    fun minimumContentMs(def: ChannelDef): Long {
        val override = def.minimumContentHours
        require(override == null || override in 0..168) { "Minimum content must be 0–168 hours" }
        if (override != null) return override * 3_600_000L
        val id = def.id
        return when {
            id == ADS_ID || id == RESTART_ID -> 0
            id.startsWith("decade:tv:") -> TV_DECADE_MIN_MS
            id.startsWith("decade:music:") -> MUSIC_DECADE_MIN_MS
            id.startsWith("movies:") -> movieThreshold(id)
            else -> Resolver.DORMANT_MS
        }
    }

    fun isDormant(def: ChannelDef, poolMs: Long, music: Boolean = true): Boolean =
        (def.kind == ChannelKind.MUSIC && !music) || poolMs <= 0 || poolMs < minimumContentMs(def)

    private fun decadeMinimum(def: ChannelDef): Long =
        if (def.kind == ChannelKind.MUSIC) MUSIC_DECADE_MIN_MS else TV_DECADE_MIN_MS

    /** Stock decade qualification is one pass, without retaining a pool per candidate channel. */
    private fun decadeTotals(library: List<MediaItem>): Map<String, Long> {
        val totals = HashMap<String, Long>()
        for (item in library) {
            if (item.durationMs < Resolver.MIN_ITEM_MS) continue
            val year = item.year?.takeIf { it in 1900..2099 } ?: continue
            val type = when (item.kind) {
                MediaKind.EPISODE -> "tv"
                MediaKind.TRACK -> "music"
                else -> continue
            }
            val id = "decade:$type:${year / 10 * 10}"
            totals[id] = totals.getOrDefault(id, 0L) + item.durationMs
        }
        return totals
    }

    private val decadeTemplates: List<ChannelDef> = (1900..2090 step 10).flatMap { decade ->
        val index = (decade - 1900) / 10
        listOf(
            ChannelDef(
                "decade:tv:$decade", 1100 + index, "${decade}s TV", ChannelKind.THEME,
                Rules(decades = setOf(decade), kinds = setOf(MediaKind.EPISODE)),
            ),
            ChannelDef(
                "decade:music:$decade", 3000 + index, "${decade}s Music", ChannelKind.MUSIC,
                Rules(decades = setOf(decade), kinds = setOf(MediaKind.TRACK)),
            ),
        )
    }

    private fun looksStock(stored: ChannelDef, template: ChannelDef): Boolean {
        if (stored.userRenamed) return false
        val name = stored.name
        val nameOk = name == template.name || name in Catalog.retiredNames(template.id)
        return nameOk &&
            stored.number == template.number &&
            stored.kind == template.kind &&
            sameIgnoringMovieBucket(stored, template) &&
            stored.order == template.order &&
            stored.blocks == template.blocks &&
            stored.hidden == template.hidden &&
            stored.minimumContentHours == template.minimumContentHours &&
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
        return ((Catalog.builtInChannels() + optional + movies + decadeTemplates).map { it.number } + RESTART_NUMBER).toSet()
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
