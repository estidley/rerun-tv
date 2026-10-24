package com.rerun.tv.data

import com.rerun.core.catalog.Lineup
import com.rerun.core.catalog.StoredChannel
import androidx.room.withTransaction
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps every active channel's locked schedule at least a day ahead. */
class ScheduleRepo(private val db: RerunDb, private val scheduler: Scheduler) {
    private val mutex = Mutex()
    @Volatile private var library: Map<String, MediaItem>? = null
    private val _guideRevision = MutableStateFlow(0L)
    val guideRevision = _guideRevision.asStateFlow()

    fun invalidateLibrary() { library = null }

    suspend fun signOut(settings: Settings) = mutex.withLock {
        settings.signOut()
        invalidateLibrary()
        _guideRevision.value++
    }

    private suspend fun lib(): Map<String, MediaItem> =
        library ?: db.items().all().map { it.toItem() }.associateBy { it.key }.also { library = it }

    suspend fun item(key: String?): MediaItem? = key?.let { lib()[it] }

    suspend fun libraryItems(): List<MediaItem> = lib().values.toList()

    suspend fun allChannelRows(): List<ChannelRow> = db.channels().all()

    suspend fun adCatalog(): List<AdRow> = db.ads().all()

    suspend fun replaceAds(source: String?, ads: List<AdRow>) = mutex.withLock {
        db.withTransaction {
            db.ads().clear()
            db.ads().insertAll(ads)
            if (source == null) {
                db.settings().remove(Settings.AD_LIBRARY)
                db.settings().remove(Settings.AD_LAST_SYNC)
            } else {
                db.settings().put(SettingRow(Settings.AD_LIBRARY, source))
                db.settings().put(SettingRow(Settings.AD_LAST_SYNC, System.currentTimeMillis().toString()))
            }
        }
    }

    fun activeChannels(): Flow<List<ChannelRow>> = db.channels().observeActive()
    fun allChannels(): Flow<List<ChannelRow>> = db.channels().observeAll()
    suspend fun channel(id: String): ChannelRow? = db.channels().byId(id)
    suspend fun active(): List<ChannelRow> = db.channels().active()

    /** Commit library/lineup changes atomically with respect to schedule generation. */
    suspend fun refreshChannels(
        eastWest: Boolean, music: Boolean, items: List<MediaItem>? = null,
        selectedLibraries: Set<String>? = null,
    ) = mutex.withLock {
        db.withTransaction {
            if (selectedLibraries != null) {
                val previous = db.settings().get(Settings.SELECTED_LIBRARIES)
                val encoded = com.rerun.core.codec.SetCodec.encode(selectedLibraries)
                if (previous != encoded) {
                    db.entries().clear()
                    db.states().clear()
                    db.channels().delete(Lineup.RESTART_ID)
                }
                db.settings().put(SettingRow(Settings.SELECTED_LIBRARIES, encoded))
            }
            if (items != null) {
                db.items().replaceAll(items.map(ItemRow::of))
                for (id in db.entries().channelsWithMissingItems()) {
                    db.entries().deleteChannel(id)
                    db.states().delete(id)
                    if (id == Lineup.RESTART_ID) db.channels().delete(id)
                }
            }
            val contents = items ?: db.items().all().map { it.toItem() }
            val existing = db.channels().all().map { StoredChannel(it.def, it.builtIn) }
            val planned = Lineup.plan(contents, existing, eastWest, music)
            db.channels().upsertAll(
                planned.channels.map { ChannelRow.of(it.def, it.builtIn, it.dormant, it.poolMs) },
            )
            for (id in planned.deleteIds) removeChannel(id)
        }
        invalidateLibrary()
        _guideRevision.value++
    }

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
        db.withTransaction {
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
        db.withTransaction {
            db.entries().deleteChannel(row.id)
            if (shifted.isNotEmpty()) db.entries().insertAll(shifted.map(EntryRow::of))
        }
    }

    suspend fun entries(channelId: String, fromMs: Long, toMs: Long): List<ScheduleEntry> =
        db.entries().range(channelId, fromMs, toMs).map { it.toEntry() }

    /** Include the preceding program when the guide window starts during its ad break. */
    suspend fun allEntries(fromMs: Long, toMs: Long): Map<String, List<ScheduleEntry>> = db.withTransaction {
        (db.entries().programsBeforeBreak(fromMs) + db.entries().rangeAll(fromMs, toMs))
            .distinctBy { it.channelId to it.startMs }
            .map { it.toEntry() }.groupBy { it.channelId }
    }

    /**
     * After a channel is edited: keep what's on now, rebuild everything after it.
     * West is derived, so an East edit drops the shifted copy and builds it again.
     */
    suspend fun resetFuture(channelId: String, nowMs: Long) = mutex.withLock {
        db.withTransaction {
            if (!Lineup.isWest(channelId)) {
                val current = db.entries().range(channelId, nowMs, nowMs + 1).firstOrNull()
                db.entries().deleteFrom(channelId, current?.endMs ?: nowMs)
                db.channels().byId(channelId)?.let { topUp(it, nowMs) }
            }
            val west = db.channels().byId(Lineup.westId(channelId)) ?: return@withTransaction
            db.entries().deleteChannel(west.id)
            mirrorWest(west, nowMs)
        }
        _guideRevision.value++
    }

    suspend fun saveChannel(row: ChannelRow) = mutex.withLock {
        db.withTransaction {
            db.channels().upsert(row)
            if (row.hidden && !Lineup.isWest(row.id)) removeChannel(Lineup.westId(row.id))
        }
    }

    suspend fun deleteChannel(id: String) = mutex.withLock {
        db.withTransaction {
            removeChannel(id)
            if (!Lineup.isWest(id)) removeChannel(Lineup.westId(id))
        }
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
