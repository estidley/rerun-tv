package com.rerun.tv.data

import com.rerun.core.catalog.Lineup
import com.rerun.core.catalog.StoredChannel
import com.rerun.core.model.MediaItem
import com.rerun.core.plex.PlexParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

data class SyncProgress(val stage: String, val done: Int, val total: Int, val finished: Boolean = false)

/** Pulls movies, episodes, and (when music channels are on) tracks into Room, then rebuilds the dial. */
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
        if (settings.isOn(Settings.MUSIC)) {
            for (s in sections.filter { it.type == "artist" }) {
                pages(base, "/library/sections/${s.key}/all?type=10", token) { json, done, total ->
                    items += PlexParse.tracks(json)
                    emit(SyncProgress("Music: ${s.title}", done, total))
                }
            }
        }

        emit(SyncProgress("Saving", items.size, items.size))
        db.items().replaceAll(items.map(ItemRow::of))
        refreshChannels(items)
        settings.setLastSyncMs(System.currentTimeMillis())
        onLibraryChanged()
        emit(SyncProgress("Done", items.size, items.size, finished = true))
    }.flowOn(Dispatchers.IO)

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

    /** Rebuilds the dial from the library and the lineup switches. */
    suspend fun refreshChannels(items: List<MediaItem>? = null) {
        withContext(Dispatchers.IO) {
            val library = items ?: db.items().all().map { it.toItem() }
            val existing = db.channels().all().map { StoredChannel(it.def, it.builtIn) }
            val planned = Lineup.plan(
                library,
                existing,
                eastWest = settings.isOn(Settings.EAST_WEST),
                music = settings.isOn(Settings.MUSIC),
            )
            db.channels().upsertAll(
                planned.channels.map { ChannelRow.of(it.def, builtIn = it.builtIn, dormant = it.dormant, poolMs = it.poolMs) },
            )
            for (id in planned.deleteIds) {
                db.channels().delete(id)
                db.entries().deleteChannel(id)
                db.states().delete(id)
            }
        }
    }

    private companion object {
        const val PAGE = 200
    }
}
