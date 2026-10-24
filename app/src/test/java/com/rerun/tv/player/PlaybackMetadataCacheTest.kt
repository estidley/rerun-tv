package com.rerun.tv.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class PlaybackMetadataCacheTest {
    private var now = 0L
    private var requests = 0
    private val cache = PlaybackMetadataCache { now }
    private val json = """{"MediaContainer":{"Metadata":[]}}"""

    private suspend fun get(key: String = "1", base: String = "server", token: String = "placeholder", revision: Long = 0) =
        cache.get(base, token, revision, key) { requests++; json }

    @Test
    fun retunesReuseParsedMetadataUntilExpiry() = runBlocking {
        val first = get()
        now = 59_999
        assertSame(first, get())
        assertEquals(1, requests)
        now = 60_000
        assertNotSame(first, get())
        assertEquals(2, requests)
    }

    @Test
    fun serverCredentialsAndLibraryChangesInvalidateAllEntries() = runBlocking {
        get()
        get(base = "other")
        get(base = "other", token = "different-placeholder")
        get(base = "other", token = "different-placeholder", revision = 1)
        assertEquals(4, requests)
        cache.clear()
        get(base = "other", token = "different-placeholder", revision = 1)
        assertEquals(5, requests)
    }

    @Test
    fun leastRecentlyUsedEntriesAreEvictedAtSixteenItems() = runBlocking {
        repeat(16) { get("$it") }
        get("0")
        get("16")
        get("0")
        assertEquals(17, requests)
        get("1")
        assertEquals(18, requests)
    }

    @Test
    fun failedLoadsAreNotCached() = runBlocking {
        try {
            cache.get("server", "placeholder", 0, "1") { throw IllegalStateException("offline") }
            fail("Load errors must propagate")
        } catch (e: IllegalStateException) {
            assertEquals("offline", e.message)
        }
        get()
        assertEquals(1, requests)
    }

    @Test
    fun clearAndCancellationPreventInflightLoadsFromRepopulatingCache() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val pending = async {
            cache.get("server", "placeholder", 0, "1") { finish.await(); json }
        }
        yield()
        cache.clear()
        finish.complete(Unit)
        pending.await()
        get()
        assertEquals(1, requests)

        cache.clear()
        val cancelled = async {
            cache.get("server", "placeholder", 0, "1") { CompletableDeferred<Unit>().await(); json }
        }
        yield()
        cancelled.cancelAndJoin()
        get()
        assertEquals(2, requests)
    }
}
