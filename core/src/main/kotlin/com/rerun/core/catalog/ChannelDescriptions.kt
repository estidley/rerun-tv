package com.rerun.core.catalog

import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaKind

/** Plain-language channel criteria derived from the same definitions used for playback. */
object ChannelDescriptions {
    fun of(def: ChannelDef): String {
        if (def.id == Lineup.ADS_ID) return "Commercials from the enabled ad library, back-to-back."
        if (def.rules.unmatchedOnly) {
            return "Otherwise-unmatched movies and episodes; excludes stock channels and eligible generated pools."
        }
        val rules = def.rules
        val simpleMusic = def.kind == ChannelKind.MUSIC && def.blocks.isEmpty() &&
            rules.kinds == setOf(MediaKind.TRACK) && rules.genres.size == 1 &&
            rules.networks.isEmpty() && rules.titles.isEmpty() && rules.tmdbIds.isEmpty() &&
            rules.labels.isEmpty() && rules.collections.isEmpty() && rules.franchises.isEmpty() &&
            rules.decades.isEmpty() && rules.languages.isEmpty() && rules.ratings.isEmpty() && rules.bucketCount == 1
        if (simpleMusic) {
            val genre = rules.genres.single()
            return "Music tracks tagged $genre" +
                (if (rules.excludeGenres.isEmpty()) "." else "; spoken-word and other excluded genres are skipped.")
        }
        val sources = mutableListOf<String>()
        if (rules.networks.isNotEmpty()) {
            sources += rules.networks.sorted().joinToString(" or ") { id ->
                Catalog.networks.firstOrNull { it.id == id }?.realName ?: id
            }.let { "Network: $it" }
        }
        if (rules.genres.isNotEmpty()) sources += "Genres: ${rules.genres.sorted().joinToString(" or ")}"
        if (rules.titles.isNotEmpty()) sources += "Shows: ${names(rules.titles)}"
        if (rules.collections.isNotEmpty()) sources += "Collections: ${names(rules.collections)}"
        if (rules.labels.isNotEmpty()) sources += "Labels: ${names(rules.labels)}"
        if (rules.tmdbIds.isNotEmpty()) sources += "TMDB IDs: ${names(rules.tmdbIds)}"
        if (rules.franchises.isNotEmpty()) {
            sources += "Franchise metadata: ${rules.franchises.sorted().joinToString(" or ") { it.uppercase() }}"
        }
        val media = when (rules.kinds) {
            setOf(MediaKind.MOVIE) -> "movies"
            setOf(MediaKind.EPISODE) -> "episodes"
            setOf(MediaKind.TRACK) -> "music tracks"
            else -> "movies and episodes"
        }
        val filters = mutableListOf<String>()
        if (rules.decades.isNotEmpty()) filters += "release ${decades(rules.decades)}"
        if (rules.languages.isNotEmpty()) filters += "language ${rules.languages.sorted().joinToString(" or ") { it.uppercase() }}"
        if (rules.ratings.isNotEmpty()) filters += "ratings ${rules.ratings.sorted().joinToString(" or ")}"
        if (rules.excludeGenres.isNotEmpty()) filters += "exclude genres ${names(rules.excludeGenres)}"
        if (rules.bucketCount > 1) filters += "library slice ${rules.bucket + 1} of ${rules.bucketCount}"
        val blockTitles = def.blocks.sumOf { it.rules.titles.size }
        if (def.blocks.isNotEmpty()) {
            filters += "${def.blocks.size} scheduled block${if (def.blocks.size == 1) "" else "s"}" +
                if (blockTitles > 0) " with $blockTitles named shows" else ""
        }
        return (if (sources.isEmpty()) "All included libraries" else sources.joinToString(" or ")) +
            " · $media" + (if (filters.isEmpty()) "." else "; ${filters.joinToString("; ")}.")
    }

    private fun names(values: Set<String>): String {
        val sorted = values.sorted()
        return sorted.take(3).joinToString(" or ") + (if (sorted.size > 3) " or ${sorted.size - 3} more" else "")
    }

    private fun decades(values: Set<Int>): String {
        val sorted = values.sorted()
        return if (sorted.size > 2 && sorted.zipWithNext().all { (a, b) -> b - a == 10 }) {
            "${sorted.first()}s–${sorted.last()}s"
        } else sorted.joinToString(" or ") { "${it}s" }
    }
}
