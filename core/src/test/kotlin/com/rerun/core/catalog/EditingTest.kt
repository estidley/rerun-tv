package com.rerun.core.catalog

import com.rerun.core.episode
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.Rules
import com.rerun.core.movie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditingTest {
    @Test
    fun facetsCollectDistinctSortedValues() {
        val lib = listOf(
            movie("a", genres = setOf("Horror", "Thriller"), year = 1984, rating = "R", labels = setOf("Halloween")),
            movie("b", genres = setOf("Comedy"), year = 1999, rating = "PG"),
            episode("e1", "Zeta Show", genres = setOf("Animation"), network = "Disney+", rating = "TV-G", year = 2021),
            episode("e2", "Alpha Show", network = "Cartoon Network"),
        )
        val f = Facets.from(lib)
        assertEquals(listOf("Animation", "Comedy", "Horror", "Thriller"), f.genres)
        assertEquals(listOf("Alpha Show", "Zeta Show"), f.shows)
        assertEquals(listOf(1980, 1990, 2020), f.decades)
        assertEquals(listOf("Halloween"), f.labels)
        assertTrue("PG" in f.ratings && "TV-G" in f.ratings)
        assertTrue("disney" in f.networks && "cn" in f.networks)
    }

    @Test
    fun draftRejectsDuplicateNumberAndBlankName() {
        val others = listOf(ChannelDef("horror", 301, "Horror", ChannelKind.THEME, Rules()))
        val dup = ChannelDef("custom:x", 301, "Fright", ChannelKind.CUSTOM, Rules(genres = setOf("Horror")))
        assertTrue(validateDraft(dup, others).any { "301" in it })
        val blank = dup.copy(number = 401, name = " ")
        assertTrue(validateDraft(blank, others).any { "name" in it.lowercase() })
        assertEquals(emptyList(), validateDraft(dup.copy(number = 401), others))
        assertTrue(validateDraft(dup.copy(number = 401, minimumContentHours = -1), others).isNotEmpty())
        assertTrue(validateDraft(dup.copy(number = 401, minimumContentHours = 169), others).isNotEmpty())
        assertEquals(emptyList(), validateDraft(dup.copy(number = 401, minimumContentHours = 0), others))
    }

    @Test
    fun editingExistingChannelDoesNotClashWithItself() {
        val existing = ChannelDef("horror", 301, "Horror", ChannelKind.THEME, Rules())
        assertEquals(emptyList(), validateDraft(existing.copy(name = "Scary"), listOf(existing)))
    }

    @Test
    fun numberMustBeInChannelRange() {
        val d = ChannelDef("custom:x", 0, "X", ChannelKind.CUSTOM, Rules())
        assertTrue(validateDraft(d, emptyList()).isNotEmpty())
        assertEquals(emptyList(), validateDraft(d.copy(number = 1400), emptyList()))
        assertTrue(validateDraft(d.copy(number = 10_000), emptyList()).isNotEmpty())
        assertTrue(validateDraft(d.copy(number = Lineup.RESTART_NUMBER), emptyList()).isNotEmpty())
    }
}
