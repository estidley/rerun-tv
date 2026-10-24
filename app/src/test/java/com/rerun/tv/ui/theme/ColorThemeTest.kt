package com.rerun.tv.ui.theme

import org.junit.Assert.*
import org.junit.Test

class ColorThemeTest {
    @Test
    fun missingOrUnknownPreferenceUsesStandardBlue() {
        assertEquals(ColorTheme.STANDARD_BLUE, ColorTheme.from(null))
        assertEquals(ColorTheme.STANDARD_BLUE, ColorTheme.from("unknown"))
        for (theme in ColorTheme.entries) assertEquals(theme, ColorTheme.from(theme.name))
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
