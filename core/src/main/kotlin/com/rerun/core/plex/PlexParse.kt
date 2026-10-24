package com.rerun.core.plex

import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.model.ShowMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

data class Pin(val id: Long, val code: String, val authToken: String?)
data class PlexConnection(val uri: String, val local: Boolean, val relay: Boolean)
data class PlexServer(val name: String, val clientId: String, val accessToken: String?, val connections: List<PlexConnection>)
data class Section(val key: String, val type: String, val title: String)

data class SubtitleStream(
    val id: Int,
    val codec: String?,
    val key: String?,
    val language: String?,
    val selected: Boolean,
) {
    val mimeType: String? get() = when (codec?.lowercase()) {
        "srt" -> "application/x-subrip"
        "ass", "ssa" -> "text/x-ssa"
        "vtt", "webvtt" -> "text/vtt"
        "ttml" -> "application/ttml+xml"
        "mov_text", "tx3g" -> "application/x-quicktime-tx3g"
        else -> null
    }
}

/** Turns Plex JSON responses into core models. Missing fields become null/empty, never errors. */
object PlexParse {
    private val json = Json { ignoreUnknownKeys = true }

    private fun root(text: String): JsonElement = json.parseToJsonElement(text)
    private fun container(text: String): JsonObject = (root(text) as JsonObject)["MediaContainer"] as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull ?: str(k)?.toIntOrNull()
    private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull ?: str(k)?.toLongOrNull()
    private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false
    private fun JsonObject.arr(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    private fun JsonObject.tags(k: String): Set<String> = arr(k).mapNotNullTo(LinkedHashSet()) { it.str("tag") }
    private fun JsonObject.tmdb(): String? =
        arr("Guid").mapNotNull { it.str("id") }.firstOrNull { it.startsWith("tmdb://") }?.removePrefix("tmdb://")

    fun pin(text: String): Pin {
        val o = root(text) as JsonObject
        return Pin(o.long("id") ?: 0, o.str("code").orEmpty(), o.str("authToken"))
    }

    fun servers(text: String): List<PlexServer> =
        (root(text) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { o -> o.str("provides").orEmpty().split(',').any { it.trim() == "server" } }
            .map { o ->
                PlexServer(
                    name = o.str("name").orEmpty(),
                    clientId = o.str("clientIdentifier").orEmpty(),
                    accessToken = o.str("accessToken"),
                    connections = o.arr("connections").mapNotNull { c ->
                        c.str("uri")?.let { PlexConnection(it, c.bool("local"), c.bool("relay")) }
                    },
                )
            }

    fun sections(text: String): List<Section> =
        container(text).arr("Directory").map { Section(it.str("key").orEmpty(), it.str("type").orEmpty(), it.str("title").orEmpty()) }

    fun totalSize(text: String): Int = container(text).let { it.int("totalSize") ?: it.int("size") ?: 0 }

    /** Streams for the same first media/part that playback uses. */
    fun subtitles(text: String): List<SubtitleStream> =
        container(text).arr("Metadata").firstOrNull()?.arr("Media")?.firstOrNull()
            ?.arr("Part")?.firstOrNull()?.arr("Stream").orEmpty()
            .filter { it.int("streamType") == 3 }
            .mapNotNull { s ->
                val id = s.int("id") ?: return@mapNotNull null
                SubtitleStream(id, s.str("codec"), s.str("key"), s.str("languageCode"), s.bool("selected") || s.int("selected") == 1)
            }

    fun movies(text: String): List<MediaItem> = container(text).arr("Metadata").map { m ->
        val media = m.arr("Media").firstOrNull()
        MediaItem(
            key = m.str("ratingKey").orEmpty(), kind = MediaKind.MOVIE, title = m.str("title").orEmpty(),
            year = m.int("year"), durationMs = m.long("duration") ?: media?.long("duration") ?: 0,
            genres = m.tags("Genre"), network = null, studio = m.str("studio"), contentRating = m.str("contentRating"),
            showKey = null, showTitle = null, season = null, episode = null, tmdbId = m.tmdb(),
            labels = m.tags("Label"), collections = m.tags("Collection"),
            partKey = media?.arr("Part")?.firstOrNull()?.str("key"), container = media?.str("container"),
            videoCodec = media?.str("videoCodec"), audioCodec = media?.str("audioCodec"), thumb = m.str("thumb"),
        )
    }

    fun shows(text: String): List<ShowMeta> = container(text).arr("Metadata").map { s ->
        ShowMeta(
            key = s.str("ratingKey").orEmpty(), title = s.str("title").orEmpty(), year = s.int("year"),
            studio = s.str("studio"), genres = s.tags("Genre"), contentRating = s.str("contentRating"),
            tmdbId = s.tmdb(), labels = s.tags("Label"), collections = s.tags("Collection"),
        )
    }

    fun episodes(text: String, shows: Map<String, ShowMeta>): List<MediaItem> = container(text).arr("Metadata").map { e ->
        val showKey = e.str("grandparentRatingKey")
        val show = showKey?.let { shows[it] }
        val media = e.arr("Media").firstOrNull()
        MediaItem(
            key = e.str("ratingKey").orEmpty(), kind = MediaKind.EPISODE, title = e.str("title").orEmpty(),
            year = e.int("year") ?: show?.year, durationMs = e.long("duration") ?: media?.long("duration") ?: 0,
            genres = show?.genres.orEmpty(), network = show?.studio, studio = show?.studio,
            contentRating = e.str("contentRating") ?: show?.contentRating,
            showKey = showKey, showTitle = e.str("grandparentTitle") ?: show?.title,
            season = e.int("parentIndex"), episode = e.int("index"), tmdbId = show?.tmdbId,
            labels = show?.labels.orEmpty(), collections = show?.collections.orEmpty(),
            partKey = media?.arr("Part")?.firstOrNull()?.str("key"), container = media?.str("container"),
            videoCodec = media?.str("videoCodec"), audioCodec = media?.str("audioCodec"),
            thumb = e.str("thumb") ?: e.str("grandparentThumb"),
        )
    }

    /** Tracks from an artist library section (`type=10`). Untagged tracks are skipped. */
    fun tracks(text: String): List<MediaItem> = container(text).arr("Metadata").mapNotNull { t ->
        val genres = t.tags("Genre")
        if (genres.isEmpty()) return@mapNotNull null
        val media = t.arr("Media").firstOrNull()
        MediaItem(
            key = t.str("ratingKey").orEmpty(),
            kind = MediaKind.TRACK,
            title = t.str("title").orEmpty(),
            year = t.int("year"),
            durationMs = t.long("duration") ?: media?.long("duration") ?: 0,
            genres = genres,
            network = null,
            studio = t.str("grandparentTitle"),
            contentRating = null,
            showKey = t.str("parentRatingKey"),
            showTitle = t.str("parentTitle"),
            season = t.int("parentIndex"),
            episode = t.int("index"),
            tmdbId = null,
            labels = emptySet(),
            collections = emptySet(),
            partKey = media?.arr("Part")?.firstOrNull()?.str("key"),
            container = media?.str("container"),
            videoCodec = null,
            audioCodec = media?.str("audioCodec"),
            thumb = t.str("parentThumb") ?: t.str("thumb") ?: t.str("grandparentThumb"),
        )
    }
}
