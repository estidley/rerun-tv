package com.rerun.tv.data

import android.os.SystemClock
import android.util.Log
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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps every active channel's locked schedule at least a day ahead. */
class ScheduleRepo internal constructor(
    private val db: RerunDb,
    private val scheduler: Scheduler,
) {
    private val mutex = Mutex()
    private data class LibrarySnapshot(val generation: Long, val items: List<MediaItem>, val byKey: Map<String, MediaItem>)
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
                    LibrarySnapshot(generation, byKey.values.toList(), byKey)
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
        items: List<MediaItem>? = null,
        selectedLibraries: Set<String>? = null,
    ) = mutex.withLock {
        val planStartedAt = SystemClock.elapsedRealtime()
        val contents = items ?: withContext(Dispatchers.Default) { db.items().all().map { it.toItem() } }
        val restart = restartSession()
        val available = contents.mapTo(HashSet()) { it.key }
        val existing = db.channels().all().map { StoredChannel(it.def, it.builtIn) }
        val adsMs = if (db.settings().get(Settings.AD_LIBRARY) != null) db.ads().all().sumOf { it.durationMs } else 0L
        val minimum = com.rerun.core.catalog.MinimumContentLevel.from(db.settings().get(Settings.MINIMUM_CONTENT))
        val music = contents.any { it.kind == MediaKind.TRACK }
        val planned = withContext(Dispatchers.Default) {
            Lineup.plan(contents, existing, music, adsPoolMs = adsMs, minimum = minimum)
        }
        val previousById = existing.associateBy { it.def.id }
        Log.d("RerunTiming", "lineup_plan channels=${planned.channels.size} elapsedMs=${SystemClock.elapsedRealtime() - planStartedAt}")

        val commitStartedAt = SystemClock.elapsedRealtime()
        db.withTransaction {
            if (selectedLibraries != null) {
                val previous = db.settings().get(Settings.SELECTED_LIBRARIES)
                val encoded = com.rerun.core.codec.SetCodec.encode(selectedLibraries)
                if (previous != encoded) {
                    db.entries().clear()
                    db.states().clear()
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
            if (restart != null && restart.keys.drop(restart.index).any { it !in available }) {
                removeRestart()
            }
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
                val staleCatchallQueue = if (row.def.id == Lineup.CATCHALL_ID && !rulesChanged) {
                    val futurePrograms = db.entries().range(row.def.id, nowMs, Long.MAX_VALUE).filter {
                        it.startMs > nowMs && it.kind == EntryKind.PROGRAM.name
                    }
                    if (futurePrograms.isEmpty()) false else {
                        val allowed = Resolver.pool(row.def, contents, minimum).mapTo(HashSet()) { it.key }
                        futurePrograms.any { it.itemKey !in allowed }
                    }
                } else false
                if (rulesChanged || staleAnimeQueue || staleCatchallQueue) {
                    val current = db.entries().range(row.def.id, nowMs, nowMs + 1).firstOrNull()
                    db.entries().deleteFrom(row.def.id, current?.endMs ?: nowMs)
                }
            }
            db.channels().upsertAll(
                planned.channels.map { ChannelRow.of(it.def, it.builtIn, it.dormant, it.poolMs) },
            )
            for (id in planned.deleteIds) removeChannel(id)
        }
        Log.d("RerunTiming", "lineup_commit elapsedMs=${SystemClock.elapsedRealtime() - commitStartedAt}")
        invalidateLibrary()
        _guideRevision.value++
    }

    /** Tops up any active channel with less than a day of schedule left. */
    suspend fun ensure(nowMs: Long) = mutex.withLock {
        val startedAt = SystemClock.elapsedRealtime()
        db.entries().pruneBefore(nowMs - KEEP_PAST_MS)
        val rows = db.channels().active()
        for (row in rows) topUp(row, nowMs)
        Log.d("RerunTiming", "schedule_all channels=${rows.size} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    /** Tops up only the given channels, so the guide does not resolve pools for rows nobody is looking at. */
    suspend fun ensure(channelIds: Collection<String>, nowMs: Long) {
        mutex.withLock {
            db.entries().pruneBefore(nowMs - KEEP_PAST_MS)
            val ids = channelIds.toList()
            if (ids.isNotEmpty()) for (row in db.channels().byIds(ids)) topUp(row, nowMs)
        }
    }

    /** Tops up a single channel (used right before tuning to it). */
    suspend fun ensure(channelId: String, nowMs: Long) {
        mutex.withLock {
            db.channels().byId(channelId)?.let { topUp(it, nowMs) }
        }
    }

    suspend fun restartSession(): RestartSession? =
        db.settings().get(RESTART_SESSION_KEY)?.let(RestartSession::decode)

    /** A device-local queue; ordinary channels keep their locked wall-clock schedules. */
    suspend fun airFromStart(item: MediaItem, returnChannel: String, queueEpisodes: Boolean) = mutex.withLock {
        db.withTransaction {
            val library = lib().items
            require(library.any { it.key == item.key }) { "This program is no longer in the library." }
            val season = item.season
            val episode = item.episode
            val following = if (queueEpisodes && item.kind == MediaKind.EPISODE && item.showKey != null &&
                season != null && episode != null) {
                library.filter {
                    val nextSeason = it.season
                    val nextEpisode = it.episode
                    it.kind == MediaKind.EPISODE && it.showKey == item.showKey && nextSeason != null && nextEpisode != null &&
                        (nextSeason > season || (nextSeason == season && nextEpisode > episode))
                }.sortedWith(compareBy<MediaItem> { it.season }.thenBy { it.episode }.thenBy { it.key })
                    .distinctBy { it.season to it.episode }
            } else emptyList()
            val session = RestartSession(UUID.randomUUID().toString(), listOf(item.key) + following.map { it.key },
                returnChannel = returnChannel)
            db.settings().put(SettingRow(RESTART_SESSION_KEY, session.encode()))
            updateRestartChannel(session, item)
            db.entries().deleteChannel(Lineup.RESTART_ID)
            db.states().delete(Lineup.RESTART_ID)
        }
        _guideRevision.value++
    }

    private suspend fun updateRestartChannel(session: RestartSession, item: MediaItem) {
        val used = db.channels().all().filterNot { it.id == Lineup.RESTART_ID }.map { it.number }.toSet()
        var number = db.channels().byId(Lineup.RESTART_ID)?.number ?: Lineup.RESTART_NUMBER
        while (number in used) number++
        val def = ChannelDef(Lineup.RESTART_ID, number,
            "From the Top${if (session.paused) " (Paused)" else ""}", ChannelKind.CUSTOM,
            Rules(kinds = setOf(item.kind)))
        db.channels().upsert(ChannelRow.of(def, true, false, item.durationMs))
    }

    suspend fun saveRestartProgress(id: String, index: Int, positionMs: Long, paused: Boolean) = mutex.withLock {
        require(positionMs >= 0) { "Invalid start-over media position." }
        db.withTransaction {
            val session = restartSession() ?: return@withTransaction
            if (session.id != id || session.index != index) return@withTransaction
            val updated = session.copy(positionMs = positionMs, paused = paused)
            db.settings().put(SettingRow(RESTART_SESSION_KEY, updated.encode()))
            if (session.paused != paused) {
                db.items().byKey(session.itemKey)?.toItem()?.let { updateRestartChannel(updated, it) }
                _guideRevision.value++
            }
        }
    }

    suspend fun advanceRestart(id: String, index: Int): RestartSession? = mutex.withLock {
        db.withTransaction {
            val session = restartSession() ?: return@withTransaction null
            check(session.id == id && session.index == index) { "Start-over queue changed." }
            if (index == session.keys.lastIndex) {
                removeRestart()
                null
            } else {
                session.copy(index = index + 1, positionMs = 0).also {
                    db.settings().put(SettingRow(RESTART_SESSION_KEY, it.encode()))
                }
            }
        }.also { _guideRevision.value++ }
    }

    private suspend fun removeRestart() {
        db.settings().remove(RESTART_SESSION_KEY)
        removeChannel(Lineup.RESTART_ID)
    }

    suspend fun removeRestartStation() = mutex.withLock {
        db.withTransaction { removeRestart() }
        _guideRevision.value++
    }

    private suspend fun topUp(row: ChannelRow, nowMs: Long) {
        if (row.dormant || row.hidden) return
        if (row.id == Lineup.RESTART_ID) return
        val last = db.entries().lastEnd(row.id)
        if (!needsTopUp(last, nowMs)) return
        val def = row.def
        val minimum = com.rerun.core.catalog.MinimumContentLevel.from(db.settings().get(Settings.MINIMUM_CONTENT))
        val pool = if (row.id == Lineup.ADS_ID) db.ads().all().map { it.toItem() }
            else Resolver.pool(def, lib().items, minimum)
        val state = db.states().get(row.id)?.let { StateCodec.decode(it.json) } ?: ChannelState(seed = kotlin.random.Random.nextLong())
        val ext = scheduler.extend(def, pool, state, last, nowMs,
            backToBack = row.id == Lineup.ADS_ID || def.kind == ChannelKind.MUSIC)
        if (ext.entries.isNotEmpty()) db.entries().insertAll(ext.entries.map(EntryRow::of))
        db.states().put(StateRow(row.id, StateCodec.encode(ext.state)))
    }

    suspend fun entries(channelId: String, fromMs: Long, toMs: Long): List<ScheduleEntry> =
        if (channelId == Lineup.RESTART_ID) restartEntries(System.currentTimeMillis())
        else db.entries().range(channelId, fromMs, toMs).map { it.toEntry() }

    suspend fun restartEntries(nowMs: Long): List<ScheduleEntry> {
        val session = restartSession() ?: return emptyList()
        return session.entries(lib().byKey, nowMs)
    }

    /** Include complete boundary programs and their following breaks for guide details. */
    suspend fun entriesFor(channelIds: Collection<String>, fromMs: Long, toMs: Long): Map<String, List<ScheduleEntry>> = db.withTransaction {
        val ids = channelIds.toList()
        if (ids.isEmpty()) return@withTransaction emptyMap()
        val scheduled = (db.entries().entriesBeforeBreakFor(ids, fromMs) + db.entries().rangeFor(ids, fromMs, toMs) +
            db.entries().followingBreaksFor(ids, fromMs, toMs))
            .distinctBy { it.channelId to it.startMs }
            .sortedWith(compareBy<EntryRow> { it.channelId }.thenBy { it.startMs })
            .map { it.toEntry() }.groupBy { it.channelId }
        if (Lineup.RESTART_ID in ids) scheduled + (Lineup.RESTART_ID to
            listOfNotNull(restartSession()?.let {
                ScheduleEntry(Lineup.RESTART_ID, fromMs, toMs, EntryKind.PROGRAM, it.itemKey)
            }))
        else scheduled
    }

    /** After a channel is edited: keep what's on now, rebuild everything after it. */
    suspend fun resetFuture(channelId: String, nowMs: Long) {
        mutex.withLock {
            db.withTransaction {
                val row = db.channels().byId(channelId)
                val current = db.entries().range(channelId, nowMs, nowMs + 1).firstOrNull()
                db.entries().deleteFrom(channelId, current?.endMs ?: nowMs)
                row?.let { topUp(it, nowMs) }
            }
            _guideRevision.value++
        }
    }

    suspend fun saveChannel(row: ChannelRow) = mutex.withLock {
        val def = row.def
        val contents = lib().items
        val minimum = com.rerun.core.catalog.MinimumContentLevel.from(db.settings().get(Settings.MINIMUM_CONTENT))
        val poolMs = withContext(Dispatchers.Default) { Resolver.pool(def, contents, minimum).sumOf { it.durationMs } }
        val music = contents.any { it.kind == MediaKind.TRACK }
        val checked = ChannelRow.of(def, row.builtIn, Lineup.isDormant(def, poolMs, music, minimum), poolMs)
        db.channels().upsert(checked)
        _guideRevision.value++
    }

    suspend fun deleteChannel(id: String) = mutex.withLock {
        if (id == Lineup.RESTART_ID) db.settings().remove(RESTART_SESSION_KEY)
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
