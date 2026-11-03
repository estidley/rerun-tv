package com.rerun.tv.data

import com.rerun.core.catalog.Lineup
import com.rerun.core.model.MediaItem
import com.rerun.core.schedule.EntryKind
import com.rerun.core.schedule.ScheduleEntry
import org.json.JSONArray
import org.json.JSONObject

internal const val RESTART_SESSION_KEY = "restart_session"

data class RestartSession(
    val id: String,
    val keys: List<String>,
    val index: Int = 0,
    val positionMs: Long = 0,
    val returnChannel: String,
    val paused: Boolean = true,
) {
    val itemKey: String get() = keys[index]

    fun encode(): String = JSONObject().put("id", id).put("keys", JSONArray(keys))
        .put("index", index).put("positionMs", positionMs).put("returnChannel", returnChannel)
        .put("paused", paused).toString()

    companion object {
        fun decode(text: String): RestartSession {
            val json = JSONObject(text)
            val keys = json.getJSONArray("keys")
            return RestartSession(
                json.getString("id"), List(keys.length()) { keys.getString(it) },
                json.getInt("index"), json.getLong("positionMs"), json.getString("returnChannel"),
                json.getBoolean("paused"),
            ).also {
                require(it.index in it.keys.indices && it.positionMs >= 0) { "Invalid saved start-over progress" }
            }
        }
    }
}

internal fun RestartSession.entries(items: Map<String, MediaItem>, nowMs: Long): List<ScheduleEntry> {
    var cursor = nowMs - positionMs
    return keys.drop(index).mapNotNull { key ->
        items[key]?.let { item ->
            val end = maxOf(cursor + item.durationMs.coerceAtLeast(1), nowMs + 1)
            ScheduleEntry(Lineup.RESTART_ID, cursor, end, EntryKind.PROGRAM, key).also { cursor = end }
        }
    }
}
