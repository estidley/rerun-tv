package com.rerun.core.schedule

import java.time.Instant
import java.time.ZoneId

/** Programs start at local :00, :15, :30, or :45. */
const val GRID_MS = 15 * 60_000L

fun gridFloor(ms: Long, zone: ZoneId = ZoneId.of("UTC")): Long {
    val local = Instant.ofEpochMilli(ms).atZone(zone)
    return local.withMinute((local.minute / 15) * 15).withSecond(0).withNano(0)
        .toInstant().toEpochMilli()
}

fun gridCeil(ms: Long, zone: ZoneId = ZoneId.of("UTC")): Long {
    val local = Instant.ofEpochMilli(ms).atZone(zone)
    if (local.minute % 15 == 0 && local.second == 0 && local.nano == 0) return ms
    return Instant.ofEpochMilli(gridFloor(ms, zone)).atZone(zone).plusMinutes(15)
        .toInstant().toEpochMilli()
}

enum class EntryKind { PROGRAM, FILLER }

/** One slot in a channel's locked schedule. Times are UTC epoch ms; `endMs` is exclusive. */
data class ScheduleEntry(
    val channelId: String,
    val startMs: Long,
    val endMs: Long,
    val kind: EntryKind,
    val itemKey: String?,
)

/** Everything the scheduler needs to continue a channel exactly where it left off. */
data class ChannelState(
    val seed: Long,
    val deck: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    val episodePointers: Map<String, Int> = emptyMap(),
    val msSinceMovie: Long = 0,
    val rngCalls: Long = 0,
)
