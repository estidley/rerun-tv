package com.rerun.core.model

import com.rerun.core.episode
import com.rerun.core.movie
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesTest {
    @Test
    fun includeSourcesAreOred() {
        val rules = Rules(genres = setOf("Horror"), networks = setOf("Syfy"))
        assertTrue(rules.matches(movie("a", genres = setOf("Comedy"), network = "Syfy")))
        assertTrue(rules.matches(movie("b", genres = setOf("Horror"), network = "NBC")))
        assertFalse(rules.matches(movie("c", genres = setOf("Comedy"), network = "NBC")))
    }

    @Test
    fun filtersAreAnded() {
        val rules = Rules(genres = setOf("Horror"), decades = setOf(1980))
        assertFalse(rules.matches(movie("a", genres = setOf("Horror"), year = 1994)))
        assertTrue(rules.matches(movie("b", genres = setOf("Horror"), year = 1984)))
    }

    @Test
    fun episodeTitleMatchesShowTitle() {
        val rules = Rules(titles = setOf("Dragon Ball Z"))
        assertTrue(rules.matches(episode("e1", show = "Dragon Ball Z")))
        assertFalse(rules.matches(episode("e2", show = "Sailor Moon")))
    }

    @Test
    fun caseInsensitive() {
        val rules = Rules(genres = setOf(" horror "), ratings = setOf("tv-ma"))
        assertTrue(rules.matches(movie("a", genres = setOf("Horror"), rating = "TV-MA")))
    }

    @Test
    fun networkIdOverridesRawNetwork() {
        val rules = Rules(networks = setOf("syfy"))
        val item = movie("a", network = "Sci Fi Channel")
        assertFalse(rules.matches(item))
        assertTrue(rules.matches(item, networkId = "syfy"))
    }

    @Test
    fun noIncludeSourcesMatchesEverything() {
        assertTrue(Rules().matches(movie("a")))
        assertFalse(Rules(kinds = setOf(MediaKind.EPISODE)).matches(movie("a")))
    }

    @Test
    fun overnightBlockActiveAfterMidnight() {
        val zone = ZoneId.of("America/Chicago")
        val block = TimeBlock(setOf(DayOfWeek.FRIDAY), LocalTime.of(21, 0), LocalTime.of(3, 0), Rules())
        // 2026-10-23 is a Friday
        assertTrue(block.activeAt(ZonedDateTime.of(2026, 10, 23, 22, 0, 0, 0, zone)))
        assertTrue(block.activeAt(ZonedDateTime.of(2026, 10, 24, 1, 30, 0, 0, zone)))
        assertFalse(block.activeAt(ZonedDateTime.of(2026, 10, 24, 21, 30, 0, 0, zone)))
        assertFalse(block.activeAt(ZonedDateTime.of(2026, 10, 23, 20, 59, 0, 0, zone)))
    }

    @Test
    fun sameDayBlock() {
        val zone = ZoneId.of("America/Chicago")
        val block = TimeBlock(setOf(DayOfWeek.MONDAY), LocalTime.of(16, 0), LocalTime.of(19, 0), Rules())
        assertTrue(block.activeAt(ZonedDateTime.of(2026, 10, 26, 16, 0, 0, 0, zone)))
        assertFalse(block.activeAt(ZonedDateTime.of(2026, 10, 26, 19, 0, 0, 0, zone)))
    }
}
