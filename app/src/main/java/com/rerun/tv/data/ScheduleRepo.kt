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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps every active channel's locked schedule at least a day ahead. */
class ScheduleRepo(private val db: RerunDb, private val scheduler: Scheduler) {
    private val mutex = Mutex()
    private data class LibrarySnapshot(val items: List<MediaItem>, val byKey: Map<String, MediaItem>)
    private val libraryLoad = Mutex()
    private val libraryLock = Any()
    private var library: LibrarySnapshot? = null
    private var libraryGeneration = 0L
    private val _guideRevision = MutableStateFlow(0L)
    val guideRevision = _guideRevision.asStateFlow()

    fun invalidateLibrary() = synchronized(libraryLock) {
        libraryGeneration++
        library = null
    }

    suspend fun signOut(settings: Settings) = mutex.withLock {
        settings.signOut()
        invalidateLibrary()
        _guideRevision.value++
    }

    private suspend fun lib(): LibrarySnapshot {
        synchronized(libraryLock) { library }?.let { return it }
        return libraryLoad.withLock {
            var loaded: LibrarySnapshot?
            do {
                val generation = synchronized(libraryLock) {
                    library?.let { return@withLock it }
                    libraryGeneration
                }
                val snapshot = withContext(Dispatchers.IO) {
                    val byKey = db.items().all().associate { row -> row.key to row.toItem() }
                    LibrarySnapshot(byKey.values.toList(), byKey)
                }
                loaded = synchronized(libraryLock) {
                    if (generation == libraryGeneration) {
                        library = snapshot
                        snapshot
                    } else null
                }
            } while (loaded == null)
            loaded
        }
    }

    suspend fun item(key: String?): MediaItem? = key?.let { lib().byKey[it] }

    suspend fun libraryItems(): List<MediaItem> = lib().items

    suspend fun allChannelRows(): List<ChannelRow> = db.channels().all()

    suspend fun adCatalog(): List<AdRow> = db.ads().all()

    suspend fun adItem(key: String?): MediaItem? = key?.let { db.ads().byKey(it)?.toItem() }

    suspend fun replaceAds(source: String?, ads: List<AdRow>) = mutex.withLock {
        db.withTransaction {
            val changed = db.ads().all() != ads.sortedBy { it.key }
            db.ads().clear()
            db.ads().insertAll(ads)
            if (source == null) {
                db.settings().remove(Settings.AD_LIBRARY)
                db.settings().remove(Settings.AD_LAST_SYNC)
            } else {
                db.settings().put(SettingRow(Settings.AD_LIBRARY, source))
                db.settings().put(SettingRow(Settings.AD_LAST_SYNC, System.currentTimeMillis().toString()))
            }
            if (source == null || ads.isEmpty()) removeChannel(Lineup.ADS_ID)
            else {
                val previous = db.channels().byId(Lineup.ADS_ID)
                val used = db.channels().all().filterNot { it.id == Lineup.ADS_ID }.map { it.number }.toSet() + Lineup.RESTART_NUMBER
                var number = previous?.number ?: Lineup.adsChannel.number
                while (number in used) number++
                db.channels().upsert(ChannelRow.of(
                    Lineup.adsChannel.copy(number = number), true, false, ads.sumOf { it.durationMs },
                ))
                if (changed) {
                    db.entries().deleteChannel(Lineup.ADS_ID)
                    db.states().delete(Lineup.ADS_ID)
                }
            }
        }
        _guideRevision.value++
    }

    fun activeChannels(): Flow<List<ChannelRow>> = db.channels().observeActive()
    fun allChannels(): Flow<List<ChannelRow>> = db.channels().observeAll()
    suspend fun channel(id: String): ChannelRow? = db.channels().byId(id)
    suspend fun active(): List<ChannelRow> = db.channels().active()

    /** Commit library/lineup changes atomically with respect to schedule generation. */
    suspend fun refreshChannels(
        music: Boolean, items: List<MediaItem>? = null,
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
                val resyncNow = System.currentTimeMillis()
                for (id in db.entries().channelsWithMissingItems()) {
                    if (id == Lineup.ADS_ID) continue
                    if (id == Lineup.RESTART_ID) {
                        db.entries().deleteChannel(id)
                        db.states().delete(id)
                        db.channels().delete(id)
                        continue
                    }
                    // Keep what is airing and later entries that are still playable; a removed item cannot air.
                    val current = db.entries().range(id, resyncNow, resyncNow + 1).firstOrNull()
                    val from = db.entries().firstMissingStart(id, current?.startMs ?: resyncNow)
                    if (from != null) db.entries().deleteFrom(id, from)
                }
            }
            val contents = items ?: db.items().all().map { it.toItem() }
            val existing = db.channels().all().map { StoredChannel(it.def, it.builtIn) }
            val adsMs = if (db.settings().get(Settings.AD_LIBRARY) != null) db.ads().all().sumOf { it.durationMs } else 0L
            val planned = Lineup.plan(contents, existing, music, adsPoolMs = adsMs)
            val previousById = existing.associateBy { it.def.id }
            val nowMs = System.currentTimeMillis()
            for (row in planned.channels) {
                val previous = previousById[row.def.id]?.def ?: continue
                val rulesChanged = previous.rules != row.def.rules || previous.blocks != row.def.blocks
                // Also repair queues left behind by the initial Anime-rule upgrade.
                val staleAnimeQueue = if (row.def.id == "toonami" && row.def.rules == Rules(genres = setOf("Anime"))) {
                    val allowed = Resolver.pool(row.def, contents).mapTo(HashSet()) { it.key }
                    db.entries().range(row.def.id, nowMs, Long.MAX_VALUE).any {
                        it.startMs > nowMs && it.kind == EntryKind.PROGRAM.name && it.itemKey !in allowed
                    }
                } else false
                if (rulesChanged || staleAnimeQueue) {
                    val current = db.entries().range(row.def.id, nowMs, nowMs + 1).firstOrNull()
                    db.entries().deleteFrom(row.def.id, current?.endMs ?: nowMs)
                }
            }
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
        if (row.dormant || row.hidden) return
        if (row.id == Lineup.RESTART_ID) return
        val last = db.entries().lastEnd(row.id)
        if (!needsTopUp(last, nowMs)) return
        val def = row.def
        val pool = if (row.id == Lineup.ADS_ID) db.ads().all().map { it.toItem() }
            else Resolver.pool(def, lib().items)
        val state = db.states().get(row.id)?.let { StateCodec.decode(it.json) } ?: ChannelState(seed = kotlin.random.Random.nextLong())
        val ext = scheduler.extend(def, pool, state, last, nowMs,
            backToBack = row.id == Lineup.ADS_ID || def.kind == ChannelKind.MUSIC)
        if (ext.entries.isNotEmpty()) db.entries().insertAll(ext.entries.map(EntryRow::of))
        db.states().put(StateRow(row.id, StateCodec.encode(ext.state)))
    }

    suspend fun entries(channelId: String, fromMs: Long, toMs: Long): List<ScheduleEntry> =
        db.entries().range(channelId, fromMs, toMs).map { it.toEntry() }

    /** Include the preceding program when the guide window starts during its ad break. */
    suspend fun allEntries(fromMs: Long, toMs: Long): Map<String, List<ScheduleEntry>> = db.withTransaction {
        (db.entries().programsBeforeBreak(fromMs) + db.entries().rangeAll(fromMs, toMs))
            .distinctBy { it.channelId to it.startMs }
            .map { it.toEntry() }.groupBy { it.channelId }
    }

    /** After a channel is edited: keep what's on now, rebuild everything after it. */
    suspend fun resetFuture(channelId: String, nowMs: Long) = mutex.withLock {
        db.withTransaction {
            val current = db.entries().range(channelId, nowMs, nowMs + 1).firstOrNull()
            db.entries().deleteFrom(channelId, current?.endMs ?: nowMs)
            db.channels().byId(channelId)?.let { topUp(it, nowMs) }
        }
        _guideRevision.value++
    }

    suspend fun saveChannel(row: ChannelRow) = mutex.withLock {
        val def = row.def
        val poolMs = withContext(Dispatchers.Default) { Resolver.pool(def, lib().items).sumOf { it.durationMs } }
        val music = db.settings().get(Settings.MUSIC)?.toBooleanStrictOrNull() ?: false
        val checked = ChannelRow.of(def, row.builtIn, Lineup.isDormant(def, poolMs, music), poolMs)
        db.channels().upsert(checked)
    }

    suspend fun deleteChannel(id: String) = mutex.withLock {
        db.withTransaction {
            removeChannel(id)
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
