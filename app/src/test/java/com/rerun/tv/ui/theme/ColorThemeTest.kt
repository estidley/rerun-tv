package com.rerun.tv.ui.theme

import org.junit.Assert.*
import org.junit.Test

class ColorThemeTest {
    @Test
    fun missingOrUnknownPreferenceUsesDefault() {
        assertEquals(ColorTheme.STANDARD_BLUE, ColorTheme.from(null))
        assertEquals(ColorTheme.STANDARD_BLUE, ColorTheme.from("unknown"))
        for (theme in ColorTheme.entries) assertEquals(theme, ColorTheme.from(theme.name))
        assertEquals("STANDARD_BLUE", ColorTheme.STANDARD_BLUE.name)
        assertEquals("SEVENTIES", ColorTheme.SEVENTIES.name)
        assertEquals("FIFTIES", ColorTheme.FIFTIES.name)
    }

    @Test
    fun displayNamesAndDetailsIdentifyTheDecadeReferences() {
        assertEquals(listOf("Default", "Partridge", "Lucy"), ColorTheme.entries.map { it.label })
        assertEquals(
            listOf("Classic cable blue", "1970s · harvest gold & avocado", "1950s · cherry & mint"),
            ColorTheme.entries.map { it.detail },
        )
    }

    @Test
    fun everyThemeHasAlternatingRowsAndDistinctSelection() {
        for (theme in ColorTheme.entries) {
            val palette = theme.palette
            assertNotEquals(palette.background, palette.alternate)
            assertNotEquals(palette.accent, palette.background)
            assertNotEquals(palette.accent, palette.alternate)
            assertNotEquals(palette.text, palette.background)
        }
        assertEquals(3, ColorTheme.entries.map { it.palette.background }.distinct().size)
    }
}
