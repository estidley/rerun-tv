package com.rerun.core.schedule

import com.rerun.core.HOUR
import com.rerun.core.MIN
import com.rerun.core.catalog.Catalog
import com.rerun.core.episode
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.PlayOrder
import com.rerun.core.model.Rules
import com.rerun.core.movie
import com.rerun.core.track
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SharedPilotSchedulerTest {
    private val def = Catalog.builtInChannels().first { it.id == "disney" }
    private val recipe = SharedScheduleCode(371, 0)
    private val window = 3 * HOUR

    @Test fun codeGoldenVectorsAndAliases() {
        val vectors = listOf(
            SharedScheduleCode(0, 0) to "040000000000000000000W8J",
            SharedScheduleCode(-1, 20000) to "07ZZZZZZZZZZZZR00172041S",
            SharedScheduleCode(Long.MIN_VALUE, -100000000) to "060000000000007T18FG06EC",
            SharedScheduleCode(Long.MAX_VALUE, 100000000) to "05ZZZZZZZZZZZZR5YQGG1PQ0",
        )
        for ((value, text) in vectors) {
            assertEquals(text, value.encode())
            assertEquals(value, SharedScheduleCode.decode(text))
            val aliases = text.lowercase().replace('0', 'o').replace('1', 'l')
                .chunked(4).joinToString("-")
            assertEquals(value, SharedScheduleCode.decode(" $aliases "))
        }
        assertEquals(-1, SharedScheduleCode.generate(-1).anchorDay)
        assertEquals(1, SharedScheduleCode.generate(86_400_000).anchorDay)
    }

    @Test fun codeRejectsTyposTruncationInvalidSymbolsAndBounds() {
        val code = recipe.encode()
        for (i in code.indices) {
            val changed = code.replaceRange(i, i + 1, if (code[i] == '0') "2" else "0")
            assertFailsWith<IllegalArgumentException> { SharedScheduleCode.decode(changed) }
        }
        for (bad in listOf("", code.dropLast(1), code + "0", "U" + code.drop(1), " ".repeat(129))) {
            assertFailsWith<IllegalArgumentException> { SharedScheduleCode.decode(bad) }
        }
        assertFailsWith<IllegalArgumentException> { SharedScheduleCode(0, 100000001) }
        assertFailsWith<IllegalArgumentException> { SharedScheduleCode.generate(Long.MAX_VALUE) }
        val future = ByteBuffer.allocate(13).put(2).putLong(0).putInt(0).array()
        val bytes = future + pilotHash("code-v1", future).copyOf(2)
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        val binary = bytes.joinToString("") { (it.toInt() and 255).toString(2).padStart(8, '0') }
        val futureCode = binary.chunked(5).map { alphabet[it.toInt(2)] }.joinToString("")
        val error = assertFailsWith<IllegalArgumentException> { SharedScheduleCode.decode(futureCode) }
        assertTrue(error.message.orEmpty().contains("version"))
    }

    @Test fun capabilityGateAllowsOnlyUneditedPilotProgramming() {
        assertTrue(SharedPilotScheduler.participates(def))
        assertTrue(SharedPilotScheduler.participates(def.copy(name = "Mine", number = 199)))
        for (changed in listOf(
            def.copy(id = "custom:disney"), def.copy(number = 200), def.copy(kind = ChannelKind.MUSIC),
            def.copy(rules = Rules()), def.copy(order = PlayOrder.CHRONOLOGICAL),
            def.copy(movieEveryMs = 1), def.copy(kidsSafe = !def.kidsSafe),
            def.copy(minimumContentHours = 2),
        )) assertFalse(SharedPilotScheduler.participates(changed))
        assertFalse(SharedPilotScheduler.participates(Catalog.builtInChannels().first { it.id == "cn" }))
        assertTrue(SharedPilotScheduler.participates(Catalog.builtInChannels().first { it.id == "horror" }.copy(number = 150)))
        for (ordered in Catalog.builtInChannels().filter { it.order != PlayOrder.SHUFFLE }) {
            assertFalse(SharedPilotScheduler.participates(ordered.copy(number = 150)))
        }
        assertFailsWith<IllegalArgumentException> {
            SharedPilotScheduler.generate(def.copy(order = PlayOrder.IN_ORDER), emptyList(), recipe, 0, window)
        }
    }

    @Test fun boundsAndEmptyPoolAreExplicit() {
        val pool = listOf(movie("a"))
        assertEquals(SharedPilotScheduler.generate(def, pool, recipe, 0, window),
            SharedPilotScheduler.generate(def, pool, recipe, -1, window))
        assertEquals(emptyList(), SharedPilotScheduler.generate(def, pool, recipe, -window, 0))
        assertEquals(emptyList(), SharedPilotScheduler.generate(def, pool, recipe, 0, 0))
        assertEquals(emptyList(), SharedPilotScheduler.generate(def, emptyList(), recipe, 0, window))
        assertFailsWith<IllegalArgumentException> { SharedPilotScheduler.generate(def, pool, recipe, 1, 0) }
        assertFailsWith<IllegalArgumentException> { SharedPilotScheduler.generate(def, pool, recipe, 0, 367 * 24 * HOUR) }
        assertFailsWith<IllegalArgumentException> { SharedPilotScheduler.generate(def, pool, recipe, Long.MIN_VALUE, 0) }
        val oldest = SharedScheduleCode(0, -100000000)
        val oldestTime = oldest.anchorDay * 24 * HOUR
        assertEquals(oldestTime,
            SharedPilotScheduler.generate(def, pool, oldest, oldestTime, oldestTime + window).first().startMs)
        val later = recipe.copy(anchorDay = 1)
        val day = 24 * HOUR
        val complete = SharedPilotScheduler.generate(def, pool, later, day - window, day + 2 * window)
        val stitched = (SharedPilotScheduler.generate(def, pool, later, day - window, day + MIN) +
            SharedPilotScheduler.generate(def, pool, later, day + MIN, day + 2 * window)).distinct()
        assertEquals(complete, stitched)
        assertEquals(day, complete.first().startMs)
    }

    @Test fun movieCutoffPreservesSourceAndTruncatesOnlyScheduledEnd() {
        val movie = movie("a", durationMs = window + 119_999)
        assertEquals(listOf(ScheduleEntry(def.id, 0, window, EntryKind.PROGRAM, "a")),
            SharedPilotScheduler.generate(def, listOf(movie), recipe, 0, window))
        assertEquals(window + 119_999, movie.durationMs)
        assertEquals(emptyList(), SharedPilotScheduler.generate(def,
            listOf(movie.copy(durationMs = window + 120_000)), recipe, 0, window))
        val short = SharedPilotScheduler.generate(def, listOf(movie.copy(durationMs = MIN)), recipe, 0, window)
        assertEquals(listOf(EntryKind.PROGRAM, EntryKind.FILLER), short.map { it.kind })
        assertEquals(window, short.last().endMs)
    }

    @Test fun episodeOrderPaddingNoSkippingAndContinuationMatchSequentialReference() {
        val durations = listOf(61, 119, 31, 151, 15, 44, 60, 16, 179)
        val episodes = durations.mapIndexed { i, duration ->
            episode("e$i", "one", season = i / 3, episode = i % 3, durationMs = duration * MIN)
        }
        val pool = episodes.reversed() + episode("too-long", "one", durationMs = window + 1) +
            episode("too-short", "one", durationMs = MIN - 1)
        val actual = SharedPilotScheduler.generate(def, pool, recipe, 0, 300 * window)
        val expected = ArrayList<ScheduleEntry>()
        var index = 0
        repeat(300) { w ->
            var cursor = w * window
            val end = cursor + window
            while (cursor + episodes[index].durationMs <= end) {
                val item = episodes[index]
                expected += ScheduleEntry(def.id, cursor, cursor + item.durationMs, EntryKind.PROGRAM, item.key)
                val padded = ((item.durationMs + 15 * MIN - 1) / (15 * MIN)) * 15 * MIN
                if (padded > item.durationMs) expected +=
                    ScheduleEntry(def.id, cursor + item.durationMs, cursor + padded, EntryKind.FILLER, null)
                cursor += padded
                index = (index + 1) % episodes.size
            }
            if (cursor < end) expected += ScheduleEntry(def.id, cursor, end, EntryKind.FILLER, null)
        }
        assertEquals(expected, actual)
        // A pool can wrap several complete cycles inside one window.
        val tiny = SharedPilotScheduler.generate(def, listOf(episode("tiny", "tiny", durationMs = MIN)), recipe, 0, window)
        assertEquals(12, tiny.count { it.kind == EntryKind.PROGRAM })
        assertEquals(window, tiny.last().endMs)
    }

    @Test fun equalExposureAndNoRepeatedDeckBoundary() {
        for (size in listOf(1, 2, 3, 7)) {
            val movies = (0 until size).map { movie("m$it", durationMs = window) }
            val shows = (0 until size).map { episode("e$it", "s$it", durationMs = window) }
            for (pool in listOf(movies, shows)) {
                val keys = SharedPilotScheduler.generate(def, pool, recipe, 0, 40L * size * window).map { it.itemKey }
                for (round in keys.chunked(size)) assertEquals(size, round.toSet().size)
                if (size > 1) assertTrue(keys.zipWithNext().all { (a, b) -> a != b })
            }
        }
    }

    @Test fun mixedWindowsAlternateAndOnlyTvWindowsAdvanceEpisodes() {
        val episodes = (0..20).map { episode("e$it", "s", episode = it, durationMs = 100 * MIN) }
        val pool = episodes + movie("m", durationMs = window)
        val entries = SharedPilotScheduler.generate(def, pool, recipe, 0, 20 * window)
        val programs = entries.filter { it.kind == EntryKind.PROGRAM }
        assertEquals(20, programs.size)
        assertTrue(programs.zipWithNext().all { (a, b) -> (a.itemKey == "m") != (b.itemKey == "m") })
        assertEquals((0..9).map { "e$it" }, programs.filter { it.itemKey != "m" }.map { it.itemKey })
        val manyShows = (0..4).flatMap { show ->
            (0..6).map { episode("s${show}e$it", "s$show", episode = it, durationMs = 100 * MIN) }
        } + movie("m", durationMs = window)
        val manyPrograms = SharedPilotScheduler.generate(def, manyShows, recipe, 0, 150 * window)
            .filter { it.kind == EntryKind.PROGRAM && it.itemKey != "m" }
        for (show in 0..4) {
            val keys = manyPrograms.mapNotNull { it.itemKey }.filter { it.startsWith("s${show}e") }
            assertEquals((0 until 15).map { "s${show}e${it % 7}" }, keys)
        }
    }

    @Test fun permutationSubrangesAndSeamsAreIndependent() {
        val pool = (0..10).map { movie("m$it") } +
            (0..30).map { episode("e$it", "s${it % 5}", episode = it) }
        val full = SharedPilotScheduler.generate(def, pool, recipe, 0, 40 * window)
        assertEquals(full, SharedPilotScheduler.generate(def, pool.reversed(), recipe, 0, 40 * window))
        for ((from, to) in listOf(17 * MIN to 7 * window + 1, window to 9 * window, 19 * window + MIN to 31 * window)) {
            assertEquals(full.filter { it.startMs < to && it.endMs > from },
                SharedPilotScheduler.generate(def, pool, recipe, from, to))
        }
        val seam = 11 * window + 7 * MIN
        val stitched = (SharedPilotScheduler.generate(def, pool, recipe, 0, seam) +
            SharedPilotScheduler.generate(def, pool, recipe, seam, 40 * window)).distinct()
        assertEquals(full, stitched)
        assertNotEquals(full, SharedPilotScheduler.generate(def, pool, recipe.copy(seed = 372), 0, 40 * window))
        val shifted = SharedPilotScheduler.generate(def, pool, recipe.copy(anchorDay = 1), 24 * HOUR, 40 * window)
        assertNotEquals(full.filter { it.startMs >= 24 * HOUR }, shifted)
    }

    @Test fun fingerprintExcludesPresentationButIncludesSourceAndMemberships() {
        val pool = listOf(episode("e", "s"), movie("m"))
        val hash = SharedPilotScheduler.fingerprint(def, pool, recipe)
        assertEquals(64, hash.length)
        assertEquals(hash, SharedPilotScheduler.fingerprint(def.copy(name = "Renamed"), pool.reversed(), recipe))
        assertEquals(hash, SharedPilotScheduler.fingerprint(def,
            pool.map { it.copy(title = "Other", showTitle = "Other", thumb = "Other", partKey = "Other") } +
                track("ignored"), recipe))
        for (changed in listOf(pool.map { it.copy(durationMs = it.durationMs + 1) },
            pool.map { it.copy(collections = setOf("new")) }, pool.dropLast(1))) {
            assertNotEquals(hash, SharedPilotScheduler.fingerprint(def, changed, recipe))
        }
        assertNotEquals(hash, SharedPilotScheduler.fingerprint(def.copy(number = 198), pool, recipe))
        assertNotEquals(hash, SharedPilotScheduler.fingerprint(def, pool, recipe.copy(seed = 372)))
        assertNotEquals(hash, SharedPilotScheduler.fingerprint(def, pool, recipe.copy(anchorDay = 1)))
        assertNotEquals(hash, SharedPilotScheduler.fingerprint(def,
            pool + movie("over-cutoff", durationMs = 4 * HOUR), recipe))
    }

    @Test fun duplicateKeysDoNotDependOnInputOrder() {
        val item = movie("m")
        assertEquals(SharedPilotScheduler.generate(def, listOf(item), recipe, 0, window),
            SharedPilotScheduler.generate(def, listOf(item, item.copy(title = "Different")), recipe, 0, window))
        assertFailsWith<IllegalArgumentException> {
            SharedPilotScheduler.generate(def, listOf(item, item.copy(durationMs = 90 * MIN)), recipe, 0, window)
        }
    }

    @Test fun invalidIdentityKeysAreRejectedConsistently() {
        for (key in listOf("", " ", "\n", "bad\u0000key", "\uD800")) {
            for (item in listOf(movie(key), episode("e", "s").copy(showKey = key),
                movie(key, durationMs = 4 * HOUR))) {
                assertFailsWith<IllegalArgumentException> {
                    SharedPilotScheduler.generate(def, listOf(item), recipe, 0, window)
                }
                assertFailsWith<IllegalArgumentException> {
                    SharedPilotScheduler.fingerprint(def, listOf(item), recipe)
                }
            }
        }
        assertTrue(SharedPilotScheduler.generate(def, listOf(episode("e", "s").copy(showKey = null)),
            recipe, 0, window).isNotEmpty())
    }

    @Test fun portableMixedScheduleGoldenVector() {
        val pool = listOf(100, 120, 181, 70, 179).mapIndexed { i, duration ->
            movie("m$i", durationMs = duration * MIN)
        } + (0..2).flatMap { show ->
            listOf(61, 119, 31, 151).mapIndexed { ep, duration ->
                episode("s${show}e$ep", "s$show", episode = ep, durationMs = duration * MIN)
            }
        }
        val entries = SharedPilotScheduler.generate(def, pool, recipe, 0, 24 * window)
        // Independently calculated with Python struct, hashlib and a sequential episode cursor.
        val canonical = entries.joinToString("\n") {
            "${it.startMs}|${it.endMs}|${it.kind.name}|${it.itemKey.orEmpty()}"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(64, entries.size)
        assertEquals("6804cd9f1af299a55432eebb468ef97f335291a0e690dfca580891688c5a028f", digest)
        assertEquals("s1e0,m1,s0e0,m3,s2e0,m4,s1e1,s1e2,m2,s0e1,s0e2,m0,s2e1,s2e2,m1," +
            "s1e3,m3,s0e3,m4,s2e3,m2,s1e0,m0,s0e0,m1,s2e0,m3",
            entries.filter { it.kind == EntryKind.PROGRAM }.joinToString(",") { it.itemKey.orEmpty() })
    }

    @Test fun fiftyThousandItemsAcrossOneHundredChannelConfigurationsAtOldAnchors() {
        val fixtureStart = System.nanoTime()
        val pool = (0 until 45_000).map {
            episode("e$it", "s${it % 100}", episode = it, durationMs = (16 + it % 89) * MIN)
        } + (0 until 5_000).map { movie("m$it", durationMs = (70 + it % 70) * MIN) }
        val reversed = pool.reversed()
        val supported = Catalog.builtInChannels().filter { SharedPilotScheduler.participates(it) }
        // Synthetic configured variants, not a claim that the stock catalog has 100 distinct IDs.
        val channels = (0 until 100).map { supported[it % supported.size].copy(number = 100 + it) }
        assertEquals(50_000, pool.size)
        assertEquals(100, channels.size)
        println("Shared pilot local JVM fixture construction: ${(System.nanoTime() - fixtureStart) / 1_000_000.0} ms")
        val baselineWork = HashMap<Int, List<Long>>()
        for (ageYears in listOf(2L, 5L, 10L)) {
            val anchorDay = -ageYears * 365
            val oldRecipe = recipe.copy(anchorDay = anchorDay)
            val before = System.nanoTime()
            var firstIndexMs = 0.0
            for ((i, channel) in channels.withIndex()) {
                val coldStart = System.nanoTime()
                // Every call builds a fresh content index over all 50,000 routed candidates.
                val stats = SharedPilotScheduler.WorkStats()
                val output = SharedPilotScheduler.generateMeasured(channel, pool, oldRecipe, 0, 24 * HOUR, stats)
                assertEquals(50_000, stats.indexedItems)
                assertEquals(45_000, stats.episodeTransitionStates)
                assertTrue(stats.episodeTransitionSteps <= 12 * 45_000)
                assertTrue(stats.episodeGraphStates <= 45_000)
                assertEquals(8, stats.visitedWindows)
                assertEquals(4, stats.episodeStateLookups)
                val work = listOf(stats.indexedItems, stats.episodeTransitionStates,
                    stats.episodeTransitionSteps, stats.episodeGraphStates,
                    stats.visitedWindows, stats.episodeStateLookups)
                if (ageYears == 2L) baselineWork[i] = work else assertEquals(baselineWork.getValue(i), work)
                if (i == 0) {
                    firstIndexMs = stats.indexNanos / 1_000_000.0
                    val generationMs = (System.nanoTime() - coldStart) / 1_000_000.0
                    val fingerprintStart = System.nanoTime()
                    val fingerprint = SharedPilotScheduler.fingerprint(channel, pool, oldRecipe)
                    val firstFingerprintMs = (System.nanoTime() - fingerprintStart) / 1_000_000.0
                    assertEquals(64, fingerprint.length)
                    println("Anchor age $ageYears: first fresh-index generation $generationMs ms; " +
                        "index $firstIndexMs ms; fingerprint $firstFingerprintMs ms; " +
                        "visited windows ${stats.visitedWindows}, state lookups ${stats.episodeStateLookups}, " +
                        "transition states ${stats.episodeTransitionStates}, steps ${stats.episodeTransitionSteps}, " +
                        "graph states ${stats.episodeGraphStates}")
                }
                assertTrue(output.isNotEmpty())
                assertEquals(0, output.first().startMs)
                assertEquals(24 * HOUR, output.last().endMs)
                val from = 41 * MIN
                val to = 19 * HOUR
                assertEquals(output.filter { it.startMs < to && it.endMs > from },
                    SharedPilotScheduler.generate(channel, reversed, oldRecipe, from, to))
            }
            val elapsedMs = (System.nanoTime() - before) / 1_000_000.0
            println("Shared pilot local JVM: anchor age $ageYears years, 50000 items per call, " +
                "100 synthetic channel configurations, 24h horizon plus reversed-input subrange replay: " +
                "$elapsedMs ms total; first fresh index $firstIndexMs ms")
        }
    }
}
