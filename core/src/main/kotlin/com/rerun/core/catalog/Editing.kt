package com.rerun.core.catalog

import com.rerun.core.model.ChannelDef
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.model.Rules
import com.rerun.core.model.matchKey
import com.rerun.core.resolve.Resolver

/** Everything the channel editor can offer as a choice, taken from the actual library. */
data class Facets(
    val genres: List<String>,
    val shows: List<String>,
    val labels: List<String>,
    val collections: List<String>,
    val decades: List<Int>,
    val ratings: List<String>,
    /** Canonical network ids that have at least one item (via alias or routing). */
    val networks: List<String>,
) {
    companion object {
        fun from(items: List<MediaItem>): Facets {
            val ci = String.CASE_INSENSITIVE_ORDER
            return Facets(
                genres = items.flatMap { it.genres }.distinct().sortedWith(ci),
                shows = items.filter { it.kind == MediaKind.EPISODE }.mapNotNull { it.showTitle }.distinct().sortedWith(ci),
                labels = items.flatMap { it.labels }.distinct().sortedWith(ci),
                collections = items.flatMap { it.collections }.distinct().sortedWith(ci),
                decades = items.mapNotNull { it.year?.let { y -> y / 10 * 10 } }.distinct().sorted(),
                ratings = items.mapNotNull { it.contentRating }.distinct().sortedWith(ci),
                networks = items.flatMapTo(LinkedHashSet()) { Resolver.networksOf(it) }.sortedBy { Catalog.numberOf(it) },
            )
        }
    }
}

/** A show in the "Find related" results: one row per series, however many Plex copies it has. */
data class RelatedShow(val title: String, val tmdbId: String?, val episodes: Int, val copies: Int, val hours: Int)

data class RelatedMovie(val title: String, val year: Int?)

/** Everything in the library whose title (or collection name) contains a word. */
data class Related(val shows: List<RelatedShow>, val movies: List<RelatedMovie>, val collections: List<String>)

/**
 * "Find related" for the channel editor: [word] is matched accent- and punctuation-insensitively
 * as a substring, so "naruto" finds Naruto, Naruto Shippūden and Boruto: Naruto Next Generations.
 */
fun Facets.Companion.related(word: String, items: List<MediaItem>): Related {
    val needle = matchKey(word)
    if (needle.isEmpty()) return Related(emptyList(), emptyList(), emptyList())
    val ci = String.CASE_INSENSITIVE_ORDER
    val shows = items.filter { it.kind == MediaKind.EPISODE && it.showTitle?.let { t -> needle in matchKey(t) } == true }
        .groupBy { it.tmdbId?.let { id -> "tmdb:$id" } ?: "title:${matchKey(it.showTitle!!)}" }
        .map { (_, eps) ->
            val title = eps.groupingBy { it.showTitle!! }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).first().key
            val copies = eps.mapNotNullTo(HashSet()) { it.showKey }.size.coerceAtLeast(1)
            val distinct = eps.distinctBy { (it.season ?: 1) to (it.episode ?: 0) }
            RelatedShow(title, eps.firstNotNullOfOrNull { it.tmdbId }, distinct.size, copies,
                (distinct.sumOf { it.durationMs } / 3_600_000L).toInt())
        }
        .sortedWith(compareBy(ci) { it.title })
    val movies = items.filter { it.kind == MediaKind.MOVIE && needle in matchKey(it.title) }
        .map { RelatedMovie(it.title, it.year) }.distinct()
        .sortedWith(compareBy<RelatedMovie, String>(ci) { it.title }.thenBy { it.year ?: 0 })
    val collections = items.flatMap { it.collections }.distinct().filter { needle in matchKey(it) }.sortedWith(ci)
    return Related(shows, movies, collections)
}

/** Whether a "Find related" show row is ticked: by its title (accent-insensitive) or its TMDB id. */
fun Rules.has(show: RelatedShow): Boolean =
    titles.any { matchKey(it) == matchKey(show.title) } || (show.tmdbId != null && show.tmdbId in tmdbIds)

/** Ticks or unticks a show: title and TMDB id together, so a Plex rename doesn't drop it. */
fun Rules.toggle(show: RelatedShow): Rules =
    if (has(show)) {
        copy(titles = titles.filterTo(HashSet()) { matchKey(it) != matchKey(show.title) }, tmdbIds = tmdbIds - setOfNotNull(show.tmdbId))
    } else {
        copy(titles = titles + show.title, tmdbIds = tmdbIds + setOfNotNull(show.tmdbId))
    }

fun Rules.has(movie: RelatedMovie): Boolean = titles.any { matchKey(it) == matchKey(movie.title) }

/** Ticking a movie also lets movies through the channel's media and title-kind filters. */
fun Rules.toggle(movie: RelatedMovie): Rules =
    if (has(movie)) {
        copy(titles = titles.filterTo(HashSet()) { matchKey(it) != matchKey(movie.title) })
    } else {
        copy(
            titles = titles + movie.title,
            kinds = kinds + MediaKind.MOVIE,
            titleKinds = if (titleKinds.isEmpty()) titleKinds else titleKinds + MediaKind.MOVIE,
        )
    }

/** Problems with an edited channel, as user-facing messages. Empty means it can be saved. */
fun validateDraft(draft: ChannelDef, others: List<ChannelDef>): List<String> {
    val errors = ArrayList<String>()
    if (draft.name.isBlank()) errors += "Give the channel a name"
    if (draft.minimumContentHours?.let { it !in 0..168 } == true) {
        errors += "Minimum content must be 0–168 hours, or use the channel default"
    }
    if (draft.number !in 1..9999) errors += "Channel number must be 1–9999"
    if (draft.number == Lineup.RESTART_NUMBER && draft.id != Lineup.RESTART_ID) {
        errors += "Channel 999 is reserved for From the Top"
    }
    errors += Catalog.validateNumbers(others.filter { it.id != draft.id } + draft)
    return errors
}
