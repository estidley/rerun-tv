package com.rerun.tv.data

import com.rerun.core.model.MediaItem
import com.rerun.core.plex.PlexParse
import com.rerun.core.plex.Section
import com.rerun.core.plex.ServerUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncProgress(val stage: String, val done: Int, val total: Int, val finished: Boolean = false)

/** Pulls movies, episodes, and (when music channels are on) tracks into Room, then rebuilds the dial. */
class LibrarySync(
    private val api: PlexApi,
    private val settings: Settings,
    private val schedule: ScheduleRepo,
) {
    private val mutex = Mutex()

    suspend fun signOut() = mutex.withLock {
        schedule.signOut(settings)
    }

    suspend fun libraries(): List<Section> {
        val adLibrary = settings.adLibrary()
        return serverLibraries().filter { it.type in setOf("movie", "show", "artist") && it.key != adLibrary }
    }

    suspend fun adLibraries(): List<Section> = serverLibraries().filter { it.type == "movie" }

    suspend fun adCount(): Int = withContext(Dispatchers.IO) { schedule.adCatalog().size }

    fun syncAds(source: String?): Flow<SyncProgress> = flow {
        mutex.withLock {
            if (source == null) {
                schedule.replaceAds(null, emptyList())
                emit(SyncProgress("Ads disabled", 0, 0, finished = true))
            } else {
                val base = requireNotNull(settings.serverBase()) { "No Plex server chosen" }
                val token = requireNotNull(settings.serverToken ?: settings.accountToken) { "Not signed in" }
                require(adLibraries().any { it.key == source }) { "Ad library is unavailable. Choose another library or Off." }
                val clips = ArrayList<AdRow>()
                pages(base, "/library/sections/$source/all?type=1&includeGuids=1", token) { json, done, total ->
                    clips += PlexParse.movies(json)
                        .filter { it.key.isNotBlank() && it.durationMs > 0 && ServerUri.isLibraryPath(it.partKey) }
                        .map(AdRow::of)
                    emit(SyncProgress("Reading ads", done, total))
                }
                val catalog = clips.distinctBy { it.key }
                schedule.replaceAds(source, catalog)
                emit(SyncProgress("Ads synced", catalog.size, catalog.size, finished = true))
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun serverLibraries(): List<Section> {
        val base = requireNotNull(settings.serverBase()) { "No Plex server chosen" }
        val token = requireNotNull(settings.serverToken ?: settings.accountToken) { "Not signed in" }
        return PlexParse.sections(api.get(base, "/library/sections", token))
    }

    fun sync(selectedLibraries: Set<String>? = null): Flow<SyncProgress> = flow {
        mutex.withLock {
            require(selectedLibraries == null || selectedLibraries.isNotEmpty()) { "Choose at least one library" }
            val base = requireNotNull(settings.serverBase()) { "No Plex server chosen" }
            val token = requireNotNull(settings.serverToken ?: settings.accountToken) { "Not signed in" }

            emit(SyncProgress("Reading libraries", 0, 0))
            val selected = selectedLibraries ?: settings.selectedLibraries()
            val sections = libraries().filter { selected == null || it.key in selected }
            require(sections.isNotEmpty()) { "None of your selected libraries are available. Choose libraries in Settings." }
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
                    val artists = HashMap<String, com.rerun.core.plex.MusicMetadata>()
                    val albums = HashMap<String, com.rerun.core.plex.MusicMetadata>()
                    pages(base, "/library/sections/${s.key}/all?type=8", token) { json, done, total ->
                        PlexParse.musicMetadata(json).forEach { artists[it.key] = it }
                        emit(SyncProgress("Artists: ${s.title}", done, total))
                    }
                    pages(base, "/library/sections/${s.key}/all?type=9", token) { json, done, total ->
                        PlexParse.musicMetadata(json).forEach { albums[it.key] = it }
                        emit(SyncProgress("Albums: ${s.title}", done, total))
                    }
                    pages(base, "/library/sections/${s.key}/all?type=10", token) { json, done, total ->
                        items += PlexParse.tracks(json, artists, albums)
                        emit(SyncProgress("Music: ${s.title}", done, total))
                    }
                }
            }

            emit(SyncProgress("Saving", items.size, items.size))
            schedule.refreshChannels(
                eastWest = settings.isOn(Settings.EAST_WEST),
                music = settings.isOn(Settings.MUSIC),
                items = items,
                selectedLibraries = selectedLibraries,
            )
            emit(SyncProgress("Updating guide", 0, 0))
            schedule.ensure(System.currentTimeMillis())
            settings.setLastSyncMs(System.currentTimeMillis())
            emit(SyncProgress("Done", items.size, items.size, finished = true))
        }
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
    suspend fun refreshChannels() = mutex.withLock {
        if (settings.serverBase() == null) return@withLock
        schedule.refreshChannels(
            eastWest = settings.isOn(Settings.EAST_WEST),
            music = settings.isOn(Settings.MUSIC),
        )
    }

    private companion object {
        const val PAGE = 200
    }
}
