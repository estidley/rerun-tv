package com.rerun.core.schedule

/** Programs start on :00 and :30. Works in every whole- or half-hour UTC offset. */
const val GRID_MS = 30 * 60_000L
const val HOUR_MS = 60 * 60_000L

fun gridFloor(ms: Long): Long = Math.floorDiv(ms, GRID_MS) * GRID_MS

fun hourFloor(ms: Long): Long = Math.floorDiv(ms, HOUR_MS) * HOUR_MS

fun gridCeil(ms: Long): Long = -Math.floorDiv(-ms, GRID_MS) * GRID_MS

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
