package com.rerun.tv.data

import com.rerun.core.catalog.Lineup
import com.rerun.core.codec.StateCodec
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.model.Rules
import com.rerun.core.resolve.Resolver
import com.rerun.core.schedule.ChannelState
import com.rerun.core.schedule.EntryKind
import com.rerun.core.schedule.ScheduleEntry
import com.rerun.core.schedule.Scheduler
import com.rerun.core.schedule.needsTopUp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps every active channel's locked schedule at least a day ahead. */
class ScheduleRepo(private val db: RerunDb, private val scheduler: Scheduler) {
    private val mutex = Mutex()
    @Volatile private var library: Map<String, MediaItem>? = null

    fun invalidateLibrary() { library = null }

    private suspend fun lib(): Map<String, MediaItem> =
        library ?: db.items().all().map { it.toItem() }.associateBy { it.key }.also { library = it }

    suspend fun item(key: String?): MediaItem? = key?.let { lib()[it] }

    suspend fun libraryItems(): List<MediaItem> = lib().values.toList()

    suspend fun allChannelRows(): List<ChannelRow> = db.channels().all()

    fun activeChannels(): Flow<List<ChannelRow>> = db.channels().observeActive()
    fun allChannels(): Flow<List<ChannelRow>> = db.channels().observeAll()
    suspend fun channel(id: String): ChannelRow? = db.channels().byId(id)
    suspend fun active(): List<ChannelRow> = db.channels().active()

    /** Tops up any active channel with less than a day of schedule left. */
    suspend fun ensure(nowMs: Long) = mutex.withLock {
        db.entries().pruneBefore(nowMs - KEEP_PAST_MS)
        for (row in db.channels().active()) topUp(row, nowMs)
    }

    /** Tops up a single channel (used right before tuning to it). */
    suspend fun ensure(channelId: String, nowMs: Long) = mutex.withLock {
        db.channels().byId(channelId)?.let { topUp(it, nowMs) }
    }

    /** Plants one episode on the hidden "From the Top" channel, starting now at 0:00. */
    suspend fun airFromStart(item: MediaItem, nowMs: Long) = mutex.withLock {
        val def = ChannelDef(
            id = Lineup.RESTART_ID,
            number = Lineup.RESTART_NUMBER,
            name = "From the Top",
            kind = ChannelKind.CUSTOM,
            rules = Rules(kinds = setOf(MediaKind.EPISODE)),
            hidden = true,
        )
        db.channels().upsert(ChannelRow.of(def, builtIn = true, dormant = false, poolMs = item.durationMs))
        db.entries().deleteChannel(Lineup.RESTART_ID)
        db.states().delete(Lineup.RESTART_ID)
        val end = nowMs + item.durationMs.coerceAtLeast(1L)
        db.entries().insertAll(listOf(EntryRow.of(ScheduleEntry(Lineup.RESTART_ID, nowMs, end, EntryKind.PROGRAM, item.key))))
    }

    private suspend fun topUp(row: ChannelRow, nowMs: Long) {
        if (row.id == Lineup.RESTART_ID) return
        if (Lineup.isWest(row.id)) {
            mirrorWest(row, nowMs)
            return
        }
        val last = db.entries().lastEnd(row.id)
        if (!needsTopUp(last, nowMs)) return
        val def = row.def
        val pool = Resolver.pool(def, lib().values.toList())
        val state = db.states().get(row.id)?.let { StateCodec.decode(it.json) } ?: ChannelState(seed = row.id.hashCode().toLong())
        val ext = scheduler.extend(def, pool, state, last, nowMs)
        if (ext.entries.isNotEmpty()) db.entries().insertAll(ext.entries.map(EntryRow::of))
        db.states().put(StateRow(row.id, StateCodec.encode(ext.state)))
    }

    /**
     * West plays East's stored schedule three hours later and does not shuffle its own.
     * Hours East has not aired stay empty, including the first three hours of a new feed.
     */
    private suspend fun mirrorWest(row: ChannelRow, nowMs: Long) {
        val east = db.channels().byId(Lineup.eastOf(row.id)) ?: return
        if (Lineup.isWest(east.id)) return
        topUp(east, nowMs)
        val eastLast = db.entries().lastEnd(east.id) ?: return
        val coveredUntil = eastLast + Lineup.WEST_LAG_MS
        val westLast = db.entries().lastEnd(row.id)
        if (westLast != null && westLast >= coveredUntil) return
        val from = nowMs - KEEP_PAST_MS - Lineup.WEST_LAG_MS
        val shifted = Lineup.shift(
            db.entries().range(east.id, from, eastLast + 1).map { it.toEntry() },
            row.id,
            Lineup.WEST_LAG_MS,
        ).filter { it.endMs > nowMs - KEEP_PAST_MS }
        db.entries().deleteChannel(row.id)
        if (shifted.isNotEmpty()) db.entries().insertAll(shifted.map(EntryRow::of))
    }

    suspend fun entries(channelId: String, fromMs: Long, toMs: Long): List<ScheduleEntry> =
        db.entries().range(channelId, fromMs, toMs).map { it.toEntry() }

    suspend fun allEntries(fromMs: Long, toMs: Long): Map<String, List<ScheduleEntry>> =
        db.entries().rangeAll(fromMs, toMs).map { it.toEntry() }.groupBy { it.channelId }

    /**
     * After a channel is edited: keep what's on now, rebuild everything after it.
     * West is derived, so an East edit drops the shifted copy and builds it again.
     */
    suspend fun resetFuture(channelId: String, nowMs: Long) = mutex.withLock {
        if (!Lineup.isWest(channelId)) {
            val current = db.entries().range(channelId, nowMs, nowMs + 1).firstOrNull()
            db.entries().deleteFrom(channelId, current?.endMs ?: nowMs)
            db.channels().byId(channelId)?.let { topUp(it, nowMs) }
        }
        val west = db.channels().byId(Lineup.westId(channelId)) ?: return@withLock
        db.entries().deleteChannel(west.id)
        mirrorWest(west, nowMs)
    }

    suspend fun saveChannel(row: ChannelRow) = mutex.withLock {
        db.channels().upsert(row)
        if (row.hidden && !Lineup.isWest(row.id)) removeChannel(Lineup.westId(row.id))
    }

    suspend fun deleteChannel(id: String) = mutex.withLock {
        removeChannel(id)
        if (!Lineup.isWest(id)) removeChannel(Lineup.westId(id))
    }

    private suspend fun removeChannel(id: String) {
        db.channels().delete(id)
        db.entries().deleteChannel(id)
        db.states().delete(id)
    }

    private companion object {
        const val KEEP_PAST_MS = 6 * 3_600_000L
    }
}
