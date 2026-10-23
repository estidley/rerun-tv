package com.rerun.tv.data

import com.rerun.core.catalog.Catalog
import com.rerun.core.model.MediaItem
import com.rerun.core.plex.PlexParse
import com.rerun.core.resolve.Resolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class SyncProgress(val stage: String, val done: Int, val total: Int, val finished: Boolean = false)

/** Pulls movies and episodes from the chosen Plex server into Room, then recomputes dormancy. */
class LibrarySync(
    private val api: PlexApi,
    private val db: RerunDb,
    private val settings: Settings,
    private val onLibraryChanged: () -> Unit,
) {
    fun sync(): Flow<SyncProgress> = flow {
        val base = requireNotNull(settings.serverBase()) { "No Plex server chosen" }
        val token = requireNotNull(settings.serverToken ?: settings.accountToken) { "Not signed in" }

        emit(SyncProgress("Reading libraries", 0, 0))
        val sections = PlexParse.sections(api.get(base, "/library/sections", token))
        val items = ArrayList<MediaItem>()

        for (s in sections.filter { it.type == "movie" }) {
            pages(base, "/library/sections/${s.key}/all?type=1&includeGuids=1", token) { json, done, total ->
                items += PlexParse.movies(json)
                emit(SyncProgress("Movies: ${s.title}", done, total))
            }
        }
        for (s in sections.filter { it.type == "show" }) {
            val shows = HashMap<String, com.rerun.core.model.ShowMeta>()
            pages(base, "/library/sections/${s.key}/all?type=2&includeGuids=1", token) { json, done, total ->
                PlexParse.shows(json).forEach { shows[it.key] = it }
                emit(SyncProgress("Shows: ${s.title}", done, total))
            }
            pages(base, "/library/sections/${s.key}/all?type=4", token) { json, done, total ->
                items += PlexParse.episodes(json, shows)
                emit(SyncProgress("Episodes: ${s.title}", done, total))
            }
        }

        emit(SyncProgress("Saving", items.size, items.size))
        db.items().replaceAll(items.map(ItemRow::of))
        refreshChannels(items)
        settings.setLastSyncMs(System.currentTimeMillis())
        onLibraryChanged()
        emit(SyncProgress("Done", items.size, items.size, finished = true))
    }

    private suspend fun pages(
        base: String, path: String, token: String,
        onPage: suspend (json: String, done: Int, total: Int) -> Unit,
    ) {
        var start = 0
        while (true) {
            val json = api.get(base, path, token, start, PAGE)
            val total = PlexParse.totalSize(json)
            start += PAGE
            onPage(json, minOf(start, total), total)
            if (start >= total) break
        }
    }

    /** Seeds built-in channels on first run and recomputes which channels are dormant. */
    suspend fun refreshChannels(items: List<MediaItem>? = null) {
        val library = items ?: db.items().all().map { it.toItem() }
        val existing = db.channels().all().associateBy { it.id }
        val rows = ArrayList<ChannelRow>()
        for (def in Catalog.builtInChannels()) {
            val row = existing[def.id]
            val current = row?.def ?: def
            val pool = Resolver.pool(current, library)
            rows += ChannelRow.of(current, builtIn = true, dormant = Resolver.isDormant(pool), poolMs = pool.sumOf { it.durationMs })
        }
        for (row in existing.values.filter { !it.builtIn }) {
            val pool = Resolver.pool(row.def, library)
            rows += row.copy(dormant = Resolver.isDormant(pool), poolMs = pool.sumOf { it.durationMs })
        }
        db.channels().upsertAll(rows)
    }

    private companion object {
        const val PAGE = 200
    }
}
