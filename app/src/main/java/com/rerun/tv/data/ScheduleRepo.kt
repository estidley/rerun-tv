package com.rerun.tv.data

import com.rerun.core.codec.StateCodec
import com.rerun.core.model.MediaItem
import com.rerun.core.resolve.Resolver
import com.rerun.core.schedule.ChannelState
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

    private suspend fun topUp(row: ChannelRow, nowMs: Long) {
        val last = db.entries().lastEnd(row.id)
        if (!needsTopUp(last, nowMs)) return
        val def = row.def
        val pool = Resolver.pool(def, lib().values.toList())
        val state = db.states().get(row.id)?.let { StateCodec.decode(it.json) } ?: ChannelState(seed = row.id.hashCode().toLong())
        val ext = scheduler.extend(def, pool, state, last, nowMs)
        if (ext.entries.isNotEmpty()) db.entries().insertAll(ext.entries.map(EntryRow::of))
        db.states().put(StateRow(row.id, StateCodec.encode(ext.state)))
    }

    suspend fun entries(channelId: String, fromMs: Long, toMs: Long): List<ScheduleEntry> =
        db.entries().range(channelId, fromMs, toMs).map { it.toEntry() }

    suspend fun allEntries(fromMs: Long, toMs: Long): Map<String, List<ScheduleEntry>> =
        db.entries().rangeAll(fromMs, toMs).map { it.toEntry() }.groupBy { it.channelId }

    /** After a channel is edited: keep what's on now, rebuild everything after it. */
    suspend fun resetFuture(channelId: String, nowMs: Long) = mutex.withLock {
        val current = db.entries().range(channelId, nowMs, nowMs + 1).firstOrNull()
        db.entries().deleteFrom(channelId, current?.endMs ?: nowMs)
        db.channels().byId(channelId)?.let { topUp(it, nowMs) }
    }

    suspend fun saveChannel(row: ChannelRow) = db.channels().upsert(row)

    suspend fun deleteChannel(id: String) {
        db.channels().delete(id)
        db.entries().deleteChannel(id)
        db.states().delete(id)
    }

    private companion object {
        const val KEEP_PAST_MS = 6 * 3_600_000L
    }
}
