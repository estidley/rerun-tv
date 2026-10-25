package com.rerun.tv.player

import android.os.SystemClock
import com.rerun.core.plex.PlexParse
import com.rerun.core.plex.SubtitleStream
import com.rerun.core.plex.VideoDetails
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class PlaybackMetadata(val video: VideoDetails, val subtitles: List<SubtitleStream>)

/** Owned by the tuner's main-thread scope. Never persists metadata or credentials to disk. */
internal class PlaybackMetadataCache(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private class Server(val base: String, val token: String, val revision: Long)
    private data class Cached(val value: PlaybackMetadata, val loadedAt: Long)
    private val entries = LinkedHashMap<String, Cached>(16, 0.75f, true)
    private var server: Server? = null
    private var generation = 0L

    suspend fun get(base: String, token: String, revision: Long, key: String, load: suspend () -> String): PlaybackMetadata {
        val current = server
        if (current == null || current.base != base || current.token != token || current.revision != revision) {
            clear()
            server = Server(base, token, revision)
        }
        entries[key]?.let {
            if (clock() - it.loadedAt in 0 until TTL_MS) return it.value
            entries.remove(key)
        }
        val started = generation
        val json = load()
        currentCoroutineContext().ensureActive()
        val value = PlaybackMetadata(PlexParse.videoDetails(json), PlexParse.subtitles(json))
        if (generation == started) {
            entries[key] = Cached(value, clock())
            if (entries.size > CAPACITY) entries.remove(entries.keys.first())
        }
        return value
    }

    fun clear() {
        entries.clear()
        server = null
        generation++
    }

    private companion object {
        const val CAPACITY = 16
        const val TTL_MS = 60_000L
    }
}
