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
import com.rerun.core.schedule.SharedScheduleCode
import com.rerun.core.schedule.SharedPilotScheduler
import com.rerun.core.schedule.AdSlot
import org.json.JSONArray
import java.security.SecureRandom
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps every active channel's locked schedule at least a day ahead. */
class ScheduleRepo internal constructor(
    private val db: RerunDb,
    private val scheduler: Scheduler,
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val fingerprint: (ChannelDef, List<MediaItem>, SharedScheduleCode) -> String,
) {
    constructor(db: RerunDb, scheduler: Scheduler) : this(db, scheduler, fingerprint = SharedPilotScheduler::fingerprint)

    private val mutex = Mutex()
    private data class LibrarySnapshot(val generation: Long, val items: List<MediaItem>, val byKey: Map<String, MediaItem>)
    private val libraryLoad = Mutex()
    private val libraryLock = Any()
    private var library: LibrarySnapshot? = null
    private var libraryGeneration = 0L
    private data class SharedInputs(val generation: Long, val definition: ChannelDef, val code: SharedScheduleCode)
    private val sharedValidated = mutableMapOf<String, SharedInputs>()
    private val sharedFingerprints = java.util.LinkedHashMap<SharedInputs, String>(16, 0.75f, true)
    private val _guideRevision = MutableStateFlow(0L)
    val guideRevision = _guideRevision.asStateFlow()
    private val _sharedRefreshRequests = MutableSharedFlow<Set<String>>(
        replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val sharedRefreshRequests = _sharedRefreshRequests.asSharedFlow()

    /** Signal outside the schedule mutex; the controller owns network work and its lifecycle. */
    private suspend fun requestSharedRefresh(channelIds: Collection<String>, nowMs: Long) {
        if (channelIds.isEmpty() || db.settings().get(Settings.SHARED_CODE) == null) return
        val lastSync = db.settings().get(Settings.LAST_SYNC)?.toLongOrNull() ?: 0L
        if (lastSync > 0 && nowMs - lastSync < SHARED_REFRESH_MS) return
        val requested = db.channels().byIds(channelIds.toList()).filter {
            SharedPilotScheduler.participates(it.def) && !it.hidden && !it.dormant &&
                (db.entries().lastEnd(it.id)?.let { end -> end < nowMs + SHARED_HORIZON_MS } != false)
        }.mapTo(linkedSetOf()) { it.id }
        if (requested.isNotEmpty()) _sharedRefreshRequests.tryEmit(requested)
    }

    suspend fun sharedRefreshDue(nowMs: Long): Boolean {
        if (db.settings().get(Settings.SHARED_CODE) == null) return false
        val lastSync = db.settings().get(Settings.LAST_SYNC)?.toLongOrNull() ?: 0L
        return lastSync == 0L || nowMs - lastSync >= SHARED_REFRESH_MS
    }

    suspend fun sharedPilotStatus(): SharedPilotStatus = mutex.withLock {
        withContext(Dispatchers.Default) {
            val encoded = db.settings().get(Settings.SHARED_CODE)
            val code = encoded?.let(SharedScheduleCode::decode)
            val channels = db.channels().active().filter { SharedPilotScheduler.participates(it.def) }
            val fingerprints = if (code == null) emptyMap() else withCurrentLibrary { snapshot ->
                channels.associate { it.id to cachedSharedFingerprint(it.def, code, snapshot) }
            }
            SharedPilotStatus(
                encoded,
                db.settings().get(Settings.SHARED_EFFECTIVE)?.toLongOrNull() ?: 0L,
                channels.map { row ->
                    val fingerprint = if (code == null) "off" else fingerprints.getValue(row.id)
                    val from = db.settings().get(sharedChannelKey(row.id))?.substringBefore('|')?.toLongOrNull()
                    "${row.number} ${row.name}: $fingerprint" +
                        (if (from != null) {
                            val mode = if (code == null) "Local" else "Shared"
                            val state = if (from > System.currentTimeMillis()) "pending until" else "effective since"
                            " · $mode $state UTC ${java.time.Instant.ofEpochMilli(from)}"
                        } else "")
                },
            )
        }
    }

    /** Validate first; the recipe, cutovers and future queues commit together. */
    suspend fun setSharedPilot(text: String?, nowMs: Long) {
        val currentTime = advancingTime(nowMs)
        withContext(Dispatchers.Default) {
            val recipe = text?.let { validateSharedCode(it, nowMs) }
            mutex.withLock {
                val participating = db.channels().all().filter { SharedPilotScheduler.participates(it.def) }
                if (recipe != null) {
                    withCurrentLibrary { snapshot ->
                        participating.forEach { cachedSharedFingerprint(it.def, recipe, snapshot) }
                    }
                }
                db.withTransaction {
                    val encoded = recipe?.encode()
                    if (encoded == db.settings().get(Settings.SHARED_CODE)) return@withTransaction
                    val appliedNow = currentTime()
                    val boundary = nextSharedBoundary(appliedNow)
                    for (row in participating) {
                        val from = if (db.entries().lastEnd(row.id) == null)
                            Math.floorDiv(appliedNow, SHARED_BLOCK_MS) * SHARED_BLOCK_MS
                        else sharedCutover(row.id, boundary, currentTime)
                        db.settings().put(SettingRow(sharedChannelKey(row.id), "$from|"))
                        db.states().delete(row.id)
                    }
                    if (encoded == null) db.settings().remove(Settings.SHARED_CODE)
                    else db.settings().put(SettingRow(Settings.SHARED_CODE, encoded))
                    db.settings().put(SettingRow(Settings.SHARED_EFFECTIVE, boundary.toString()))
                    pruneSharedBookings(appliedNow)
                    sharedValidated.clear()
                }
                _guideRevision.value++
            }
        }
    }

    private fun advancingTime(nowMs: Long): () -> Long {
        // Keep caller/test wall time while accounting for dispatcher, lock and calculation delays.
        val started = monotonicMs()
        return { nowMs + (monotonicMs() - started).coerceAtLeast(0L) }
    }

    private fun sharedChannelKey(id: String) = "${Settings.SHARED_PREFIX}channel:$id"
    private fun sharedBreakKey(entry: ScheduleEntry) =
        "${Settings.SHARED_PREFIX}break:${entry.channelId}:${entry.startMs}:${entry.endMs}"

    private suspend fun pruneSharedBookings(nowMs: Long) {
        for (booking in db.settings().withPrefix("${Settings.SHARED_PREFIX}break:")) {
            if ((booking.key.substringAfterLast(':').toLongOrNull() ?: Long.MAX_VALUE) < nowMs - KEEP_PAST_MS) {
                db.settings().remove(booking.key)
            }
        }
    }

    /** Retain the straddling program and its following break, then wait for a common UTC block. */
    private suspend fun sharedCutover(id: String, boundary: Long, currentTime: () -> Long): Long {
        val entries = db.entries().range(id, currentTime(), Long.MAX_VALUE)
        val nowMs = currentTime()
        val safeBoundary = maxOf(boundary, nextSharedBoundary(nowMs))
        val crossing = entries.firstOrNull { it.startMs < safeBoundary && it.endMs > safeBoundary }
        var end = crossing?.endMs ?: safeBoundary
        if (crossing?.kind == EntryKind.PROGRAM.name) {
            entries.firstOrNull { it.startMs == end && it.kind == EntryKind.FILLER.name }?.let { end = it.endMs }
        }
        val from = if (end % SHARED_BLOCK_MS == 0L) end else nextSharedBoundary(end)
        db.entries().deleteFrom(id, end)
        val last = db.entries().lastEnd(id)
        if (last != null && last < from) {
            val bridge = ScheduleEntry(id, maxOf(last, nowMs), from, EntryKind.FILLER, null)
            db.entries().insertAll(listOf(EntryRow.of(bridge)))
            db.settings().put(SettingRow(sharedBreakKey(bridge), ""))
        }
        return from
    }

    /** Local bookings freeze clip identities and bounds, not just a seed that catalog edits could redraw. */
    suspend fun isSharedFiller(entry: ScheduleEntry): Boolean = db.settings().get(sharedBreakKey(entry)) != null

    suspend fun sharedTiming(channelId: String, nowMs: Long): Boolean = mutex.withLock {
        val transition = db.settings().get(sharedChannelKey(channelId)) ?: return@withLock false
        val def = db.channels().byId(channelId)?.def ?: return@withLock false
        SharedPilotScheduler.participates(def) && (db.settings().get(Settings.SHARED_CODE) != null ||
            nowMs < (transition.substringBefore('|').toLongOrNull() ?: 0L))
    }

    suspend fun sharedAdNextChange(entry: ScheduleEntry, nowMs: Long): Long {
        val plan = db.settings().get(sharedBreakKey(entry))?.takeIf { it.isNotEmpty() }?.let(::JSONArray)
            ?: return entry.endMs
        for (index in 0 until plan.length()) {
            val slot = plan.getJSONArray(index)
            if (nowMs < slot.getLong(2)) return slot.getLong(2)
        }
        return entry.endMs
    }

    suspend fun sharedAd(entry: ScheduleEntry, nowMs: Long): AdSlot? = mutex.withLock {
        val key = sharedBreakKey(entry)
        val saved = db.settings().get(key)
        val plan = if (!saved.isNullOrEmpty()) JSONArray(saved) else withContext(Dispatchers.Default) {
            val catalog = db.ads().all().map { it.toItem() }
            val slots = JSONArray()
            for (slot in bookSharedAds(entry, catalog, Random(SecureRandom().nextLong()))) {
                slots.put(JSONArray().put(slot.item.key).put(slot.startMs).put(slot.endMs))
            }
            db.settings().put(SettingRow(key, slots.toString()))
            slots
        }
        for (index in 0 until plan.length()) {
            val slot = plan.getJSONArray(index)
            if (nowMs >= slot.getLong(1) && nowMs < slot.getLong(2)) {
                return@withLock db.ads().byKey(slot.getString(0))?.toItem()?.let {
                    AdSlot(it, slot.getLong(1), slot.getLong(2))
                }
            }
        }
        null
    }

    fun invalidateLibrary() = synchronized(libraryLock) {
        libraryGeneration++
        library = null
        sharedFingerprints.clear()
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

    private suspend fun <T> withCurrentLibrary(block: (LibrarySnapshot) -> T): T {
        while (true) {
            val snapshot = lib()
            val result = block(snapshot)
            synchronized(libraryLock) {
                if (snapshot.generation == libraryGeneration) return result
            }
        }
    }

    private fun cachedSharedFingerprint(
        def: ChannelDef, code: SharedScheduleCode, snapshot: LibrarySnapshot,
        pool: List<MediaItem>? = null,
    ): String {
        val key = SharedInputs(snapshot.generation, def, code)
        synchronized(libraryLock) { sharedFingerprints[key] }?.let { return it }
        val value = fingerprint(def, pool ?: Resolver.pool(def, snapshot.items), code)
        synchronized(libraryLock) {
            if (snapshot.generation == libraryGeneration) {
                sharedFingerprints[key] = value
                if (sharedFingerprints.size > SHARED_FINGERPRINT_CACHE_SIZE) {
                    sharedFingerprints.remove(sharedFingerprints.keys.first())
                }
            }
        }
        return value
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
                    if (db.settings().get(Settings.SHARED_CODE) == null) db.entries().clear()
                    else for (row in db.channels().all()) {
                        if (!SharedPilotScheduler.participates(row.def)) db.entries().deleteChannel(row.id)
                    }
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
                    if (db.settings().get(Settings.SHARED_CODE) != null &&
                        db.channels().byId(id)?.def?.let { SharedPilotScheduler.participates(it) } == true) continue
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
        val shared = db.settings().get(Settings.SHARED_CODE) != null
        for (row in db.channels().active()) {
            if (!shared || !SharedPilotScheduler.participates(row.def)) topUp(row, nowMs)
        }
    }

    /** Tops up only the given channels, so the guide does not resolve pools for rows nobody is looking at. */
    suspend fun ensure(channelIds: Collection<String>, nowMs: Long) {
        val currentTime = advancingTime(nowMs)
        requestSharedRefresh(channelIds, nowMs)
        mutex.withLock {
            db.entries().pruneBefore(nowMs - KEEP_PAST_MS)
            val ids = channelIds.toList()
            if (ids.isNotEmpty()) for (row in db.channels().byIds(ids)) topUp(row, nowMs, currentTime)
        }
    }

    /** Tops up a single channel (used right before tuning to it). */
    suspend fun ensure(channelId: String, nowMs: Long) {
        val currentTime = advancingTime(nowMs)
        requestSharedRefresh(listOf(channelId), nowMs)
        mutex.withLock {
            db.channels().byId(channelId)?.let { topUp(it, nowMs, currentTime) }
        }
    }

    /** Plants one episode on the hidden "From the Top" channel, starting now at 0:00. */
    suspend fun airFromStart(item: MediaItem, nowMs: Long) = mutex.withLock {
        db.withTransaction {
            val def = ChannelDef(
                id = Lineup.RESTART_ID,
                number = Lineup.RESTART_NUMBER,
                name = "From the Top",
                kind = ChannelKind.CUSTOM,
                rules = Rules(kinds = setOf(item.kind)),
                hidden = true,
            )
            db.channels().upsert(ChannelRow.of(def, builtIn = true, dormant = false, poolMs = item.durationMs))
            db.entries().deleteChannel(Lineup.RESTART_ID)
            db.states().delete(Lineup.RESTART_ID)
            val end = nowMs + item.durationMs.coerceAtLeast(1L)
            db.entries().insertAll(listOf(EntryRow.of(ScheduleEntry(Lineup.RESTART_ID, nowMs, end, EntryKind.PROGRAM, item.key))))
        }
    }

    private suspend fun topUp(row: ChannelRow, nowMs: Long, currentTime: () -> Long = { nowMs }) {
        if (row.dormant || row.hidden) return
        if (row.id == Lineup.RESTART_ID) return
        if (SharedPilotScheduler.participates(row.def)) {
            val code = db.settings().get(Settings.SHARED_CODE)?.let { SharedScheduleCode.decode(it) }
            if (code != null) {
                topUpShared(row, code, currentTime)
                return
            }
        }
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

    private suspend fun topUpShared(row: ChannelRow, code: SharedScheduleCode, currentTime: () -> Long) =
        withContext(Dispatchers.Default) {
            val nowMs = currentTime()
            val storedEnd = db.entries().lastEnd(row.id)
            val expected = SharedInputs(synchronized(libraryLock) { libraryGeneration }, row.def, code)
            if (sharedValidated[row.id] == expected && !needsTopUp(storedEnd, nowMs)) {
                return@withContext
            }
            val (identity, pool, fingerprint) = withCurrentLibrary { snapshot ->
                val pool = Resolver.pool(row.def, snapshot.items)
                Triple(SharedInputs(snapshot.generation, row.def, code), pool,
                    cachedSharedFingerprint(row.def, code, snapshot, pool))
            }
            db.withTransaction {
                val appliedNow = currentTime()
                val key = sharedChannelKey(row.id)
                val previous = db.settings().get(key)?.split('|', limit = 2)
                val oldLast = db.entries().lastEnd(row.id)
                var from = previous?.firstOrNull()?.toLongOrNull() ?: if (oldLast == null)
                    Math.floorDiv(appliedNow, SHARED_BLOCK_MS) * SHARED_BLOCK_MS
                else sharedCutover(row.id, nextSharedBoundary(appliedNow), currentTime)
                if (previous != null && previous.getOrNull(1).orEmpty().isNotEmpty() &&
                    previous[1] != fingerprint) {
                    from = sharedCutover(row.id, nextSharedBoundary(appliedNow), currentTime)
                }
                val last = db.entries().lastEnd(row.id)
                val start = maxOf(from, last ?: from, Math.floorDiv(nowMs, SHARED_BLOCK_MS) * SHARED_BLOCK_MS)
                if (start < nowMs + SHARED_HORIZON_MS && (last == null || last < nowMs + SHARED_HORIZON_MS)) {
                    val generated = SharedPilotScheduler.generate(row.def, pool, code, start, nowMs + SHARED_HORIZON_MS)
                        .filter { it.startMs >= start }
                    db.entries().insertAll(generated.map(EntryRow::of))
                    for (entry in generated.filter { it.kind == EntryKind.FILLER }) {
                        val breakKey = sharedBreakKey(entry)
                        if (db.settings().get(breakKey) == null) db.settings().put(SettingRow(breakKey, ""))
                    }
                }
                db.settings().put(SettingRow(key, "$from|$fingerprint"))
                pruneSharedBookings(nowMs)
            }
            sharedValidated[row.id] = identity
        }

    suspend fun entries(channelId: String, fromMs: Long, toMs: Long): List<ScheduleEntry> =
        db.entries().range(channelId, fromMs, toMs).map { it.toEntry() }

    /** Include the preceding program when the guide window starts during its ad break. */
    suspend fun entriesFor(channelIds: Collection<String>, fromMs: Long, toMs: Long): Map<String, List<ScheduleEntry>> = db.withTransaction {
        val ids = channelIds.toList()
        if (ids.isEmpty()) return@withTransaction emptyMap()
        (db.entries().programsBeforeBreakFor(ids, fromMs) + db.entries().rangeFor(ids, fromMs, toMs))
            .distinctBy { it.channelId to it.startMs }
            .map { it.toEntry() }.groupBy { it.channelId }
    }

    /** After a channel is edited: keep what's on now, rebuild everything after it. */
    suspend fun resetFuture(channelId: String, nowMs: Long) {
        val currentTime = advancingTime(nowMs)
        mutex.withLock {
            db.withTransaction {
                val row = db.channels().byId(channelId)
                if (row != null && SharedPilotScheduler.participates(row.def) &&
                    db.settings().get(Settings.SHARED_CODE) != null) {
                    val from = sharedCutover(channelId, nextSharedBoundary(currentTime()), currentTime)
                    db.settings().put(SettingRow(sharedChannelKey(channelId), "$from|"))
                } else {
                    val current = db.entries().range(channelId, nowMs, nowMs + 1).firstOrNull()
                    db.entries().deleteFrom(channelId, current?.endMs ?: nowMs)
                }
                row?.let { topUp(it, nowMs, currentTime) }
            }
            _guideRevision.value++
        }
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
        sharedValidated.remove(id)
        synchronized(libraryLock) { sharedFingerprints.keys.removeAll { it.definition.id == id } }
        db.channels().delete(id)
        db.entries().deleteChannel(id)
        db.states().delete(id)
    }

    private companion object {
        const val SHARED_FINGERPRINT_CACHE_SIZE = 128
        const val KEEP_PAST_MS = 6 * 3_600_000L
        const val SHARED_REFRESH_MS = 24 * 3_600_000L
    }
}
