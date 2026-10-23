package com.rerun.core.catalog

import com.rerun.core.model.ChannelDef
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
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
                networks = items.mapNotNull { Resolver.networkOf(it) }.distinct().sortedBy { id -> Catalog.networks.firstOrNull { it.id == id }?.number ?: 999 },
            )
        }
    }
}

/** Problems with an edited channel, as user-facing messages. Empty means it can be saved. */
fun validateDraft(draft: ChannelDef, others: List<ChannelDef>): List<String> {
    val errors = ArrayList<String>()
    if (draft.name.isBlank()) errors += "Give the channel a name"
    if (draft.number !in 1..999) errors += "Channel number must be 1–999"
    errors += Catalog.validateNumbers(others.filter { it.id != draft.id } + draft)
    return errors
}
