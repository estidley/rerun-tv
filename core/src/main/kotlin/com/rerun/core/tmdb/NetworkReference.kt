package com.rerun.core.tmdb

import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A TMDB network or company the refresh tool matched to a channel. */
data class TmdbSource(val id: Long, val name: String)

/** What TMDB says aired on one built-in channel's real network. */
data class ChannelReference(
    val channelId: String,
    val realName: String,
    val networks: List<TmdbSource>,
    val companies: List<TmdbSource>,
    /** TMDB TV ids of every show that aired on one of [networks]. */
    val shows: Set<Long>,
    /** TMDB movie ids made by one of [companies]. */
    val movies: Set<Long>,
)

/**
 * Lists of what actually aired on each real network, built from TMDB at build time by
 * `tools/tmdb` and bundled as a resource. The app never calls TMDB and never sees a token.
 * When the resource is missing, the reference is empty and channels fall back to the network
 * Plex reports.
 */
class NetworkReference(val channels: List<ChannelReference>, val generated: String? = null, val country: String? = null) {
    private val byShow: Map<Long, Set<String>> = index { it.shows }
    private val byMovie: Map<Long, Set<String>> = index { it.movies }

    val isEmpty: Boolean get() = byShow.isEmpty() && byMovie.isEmpty()

    private fun index(ids: (ChannelReference) -> Set<Long>): Map<Long, Set<String>> {
        val out = HashMap<Long, MutableSet<String>>()
        for (c in channels) for (id in ids(c)) out.getOrPut(id) { LinkedHashSet() } += c.channelId
        return out
    }

    /** Channel ids whose real network aired [item]. Episodes use their show's TMDB id. */
    fun channelsFor(item: MediaItem): Set<String> {
        val id = item.tmdbId?.trim()?.toLongOrNull() ?: return emptySet()
        return when (item.kind) {
            MediaKind.EPISODE -> byShow[id].orEmpty()
            MediaKind.MOVIE -> byMovie[id].orEmpty()
            MediaKind.TRACK -> emptySet()
        }
    }

    fun toJson(): String = json.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("version", VERSION)
        generated?.let { put("generated", it) }
        country?.let { put("country", it) }
        put("channels", buildJsonArray {
            for (c in channels) add(buildJsonObject {
                put("id", c.channelId)
                put("realName", c.realName)
                put("networks", sources(c.networks))
                put("companies", sources(c.companies))
                put("shows", buildJsonArray { c.shows.sorted().forEach { add(JsonPrimitive(it)) } })
                put("movies", buildJsonArray { c.movies.sorted().forEach { add(JsonPrimitive(it)) } })
            })
        })
    })

    companion object {
        const val VERSION = 1

        /** Classpath location of the bundled reference, written by `tools/tmdb`. */
        const val RESOURCE = "rerun/tmdb-networks.json"

        val EMPTY = NetworkReference(emptyList())

        private val json = Json { ignoreUnknownKeys = true }

        @Volatile
        private var loaded: NetworkReference? = null

        /** The bundled reference, loaded once. Tests may replace it with [use]. */
        val current: NetworkReference
            get() = loaded ?: synchronized(this) { loaded ?: load().also { loaded = it } }

        /** Replaces [current]; pass null to reload the bundled resource on next use. */
        fun use(reference: NetworkReference?) = synchronized(this) { loaded = reference }

        private fun load(): NetworkReference {
            if (System.getProperty("rerun.tmdb") == "off") return EMPTY
            val stream = NetworkReference::class.java.classLoader?.getResourceAsStream(RESOURCE) ?: return EMPTY
            return runCatching { stream.use { parse(it.readBytes().decodeToString()) } }.getOrDefault(EMPTY)
        }

        /** Reads [toJson] output. A newer, unreadable or partly malformed file gives [EMPTY] rather than an error. */
        fun parse(text: String): NetworkReference = try {
            parseStrict(text)
        } catch (_: MalformedReference) {
            EMPTY
        }

        private class MalformedReference : RuntimeException()

        private fun malformed(): Nothing = throw MalformedReference()

        private fun parseStrict(text: String): NetworkReference {
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return EMPTY
            val version = (root["version"] as? JsonPrimitive)?.longOrNull ?: return EMPTY
            if (version != VERSION.toLong()) return EMPTY
            val channels = (root["channels"] as? JsonArray ?: malformed()).map { e ->
                val o = e as? JsonObject ?: malformed()
                ChannelReference(
                    channelId = o.str("id") ?: malformed(),
                    realName = o.str("realName").orEmpty(),
                    networks = o.sources("networks"),
                    companies = o.sources("companies"),
                    shows = o.ids("shows"),
                    movies = o.ids("movies"),
                )
            }
            return NetworkReference(channels, root.str("generated"), root.str("country"))
        }

        private fun sources(list: List<TmdbSource>) = buildJsonArray {
            for (s in list) add(buildJsonObject { put("id", s.id); put("name", s.name) })
        }

        private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.ids(k: String): Set<Long> =
            (this[k] as? JsonArray ?: malformed()).mapTo(HashSet()) { (it as? JsonPrimitive)?.longOrNull ?: malformed() }

        private fun JsonObject.sources(k: String): List<TmdbSource> = (this[k] as? JsonArray ?: malformed()).map { e ->
            val o = e as? JsonObject ?: malformed()
            val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: malformed()
            TmdbSource(id, o.str("name").orEmpty())
        }
    }
}
