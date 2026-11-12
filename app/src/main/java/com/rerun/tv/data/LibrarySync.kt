package com.rerun.tv.data

import android.os.SystemClock
import android.util.Log
import com.rerun.core.model.MediaItem
import com.rerun.core.plex.PlexParse
import com.rerun.core.plex.Section
import com.rerun.core.plex.ServerUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

data class SyncProgress(val stage: String, val done: Int, val total: Int, val finished: Boolean = false)

/** Pulls movies, episodes, and tracks from included Plex libraries into Room, then rebuilds the dial. */
class LibrarySync(
    private val api: PlexApi,
    private val settings: Settings,
    private val schedule: ScheduleRepo,
    private val sectionParallelism: Int = 2,
) {
    private val mutex = Mutex()

    init {
        require(sectionParallelism > 0) { "Section parallelism must be positive" }
    }

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

    fun sync(selectedLibraries: Set<String>? = null): Flow<SyncProgress> = channelFlow {
        mutex.withLock {
            val syncStartedAt = SystemClock.elapsedRealtime()
            require(selectedLibraries == null || selectedLibraries.isNotEmpty()) { "Choose at least one library" }
            val base = requireNotNull(settings.serverBase()) { "No Plex server chosen" }
            val token = requireNotNull(settings.serverToken ?: settings.accountToken) { "Not signed in" }

            send(SyncProgress("Reading libraries", 0, 0))
            val selected = selectedLibraries ?: settings.selectedLibraries()
            val sections = libraries().filter { selected == null || it.key in selected }
            require(sections.isNotEmpty()) { "None of your selected libraries are available. Choose libraries in Settings." }
            val items = coroutineScope {
                val semaphore = Semaphore(sectionParallelism)
                val progressMutex = Mutex()
                sections.map { section ->
                    async {
                        semaphore.withPermit {
                            readSection(base, token, section) { progress ->
                                progressMutex.withLock { send(progress) }
                            }
                        }
                    }
                }.awaitAll().flatten()
            }

            send(SyncProgress("Saving", items.size, items.size))
            val saveStartedAt = SystemClock.elapsedRealtime()
            schedule.refreshChannels(
                items = items,
                selectedLibraries = selectedLibraries,
            )
            Log.d("RerunTiming", "sync_save elapsedMs=${SystemClock.elapsedRealtime() - saveStartedAt}")
            send(SyncProgress("Updating guide", 0, 0))
            val guideStartedAt = SystemClock.elapsedRealtime()
            schedule.ensure(System.currentTimeMillis())
            Log.d("RerunTiming", "sync_schedule elapsedMs=${SystemClock.elapsedRealtime() - guideStartedAt}")
            settings.setLastSyncMs(System.currentTimeMillis())
            send(SyncProgress("Done", items.size, items.size, finished = true))
            Log.i("RerunTiming", "sync_complete elapsedMs=${SystemClock.elapsedRealtime() - syncStartedAt} items=${items.size}")
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun readSection(
        base: String,
        token: String,
        section: Section,
        progress: suspend (SyncProgress) -> Unit,
    ): List<MediaItem> {
        val sectionStartedAt = SystemClock.elapsedRealtime()
        val items = ArrayList<MediaItem>()
        when (section.type) {
            "movie" -> pages(base, "/library/sections/${section.key}/all?type=1&includeGuids=1", token) { json, done, total ->
                items += PlexParse.movies(json)
                progress(SyncProgress("Movies: ${section.title}", done, total))
            }
            "show" -> {
                val shows = HashMap<String, com.rerun.core.model.ShowMeta>()
                pages(base, "/library/sections/${section.key}/all?type=2&includeGuids=1", token) { json, done, total ->
                    PlexParse.shows(json).forEach { shows[it.key] = it }
                    progress(SyncProgress("Shows: ${section.title}", done, total))
                }
                pages(base, "/library/sections/${section.key}/all?type=4", token) { json, done, total ->
                    items += PlexParse.episodes(json, shows)
                    progress(SyncProgress("Episodes: ${section.title}", done, total))
                }
            }
            "artist" -> {
                val artists = HashMap<String, com.rerun.core.plex.MusicMetadata>()
                val albums = HashMap<String, com.rerun.core.plex.MusicMetadata>()
                pages(base, "/library/sections/${section.key}/all?type=8", token) { json, done, total ->
                    PlexParse.musicMetadata(json).forEach { artists[it.key] = it }
                    progress(SyncProgress("Artists: ${section.title}", done, total))
                }
                pages(base, "/library/sections/${section.key}/all?type=9", token) { json, done, total ->
                    PlexParse.musicMetadata(json).forEach { albums[it.key] = it }
                    progress(SyncProgress("Albums: ${section.title}", done, total))
                }
                pages(base, "/library/sections/${section.key}/all?type=10", token) { json, done, total ->
                    items += PlexParse.tracks(json, artists, albums)
                    progress(SyncProgress("Music: ${section.title}", done, total))
                }
            }
        }
        Log.d("RerunTiming", "sync_section type=${section.type} elapsedMs=${SystemClock.elapsedRealtime() - sectionStartedAt} items=${items.size}")
        return items
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

    /** Rebuilds the dial from the saved library and lineup settings. */
    suspend fun refreshChannels() = mutex.withLock {
        if (settings.serverBase() == null) return@withLock
        schedule.refreshChannels()
    }

    /**
     * Startup rebuild. Syncs and lineup switches already rebuild the dial, so launches only need it
     * once after an app update (renamed stock channels, new extras). Everything else skips the pass.
     */
    suspend fun refreshChannelsForAppVersion(appVersion: String) {
        if (settings.lineupAppVersion() == appVersion) return
        refreshChannels()
        if (settings.serverBase() != null) settings.setLineupAppVersion(appVersion)
    }

    private companion object {
        const val PAGE = 200
    }
}
