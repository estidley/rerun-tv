package com.rerun.core.model

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

enum class ChannelKind { NETWORK, CURATED, THEME, CUSTOM, MUSIC }

enum class PlayOrder { SHUFFLE, IN_ORDER, CHRONOLOGICAL }

private fun norm(s: String) = s.trim().lowercase()
private fun Set<String>.normed() = mapTo(HashSet()) { norm(it) }

/** Title key that ignores case, punctuation and a leading "The" ("Tenchi Muyo!" == "tenchi muyo"). */
fun titleKey(s: String): String =
    s.trim().lowercase().removePrefix("the ").filter { it.isLetterOrDigit() }

/**
 * Deterministic content slice for split channels (MOVIES 1/2/…). Hashing the title keeps an
 * item on the same slice across syncs; [count] is the total number of slices.
 */
fun bucketOf(item: MediaItem, count: Int): Int {
    val title = if (item.kind == MediaKind.EPISODE) item.showTitle ?: item.title else item.title
    return Math.floorMod(titleKey(title).hashCode(), count)
}

/**
 * What a channel plays.
 *
 * Include sources (networks, titles, tmdbIds, labels, collections, genres, franchises) are OR'd; when none
 * are set, everything passes. Filters (excludeGenres, decades, ratings, kinds) are AND'd.
 * [bucketCount] > 1 splits the matching pool into that many deterministic slices; the channel
 * then only plays the slice numbered [bucket] (0-based) — that's how MOVIES 1/2 share a library.
 */
data class Rules(
    val networks: Set<String> = emptySet(),
    val titles: Set<String> = emptySet(),
    val tmdbIds: Set<String> = emptySet(),
    val labels: Set<String> = emptySet(),
    val collections: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val excludeGenres: Set<String> = emptySet(),
    val decades: Set<Int> = emptySet(),
    val ratings: Set<String> = emptySet(),
    val kinds: Set<MediaKind> = setOf(MediaKind.MOVIE, MediaKind.EPISODE),
    val bucket: Int = 0,
    val bucketCount: Int = 1,
    val franchises: Set<String> = emptySet(),
) {
    private val nNetworks by lazy { networks.normed() }
    private val nTitles by lazy { titles.mapTo(HashSet()) { titleKey(it) } }
    private val nTmdb by lazy { tmdbIds.normed() }
    private val nLabels by lazy { labels.normed() }
    private val nCollections by lazy { collections.normed() }
    private val nGenres by lazy { genres.normed() }
    private val nExclude by lazy { excludeGenres.normed() }
    private val nRatings by lazy { ratings.normed() }

    private val hasIncludeSource: Boolean
        get() = networks.isNotEmpty() || titles.isNotEmpty() || tmdbIds.isNotEmpty() ||
            labels.isNotEmpty() || collections.isNotEmpty() || genres.isNotEmpty() || franchises.isNotEmpty()

    fun matches(item: MediaItem, networkId: String? = item.network): Boolean =
        matchesWith(item) { networkId != null && norm(networkId) in nNetworks }

    /** Like [matches], for an item that belongs to several networks (it aired on each one). */
    fun matches(item: MediaItem, networkIds: Set<String>): Boolean =
        matchesWith(item) { networkIds.any { norm(it) in nNetworks } }

    private inline fun matchesWith(item: MediaItem, networkHit: () -> Boolean): Boolean {
        if (item.kind !in kinds) return false
        if (bucketCount > 1 && bucketOf(item, bucketCount) != bucket) return false
        if (nExclude.isNotEmpty() && item.genres.any { norm(it) in nExclude }) return false
        if (decades.isNotEmpty()) {
            val y = item.year ?: return false
            if (y / 10 * 10 !in decades) return false
        }
        if (nRatings.isNotEmpty()) {
            val r = item.contentRating?.let(::norm) ?: return false
            if (r !in nRatings) return false
        }
        if (!hasIncludeSource) return true

        if (franchises.any { com.rerun.core.catalog.ComicCatalog.matches(item, it) }) return true

        if (nNetworks.isNotEmpty() && networkHit()) return true
        val title = if (item.kind == MediaKind.EPISODE) item.showTitle else item.title
        if (nTitles.isNotEmpty() && title != null && titleKey(title) in nTitles) return true
        if (nTmdb.isNotEmpty() && item.tmdbId != null && norm(item.tmdbId) in nTmdb) return true
        if (nLabels.isNotEmpty() && item.labels.any { norm(it) in nLabels }) return true
        if (nCollections.isNotEmpty() && item.collections.any { norm(it) in nCollections }) return true
        if (nGenres.isNotEmpty() && item.genres.any { norm(it) in nGenres }) return true
        return false
    }
}

/**
 * A time window that narrows a channel's pool. `end < start` means overnight; the block then
 * belongs to the day it starts on (Fri 21:00–03:00 covers early Saturday morning).
 */
data class TimeBlock(
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val rules: Rules,
) {
    fun activeAt(t: ZonedDateTime): Boolean {
        val time = t.toLocalTime()
        return if (start <= end) {
            t.dayOfWeek in days && time >= start && time < end
        } else {
            (t.dayOfWeek in days && time >= start) ||
                (t.dayOfWeek.minus(1) in days && time < end)
        }
    }
}

data class ChannelDef(
    val id: String,
    val number: Int,
    val name: String,
    val kind: ChannelKind,
    val rules: Rules,
    val order: PlayOrder = PlayOrder.SHUFFLE,
    val blocks: List<TimeBlock> = emptyList(),
    val kidsSafe: Boolean = false,
    val hidden: Boolean = false,
    val movieEveryMs: Long = 6 * 3_600_000L,
    val minimumContentHours: Int? = null,
    val userRenamed: Boolean = false,
)
