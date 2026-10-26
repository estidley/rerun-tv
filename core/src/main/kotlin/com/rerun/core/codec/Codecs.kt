package com.rerun.core.codec

import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaKind
import com.rerun.core.model.PlayOrder
import com.rerun.core.model.Rules
import com.rerun.core.model.TimeBlock
import com.rerun.core.schedule.ChannelState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.LocalTime

private val json = Json

private fun strings(values: Collection<String>): JsonArray = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }
private fun JsonElement?.stringSet(): Set<String> =
    (this as? JsonArray)?.mapTo(LinkedHashSet()) { it.jsonPrimitive.content } ?: emptySet()
private fun JsonElement?.stringList(): List<String> =
    (this as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()

/** Set<String> ↔ JSON array string (for Room columns). */
object SetCodec {
    fun encode(s: Set<String>): String = strings(s).toString()
    fun decode(text: String): Set<String> = if (text.isBlank()) emptySet() else json.parseToJsonElement(text).stringSet()
}

/** ChannelDef ↔ JSON string. */
object ChannelCodec {
    fun encode(def: ChannelDef): String = buildJsonObject {
        put("id", def.id); put("number", def.number); put("name", def.name); put("kind", def.kind.name)
        put("rules", rules(def.rules)); put("order", def.order.name)
        put("blocks", buildJsonArray {
            def.blocks.forEach { b ->
                add(buildJsonObject {
                    put("days", strings(b.days.map { it.name }))
                    put("start", b.start.toString()); put("end", b.end.toString())
                    put("rules", rules(b.rules))
                })
            }
        })
        put("kidsSafe", def.kidsSafe); put("hidden", def.hidden); put("movieEveryMs", def.movieEveryMs)
        def.minimumContentHours?.let { put("minimumContentHours", it) }
    }.toString()

    fun decode(text: String): ChannelDef {
        val o = json.parseToJsonElement(text).jsonObject
        return ChannelDef(
            id = o.getValue("id").jsonPrimitive.content,
            number = o.getValue("number").jsonPrimitive.int,
            name = o.getValue("name").jsonPrimitive.content,
            kind = ChannelKind.valueOf(o.getValue("kind").jsonPrimitive.content),
            rules = rules(o.getValue("rules").jsonObject),
            order = PlayOrder.valueOf(o["order"]?.jsonPrimitive?.content ?: PlayOrder.SHUFFLE.name),
            blocks = (o["blocks"] as? JsonArray).orEmpty().map { e ->
                val b = e.jsonObject
                TimeBlock(
                    days = b["days"].stringList().mapTo(LinkedHashSet()) { DayOfWeek.valueOf(it) },
                    start = LocalTime.parse(b.getValue("start").jsonPrimitive.content),
                    end = LocalTime.parse(b.getValue("end").jsonPrimitive.content),
                    rules = rules(b.getValue("rules").jsonObject),
                )
            },
            kidsSafe = o["kidsSafe"]?.jsonPrimitive?.boolean ?: false,
            hidden = o["hidden"]?.jsonPrimitive?.boolean ?: false,
            movieEveryMs = o["movieEveryMs"]?.jsonPrimitive?.long ?: 6 * 3_600_000L,
            minimumContentHours = o["minimumContentHours"]?.jsonPrimitive?.int,
        )
    }

    private fun rules(r: Rules): JsonObject = buildJsonObject {
        put("networks", strings(r.networks)); put("titles", strings(r.titles)); put("tmdbIds", strings(r.tmdbIds))
        put("labels", strings(r.labels)); put("collections", strings(r.collections)); put("genres", strings(r.genres))
        put("excludeGenres", strings(r.excludeGenres)); put("decades", strings(r.decades.map { it.toString() }))
        put("ratings", strings(r.ratings)); put("kinds", strings(r.kinds.map { it.name }))
        put("bucket", r.bucket); put("bucketCount", r.bucketCount)
    }

    private fun rules(o: JsonObject): Rules = Rules(
        networks = o["networks"].stringSet(), titles = o["titles"].stringSet(), tmdbIds = o["tmdbIds"].stringSet(),
        labels = o["labels"].stringSet(), collections = o["collections"].stringSet(), genres = o["genres"].stringSet(),
        excludeGenres = o["excludeGenres"].stringSet(),
        decades = o["decades"].stringList().mapTo(LinkedHashSet()) { it.toInt() },
        ratings = o["ratings"].stringSet(),
        kinds = o["kinds"]?.let { k -> k.stringList().mapTo(LinkedHashSet()) { MediaKind.valueOf(it) } }
            ?: setOf(MediaKind.MOVIE, MediaKind.EPISODE),
        bucket = o["bucket"]?.jsonPrimitive?.int ?: 0,
        bucketCount = o["bucketCount"]?.jsonPrimitive?.int ?: 1,
    )
}

/** ChannelState ↔ JSON string. */
object StateCodec {
    fun encode(s: ChannelState): String = buildJsonObject {
        put("seed", s.seed); put("deck", strings(s.deck)); put("recent", strings(s.recent))
        put("episodePointers", buildJsonObject { s.episodePointers.forEach { (k, v) -> put(k, v) } })
        put("msSinceMovie", s.msSinceMovie); put("rngCalls", s.rngCalls)
    }.toString()

    fun decode(text: String): ChannelState {
        val o = json.parseToJsonElement(text).jsonObject
        return ChannelState(
            seed = o.getValue("seed").jsonPrimitive.long,
            deck = o["deck"].stringList(),
            recent = o["recent"].stringList(),
            episodePointers = (o["episodePointers"] as? JsonObject).orEmpty().mapValues { it.value.jsonPrimitive.int },
            msSinceMovie = o["msSinceMovie"]?.jsonPrimitive?.long ?: 0,
            rngCalls = o["rngCalls"]?.jsonPrimitive?.long ?: 0,
        )
    }
}
