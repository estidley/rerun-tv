package com.rerun.core.tmdb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** A page of `/discover/tv` or `/discover/movie` results. */
data class DiscoverPage(val ids: List<Long>, val page: Int, val totalPages: Int)

/** Parses the TMDB responses the refresh tool needs. Bad input gives empty results, not errors. */
object TmdbParse {
    private val json = Json { ignoreUnknownKeys = true }

    private fun obj(text: String): JsonObject? = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull

    /**
     * Reads a TMDB daily ID export (`tv_network_ids_*.json`, `production_company_ids_*.json`):
     * one JSON object per line with `id` and `name`.
     */
    fun exportEntries(lines: Sequence<String>): List<TmdbSource> = lines.mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val o = obj(line) ?: return@mapNotNull null
        val id = o.long("id") ?: return@mapNotNull null
        TmdbSource(id, o.str("name")?.trim() ?: return@mapNotNull null)
    }.toList()

    /** Export entries whose name equals one of [names], ignoring case and surrounding space. */
    fun named(entries: List<TmdbSource>, names: Collection<String>): List<TmdbSource> {
        val wanted = names.mapTo(HashSet()) { it.trim().lowercase() }
        return entries.filter { it.name.lowercase() in wanted }
    }

    /** `origin_country` from `/network/{id}` or `/company/{id}`; blank becomes null. */
    fun originCountry(text: String): String? = obj(text)?.str("origin_country")?.trim()?.takeIf { it.isNotEmpty() }

    fun discoverPage(text: String): DiscoverPage {
        val o = obj(text) ?: return DiscoverPage(emptyList(), 1, 0)
        val ids = (o["results"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.long("id") }
        return DiscoverPage(ids, o.int("page") ?: 1, o.int("total_pages") ?: 0)
    }
}
