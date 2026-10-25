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
import kotlin.test.assertTrue

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
        // Baseline includes the five comic channels and the serialized franchise-rule field.
        measure("lineup-2500", "f8e12e71b49879d7e571d0f8730431e00685c919a02dbd9ded137a48268f8663") {
            val plan = Lineup.plan(library, emptyList(), eastWest = true, music = false)
            // Keep the original workload fingerprint for all pre-existing channels.
            plan.copy(channels = plan.channels.filterNot { it.def.id.startsWith("decade:") })
        }
    }

    @Test
    fun largeDecadeLineupUsesOneAdditionalLibraryPass() {
        val items = (0 until 20_000).map {
            episode("e$it", "Show ${it % 100}", episode = it, year = 1900 + it % 200)
        } + (0 until 20_000).map { track("t$it").copy(year = 1900 + it % 200) }
        var reads = 0
        val counted = object : AbstractList<MediaItem>() {
            override val size: Int get() = items.size
            override fun get(index: Int): MediaItem {
                reads++
                return items[index]
            }
        }
        var plan = Lineup.plan(counted, emptyList(), eastWest = false, music = true)
        val withDecades = reads
        reads = 0
        val unknownYears = counted.map { it.copy(year = null) }
        reads = 0
        val unknownCounted = object : AbstractList<MediaItem>() {
            override val size: Int get() = unknownYears.size
            override fun get(index: Int): MediaItem {
                reads++
                return unknownYears[index]
            }
        }
        Lineup.plan(unknownCounted, emptyList(), eastWest = false, music = true)
        assertEquals(reads, withDecades, "Qualifying decades must not add per-channel library scans")
        assertEquals(40, plan.channels.count { it.def.id.startsWith("decade:") })
        reads = 0
        val renamed = plan.channels.filter { it.def.id.startsWith("decade:") }.map {
            com.rerun.core.catalog.StoredChannel(it.def.copy(name = "My ${it.def.name}"), true)
        }
        Lineup.plan(counted, renamed, eastWest = false, music = true)
        assertEquals(withDecades, reads, "Renames must reuse decade totals rather than scan each pool")
        val expected = fingerprint(plan)
        val elapsed = measureNanoTime {
            plan = Lineup.plan(items, emptyList(), eastWest = false, music = true)
        }
        assertEquals(expected, fingerprint(plan))
        assertTrue(plan.channels.map { it.def.number }.distinct().size == plan.channels.size)
        println("PERF decade-lineup-40000 elapsedMs=${elapsed / 1_000_000.0} fingerprint=$expected")
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
