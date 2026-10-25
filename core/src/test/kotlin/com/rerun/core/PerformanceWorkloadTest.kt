package com.rerun.core

import com.rerun.core.catalog.Lineup
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.model.Rules
import com.rerun.core.schedule.ChannelState
import com.rerun.core.schedule.Scheduler
import java.security.MessageDigest
import java.time.ZoneId
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fixed synthetic libraries exercise large-library paths without a Plex server or timing thresholds. */
class PerformanceWorkloadTest {
    private val scheduler = Scheduler(ZoneId.of("UTC"))
    private val now = 1_800_000_000_000L

    @Test
    fun largeMusicSchedule() {
        val tracks = (0 until 5_000).map { track("t$it", album = "Album ${it % 250}", index = it) }
        val def = ChannelDef("music:jazz", 2000, "Jazz", ChannelKind.MUSIC, Rules(kinds = setOf(MediaKind.TRACK)))
        measure("music-5000", "b6afe1417a5ecc684b58b55fc2b9df224b6fc17fc54cfb170a762afeabb30a5f") {
            scheduler.extend(def, tracks, ChannelState(seed = 42), null, now)
        }
    }

    @Test
    fun largeMixedSchedule() {
        val library = (0 until 5_000).map { episode("e$it", "Show ${it % 250}", episode = it) } +
            (0 until 500).map { movie("m$it") }
        val def = ChannelDef("mixed", 400, "Mixed", ChannelKind.CUSTOM, Rules())
        measure("mixed-5500", "4f139395b86907fc62d5836df114207b5c46b9bc1a22bf60a5d22a478974677f") {
            scheduler.extend(def, library, ChannelState(seed = 42), null, now)
        }
    }

    @Test
    fun largeLineup() {
        val library: List<MediaItem> = (0 until 2_000).map {
            episode("e$it", "Show ${it % 100}", episode = it, genres = setOf("Comedy", "Drama"), network = "Netflix")
        } + (0 until 500).map { movie("m$it", genres = setOf("Action", "Drama"), studio = "Walt Disney Pictures") }
        measure("lineup-2500", "6c5244768dcce0e76ff7f4eca43999ca01e5dcb31e9b496efbcf5ef0e683d093") {
            Lineup.plan(library, emptyList(), eastWest = true, music = false)
        }
    }

    private fun measure(name: String, expected: String, operation: () -> Any) {
        assertEquals(expected, fingerprint(operation()), "Must preserve the unoptimized workload's output")
        repeat(3) { assertEquals(expected, fingerprint(operation())) }
        val times = (0 until 7).map {
            lateinit var result: Any
            val elapsed = measureNanoTime { result = operation() }
            assertEquals(expected, fingerprint(result))
            elapsed
        }.sorted()
        println("PERF $name medianMs=${times[times.size / 2] / 1_000_000.0} fingerprint=$expected")
    }

    private fun fingerprint(value: Any): String =
        MessageDigest.getInstance("SHA-256").digest(value.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
}
